package leaderrank.graph.outofcore.build;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public final class BinFiles implements Closeable {

    private static final int PAIR_BYTES = 2 * Integer.BYTES;
    private static final int WRITE_BUFFER_BYTES = 1 << 16;
    private static final int READ_BUFFER_BYTES = 1 << 16;

    private final Path directory;
    private final List<Bin> bins;
    private final int[] binBegins;
    private final boolean[] oversized;

    private BinFiles(Path directory, List<Bin> bins, int[] binBegins, boolean[] oversized) {
        this.directory = directory;
        this.bins = bins;
        this.binBegins = binBegins;
        this.oversized = oversized;
    }

    static BinFiles create(List<Bin> bins) throws IOException {
        Path directory = Files.createTempDirectory("leaderrank-bins-");
        directory.toFile().deleteOnExit();
        int[] binBegins = new int[bins.size()];
        boolean[] oversized = new boolean[bins.size()];
        for (int i = 0; i < bins.size(); i++) {
            binBegins[i] = bins.get(i).begin();
            oversized[i] = bins.get(i).oversized();
        }
        return new BinFiles(directory, bins, binBegins, oversized);
    }

    public List<Bin> bins() {
        return bins;
    }

    public Path pathOf(int binIndex) {
        return directory.resolve("bin-" + binIndex);
    }

    void deleteBin(int binIndex) throws IOException {
        Files.deleteIfExists(pathOf(binIndex));
    }

    Path directory() {
        return directory;
    }

    public long recordCount(int binIndex) throws IOException {
        int width = oversized[binIndex] ? Integer.BYTES : PAIR_BYTES;
        return Files.size(pathOf(binIndex)) / width;
    }

    public long[] loadPacked(int binIndex) throws IOException {
        long[] packed = new long[(int) recordCount(binIndex)];
        try (FileChannel channel = FileChannel.open(pathOf(binIndex), StandardOpenOption.READ)) {
            ByteBuffer buffer = ByteBuffer.allocate(READ_BUFFER_BYTES).order(ByteOrder.LITTLE_ENDIAN);
            buffer.position(0).limit(0);
            for (int i = 0; i < packed.length; i++) {
                if (buffer.remaining() < PAIR_BYTES) {
                    refill(channel, buffer, PAIR_BYTES);
                }
                int destination = buffer.getInt();
                int sourceVertex = buffer.getInt();
                packed[i] = ((long) destination << 32) | (sourceVertex & 0xFFFFFFFFL);
            }
        }
        return packed;
    }

    int binOf(int destination) {
        int index = Arrays.binarySearch(binBegins, destination);
        return index >= 0 ? index : -index - 2;
    }

    void distribute(Path denseEdges) throws IOException {
        distribute(denseEdges, Long.MAX_VALUE);
    }

    void distribute(Path denseEdges, long availableBytes) throws IOException {
        int count = bins.size();
        int maxOpenBins = maxOpenBins(availableBytes, count);
        for (int windowStart = 0; windowStart < count; windowStart += maxOpenBins) {
            int windowEnd = Math.min(count, windowStart + maxOpenBins);
            distributeWindow(denseEdges, windowStart, windowEnd);
        }
    }

    public static int distributionWaves(long availableBytes, int binCount) {
        if (binCount <= 0) {
            return 0;
        }
        int open = maxOpenBins(availableBytes, binCount);
        return (binCount + open - 1) / open;
    }

    private static int maxOpenBins(long availableBytes, int count) {
        if (availableBytes >= (long) count * WRITE_BUFFER_BYTES) {
            return Math.max(1, count);
        }
        long open = availableBytes / WRITE_BUFFER_BYTES;
        if (open < 1) {
            return 1;
        }
        return (int) Math.min(open, count);
    }

    private void distributeWindow(Path denseEdges, int windowStart, int windowEnd) throws IOException {
        int width = windowEnd - windowStart;
        FileChannel[] channels = new FileChannel[width];
        ByteBuffer[] buffers = new ByteBuffer[width];
        try {
            for (int i = 0; i < width; i++) {
                int bin = windowStart + i;
                pathOf(bin).toFile().deleteOnExit();
                channels[i] = FileChannel.open(pathOf(bin),
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
                buffers[i] = ByteBuffer.allocate(WRITE_BUFFER_BYTES).order(ByteOrder.LITTLE_ENDIAN);
            }
            try (FileChannel in = FileChannel.open(denseEdges, StandardOpenOption.READ)) {
                ByteBuffer read = ByteBuffer.allocate(READ_BUFFER_BYTES).order(ByteOrder.LITTLE_ENDIAN);
                read.position(0).limit(0);
                while (true) {
                    if (read.remaining() < PAIR_BYTES) {
                        refill(in, read, PAIR_BYTES);
                        if (read.remaining() < PAIR_BYTES) {
                            break;
                        }
                    }
                    int sourceVertex = read.getInt();
                    int destination = read.getInt();
                    int bin = binOf(destination);
                    if (bin < windowStart || bin >= windowEnd) {
                        continue;
                    }
                    int slot = bin - windowStart;
                    ByteBuffer buffer = buffers[slot];
                    if (oversized[bin]) {
                        if (buffer.remaining() < Integer.BYTES) {
                            drain(channels[slot], buffer);
                        }
                        buffer.putInt(sourceVertex);
                    } else {
                        if (buffer.remaining() < PAIR_BYTES) {
                            drain(channels[slot], buffer);
                        }
                        buffer.putInt(destination);
                        buffer.putInt(sourceVertex);
                    }
                }
            }
            for (int i = 0; i < width; i++) {
                drain(channels[i], buffers[i]);
                channels[i].force(false);
            }
        } finally {
            for (FileChannel channel : channels) {
                if (channel != null) {
                    channel.close();
                }
            }
        }
    }

    @Override
    public void close() throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> entries = Files.list(directory)) {
            for (Path entry : entries.toList()) {
                Files.deleteIfExists(entry);
            }
        }
        Files.deleteIfExists(directory);
    }

    private static void refill(FileChannel channel, ByteBuffer buffer, int minimumBytes) throws IOException {
        buffer.compact();
        while (buffer.position() < minimumBytes) {
            if (channel.read(buffer) < 0) {
                break;
            }
        }
        buffer.flip();
    }

    private static void drain(FileChannel channel, ByteBuffer buffer) throws IOException {
        buffer.flip();
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
        buffer.clear();
    }
}
