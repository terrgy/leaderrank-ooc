package leaderrank.graph.outofcore.preprocessing;

import leaderrank.graph.outofcore.io.IntReader;
import leaderrank.graph.outofcore.io.IntWriter;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
        try (IntReader reader = IntReader.open(pathOf(binIndex), READ_BUFFER_BYTES)) {
            for (int i = 0; i < packed.length; i++) {
                int destination = reader.next();
                int sourceVertex = reader.next();
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
            distributeWindow(denseEdges, windowStart, Math.min(count, windowStart + maxOpenBins));
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
        try (OpenBins window = openWindow(windowStart, windowEnd);
                IntReader edges = IntReader.open(denseEdges, READ_BUFFER_BYTES)) {
            while (edges.hasNext()) {
                int sourceVertex = edges.next();
                int destination = edges.next();
                int bin = binOf(destination);
                if (bin < windowStart || bin >= windowEnd) {
                    continue;
                }
                IntWriter writer = window.writer(bin - windowStart);
                if (oversized[bin]) {
                    writer.write(sourceVertex);
                } else {
                    writer.write(destination, sourceVertex);
                }
            }
        }
    }

    private OpenBins openWindow(int windowStart, int windowEnd) throws IOException {
        IntWriter[] writers = new IntWriter[windowEnd - windowStart];
        try {
            for (int i = 0; i < writers.length; i++) {
                pathOf(windowStart + i).toFile().deleteOnExit();
                writers[i] = IntWriter.create(pathOf(windowStart + i), WRITE_BUFFER_BYTES);
            }
        } catch (IOException e) {
            closeAll(writers);
            throw e;
        }
        return new OpenBins(writers);
    }

    private static void closeAll(IntWriter[] writers) throws IOException {
        IOException failure = null;
        for (IntWriter writer : writers) {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    failure = e;
                }
            }
        }
        if (failure != null) {
            throw failure;
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

    private record OpenBins(IntWriter[] writers) implements Closeable {

        IntWriter writer(int slot) {
            return writers[slot];
        }

        @Override
        public void close() throws IOException {
            closeAll(writers);
        }
    }
}
