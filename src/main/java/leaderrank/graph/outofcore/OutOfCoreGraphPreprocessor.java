package leaderrank.graph.outofcore;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import leaderrank.utils.IdMapper;
import leaderrank.graph.edge.EdgeCursor;
import leaderrank.graph.edge.EdgeSource;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;

final class OutOfCoreGraphPreprocessor {

    private static final int EDGE_BUFFER_BYTES = 1 << 16;

    static Pass1Result pass1(EdgeSource source) throws IOException {
        IdMapper mapper = new IdMapper();
        IntArrayList inDegrees = new IntArrayList();
        IntArrayList outDegrees = new IntArrayList();
        long edgeCount = 0;

        try (EdgeCursor cursor = source.open()) {
            while (cursor.next()) {
                int denseFrom = mapper.mapOrAssign(cursor.from());
                int denseTo = mapper.mapOrAssign(cursor.to());
                growTo(inDegrees, outDegrees, mapper.size());
                inDegrees.set(denseTo, inDegrees.getInt(denseTo) + 1);
                outDegrees.set(denseFrom, outDegrees.getInt(denseFrom) + 1);
                edgeCount++;
            }
        }

        return new Pass1Result(mapper, prefixSums(inDegrees), outDegrees.toIntArray(), edgeCount);
    }

    static Pass1Result buildIdMapAndSpill(EdgeSource source, Path denseEdges) throws IOException {
        try (EdgeCursor cursor = source.open()) {
            return buildIdMapAndSpill(cursor, denseEdges);
        }
    }

    static Pass1Result buildIdMapAndSpill(EdgeCursor cursor, Path denseEdges) throws IOException {
        IdMapper mapper = new IdMapper();
        IntArrayList inDegrees = new IntArrayList();
        IntArrayList outDegrees = new IntArrayList();
        long edgeCount = 0;

        try (FileChannel out = FileChannel.open(denseEdges,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer buffer = ByteBuffer.allocate(EDGE_BUFFER_BYTES).order(ByteOrder.LITTLE_ENDIAN);
            while (cursor.next()) {
                int denseFrom = mapper.mapOrAssign(cursor.from());
                int denseTo = mapper.mapOrAssign(cursor.to());
                growTo(inDegrees, outDegrees, mapper.size());
                inDegrees.set(denseTo, inDegrees.getInt(denseTo) + 1);
                outDegrees.set(denseFrom, outDegrees.getInt(denseFrom) + 1);
                edgeCount++;
                if (buffer.remaining() < 2 * Integer.BYTES) {
                    drain(out, buffer);
                }
                buffer.putInt(denseFrom);
                buffer.putInt(denseTo);
            }
            drain(out, buffer);
            out.force(false);
        }

        return new Pass1Result(mapper, prefixSums(inDegrees), outDegrees.toIntArray(), edgeCount);
    }

    private static Pass1Result mapAndSpill(EdgeSource source, Path denseEdges, int parallelism) throws IOException {
        if (parallelism > 1 && source.parallelFile().isPresent()) {
            return mapAndSpillParallel(source.parallelFile().get(), denseEdges, parallelism);
        }
        return buildIdMapAndSpill(source, denseEdges);
    }

    private static Pass1Result mapAndSpillParallel(Path csvFile, Path denseEdges, int parallelism) throws IOException {
        Path workDirectory = Files.createTempDirectory("leaderrank-raw-");
        workDirectory.toFile().deleteOnExit();
        List<Path> chunks = null;
        try {
            chunks = ParallelCsvIngest.parseToRawChunks(csvFile, parallelism, workDirectory);
            try (EdgeCursor cursor = ParallelCsvIngest.rawCursor(chunks)) {
                return buildIdMapAndSpill(cursor, denseEdges);
            }
        } finally {
            if (chunks != null) {
                for (Path chunk : chunks) {
                    Files.deleteIfExists(chunk);
                }
            }
            Files.deleteIfExists(workDirectory);
        }
    }

    static OutOfCorePreprocessingData process(EdgeSource source, Path sourcesPath, long maxEdgesPerBin)
            throws IOException {
        return process(source, sourcesPath, maxEdgesPerBin, defaultParallelism());
    }

    static OutOfCorePreprocessingData process(EdgeSource source, Path sourcesPath, long maxEdgesPerBin,
            int parallelism) throws IOException {
        Path denseEdges = createDenseEdgesFile();
        try {
            Pass1Result pass1 = mapAndSpill(source, denseEdges, parallelism);
            return assemble(denseEdges, pass1, sourcesPath, clampToInt(maxEdgesPerBin), Long.MAX_VALUE);
        } finally {
            Files.deleteIfExists(denseEdges);
        }
    }

    static OutOfCorePreprocessingData process(EdgeSource source, Path sourcesPath, MemoryBudget budget)
            throws IOException {
        return process(source, sourcesPath, budget, defaultParallelism());
    }

    static OutOfCorePreprocessingData process(EdgeSource source, Path sourcesPath, MemoryBudget budget,
            int parallelism) throws IOException {
        Path denseEdges = createDenseEdgesFile();
        try {
            Pass1Result pass1 = mapAndSpill(source, denseEdges, parallelism);
            int vertexCount = pass1.outDegrees().length;
            return assemble(denseEdges, pass1, sourcesPath,
                    budget.maxEdgesPerBin(vertexCount), budget.availableBytes(vertexCount));
        } finally {
            Files.deleteIfExists(denseEdges);
        }
    }

    private static int defaultParallelism() {
        return Runtime.getRuntime().availableProcessors();
    }

    private static Path createDenseEdgesFile() throws IOException {
        Path denseEdges = Files.createTempFile("leaderrank-edges-", ".bin");
        denseEdges.toFile().deleteOnExit();
        return denseEdges;
    }

    private static OutOfCorePreprocessingData assemble(Path denseEdges, Pass1Result pass1, Path sourcesPath,
            int maxEdgesPerBin, long availableBytes) throws IOException {
        List<Bin> bins = BinPlanner.plan(pass1.sourcesPtr(), maxEdgesPerBin);

        try (BinFiles binFiles = BinFiles.create(bins)) {
            binFiles.distribute(denseEdges, availableBytes);
            Files.deleteIfExists(denseEdges);
            writeSortedSources(binFiles, sourcesPath, maxEdgesPerBin, availableBytes);
        }

        return new OutOfCorePreprocessingData(
                pass1.sourcesPtr(),
                pass1.outDegrees(),
                pass1.mapper().originalIds(),
                pass1.edgeCount(),
                sourcesPath
        );
    }

    private static int clampToInt(long value) {
        return (int) Math.max(1, Math.min(value, Integer.MAX_VALUE));
    }

    private static void writeSortedSources(BinFiles binFiles, Path sourcesPath, int chunkRecords, long availableBytes)
            throws IOException {
        List<Bin> bins = binFiles.bins();
        try (FileChannel channel = FileChannel.open(sourcesPath,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer buffer = ByteBuffer.allocate(1 << 20).order(ByteOrder.LITTLE_ENDIAN);
            IntSink sink = value -> putSource(channel, buffer, value);
            for (int b = 0; b < bins.size(); b++) {
                if (bins.get(b).oversized()) {
                    ExternalSort.sort(binFiles.pathOf(b), chunkRecords, binFiles.directory(), sink, availableBytes);
                } else {
                    long[] packed = binFiles.loadPacked(b);
                    Arrays.sort(packed);
                    for (long value : packed) {
                        sink.accept((int) value);
                    }
                }
                binFiles.deleteBin(b);
            }
            drain(channel, buffer);
            channel.force(false);
        }
    }

    private static void putSource(FileChannel channel, ByteBuffer buffer, int value) throws IOException {
        if (buffer.remaining() < Integer.BYTES) {
            drain(channel, buffer);
        }
        buffer.putInt(value);
    }

    private static void growTo(IntArrayList inDegrees, IntArrayList outDegrees, int newSize) {
        while (outDegrees.size() < newSize) {
            inDegrees.add(0);
            outDegrees.add(0);
        }
    }

    private static int[] prefixSums(IntArrayList values) {
        int n = values.size();
        int[] sums = new int[n + 1];
        for (int i = 0; i < n; i++) {
            sums[i + 1] = sums[i] + values.getInt(i);
        }
        return sums;
    }

    private static void drain(FileChannel channel, ByteBuffer buffer) throws IOException {
        buffer.flip();
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
        buffer.clear();
    }
}
