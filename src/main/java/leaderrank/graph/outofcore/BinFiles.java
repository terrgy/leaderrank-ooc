package leaderrank.graph.outofcore;

import leaderrank.graph.edge.EdgeCursor;
import leaderrank.graph.edge.EdgeSource;
import leaderrank.utils.IdMapper;

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
            ByteBuffer buffer = ByteBuffer.allocateDirect(READ_BUFFER_BYTES).order(ByteOrder.LITTLE_ENDIAN);
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

    void distribute(EdgeSource source, IdMapper mapper) throws IOException {
        int count = bins.size();
        FileChannel[] channels = new FileChannel[count];
        ByteBuffer[] buffers = new ByteBuffer[count];
        try {
            for (int i = 0; i < count; i++) {
                pathOf(i).toFile().deleteOnExit();
                channels[i] = FileChannel.open(pathOf(i),
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
                buffers[i] = ByteBuffer.allocate(WRITE_BUFFER_BYTES).order(ByteOrder.LITTLE_ENDIAN);
            }
            try (EdgeCursor cursor = source.open()) {
                while (cursor.next()) {
                    int destination = mapper.denseOf(cursor.to());
                    int sourceVertex = mapper.denseOf(cursor.from());
                    int bin = binOf(destination);
                    ByteBuffer buffer = buffers[bin];
                    if (oversized[bin]) {
                        if (buffer.remaining() < Integer.BYTES) {
                            drain(channels[bin], buffer);
                        }
                        buffer.putInt(sourceVertex);
                    } else {
                        if (buffer.remaining() < PAIR_BYTES) {
                            drain(channels[bin], buffer);
                        }
                        buffer.putInt(destination);
                        buffer.putInt(sourceVertex);
                    }
                }
            }
            for (int i = 0; i < count; i++) {
                drain(channels[i], buffers[i]);
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
