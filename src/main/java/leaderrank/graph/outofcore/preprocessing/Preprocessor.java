package leaderrank.graph.outofcore.preprocessing;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import leaderrank.graph.IdMapper;
import leaderrank.graph.EdgeCursor;
import leaderrank.graph.EdgeSource;
import leaderrank.graph.outofcore.io.IntWriter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

// Builds the on-disk CSC in bounded RAM. Pass 1 maps ids and counts degrees while spilling dense
// edges, then edges are bucketed by destination and each bucket is sorted into the in-neighbour column.
public final class Preprocessor {

    private static final int EDGE_BUFFER_BYTES = 1 << 16;
    private static final int SOURCES_BUFFER_BYTES = 1 << 20;

    static Pass1Result buildIdMapAndSpill(EdgeSource source, Path denseEdges) throws IOException {
        try (EdgeCursor cursor = source.open()) {
            return buildIdMapAndSpill(cursor, denseEdges);
        }
    }

    // Pass 1. Assign dense ids in first-appearance order, tally in and out degrees, and write the
    // dense (from, to) edges for the bucketing pass.
    static Pass1Result buildIdMapAndSpill(EdgeCursor cursor, Path denseEdges) throws IOException {
        IdMapper mapper = new IdMapper();
        IntArrayList inDegrees = new IntArrayList();
        IntArrayList outDegrees = new IntArrayList();
        long edgeCount = 0;

        try (IntWriter edges = IntWriter.create(denseEdges, EDGE_BUFFER_BYTES)) {
            while (cursor.next()) {
                int denseFrom = mapper.mapOrAssign(cursor.from());
                int denseTo = mapper.mapOrAssign(cursor.to());
                growTo(inDegrees, outDegrees, mapper.size());
                inDegrees.set(denseTo, inDegrees.getInt(denseTo) + 1);
                outDegrees.set(denseFrom, outDegrees.getInt(denseFrom) + 1);
                edgeCount++;
                edges.write(denseFrom, denseTo);
            }
        }

        return new Pass1Result(mapper.originalIds(), prefixSums(inDegrees), outDegrees.toIntArray(), edgeCount);
    }

    private static Pass1Result mapAndSpill(EdgeSource source, Path denseEdges, int parallelism) throws IOException {
        if (parallelism > 1 && source.parallelFile().isPresent()) {
            return mapAndSpillParallel(source.parallelFile().get(), denseEdges, parallelism);
        }
        return buildIdMapAndSpill(source, denseEdges);
    }

    // Parse the file in parallel byte ranges, then replay the raw chunks in file order. Sequential
    // replay reproduces the exact single-pass id labelling, so the result is the same for any P.
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

    public static void process(EdgeSource source, Path sourcesPath, long maxEdgesPerBin) throws IOException {
        process(source, sourcesPath, maxEdgesPerBin, defaultParallelism());
    }

    public static PreprocessingData process(EdgeSource source, Path sourcesPath, long maxEdgesPerBin,
            int parallelism) throws IOException {
        Path denseEdges = createDenseEdgesFile();
        try {
            Pass1Result pass1 = mapAndSpill(source, denseEdges, parallelism);
            return assemble(denseEdges, pass1, sourcesPath, clampToInt(maxEdgesPerBin), Long.MAX_VALUE);
        } finally {
            Files.deleteIfExists(denseEdges);
        }
    }

    public static void process(EdgeSource source, Path sourcesPath, MemoryBudget budget) throws IOException {
        process(source, sourcesPath, budget, defaultParallelism());
    }

    public static PreprocessingData process(EdgeSource source, Path sourcesPath, MemoryBudget budget,
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

    // Plan destination bins under the edge limit, route the dense edges into per-bin files, then sort
    // each bin and append its source column. The dense edge file is dropped as soon as it is consumed.
    private static PreprocessingData assemble(Path denseEdges, Pass1Result pass1, Path sourcesPath,
            int maxEdgesPerBin, long availableBytes) throws IOException {
        List<Bin> bins = BinPlanner.plan(pass1.sourcesPtr(), maxEdgesPerBin);
        int binCount = bins.size();
        int distributionWaves = BinFiles.distributionWaves(availableBytes, binCount);

        try (BinFiles binFiles = BinFiles.create(bins)) {
            binFiles.distribute(denseEdges, availableBytes);
            Files.deleteIfExists(denseEdges);
            writeSortedSources(binFiles, sourcesPath, maxEdgesPerBin, availableBytes);
        }

        return new PreprocessingData(
                pass1.sourcesPtr(),
                pass1.outDegrees(),
                pass1.originalIds(),
                pass1.edgeCount(),
                sourcesPath,
                binCount,
                maxEdgesPerBin,
                distributionWaves
        );
    }

    private static int clampToInt(long value) {
        return (int) Math.clamp(value, 1, Integer.MAX_VALUE);
    }

    // Normal bins fit in RAM and sort with one Arrays.sort. An oversized bin (a hyper-node) goes through
    // external merge sort so its RAM stays bounded whatever the in-degree.
    private static void writeSortedSources(BinFiles binFiles, Path sourcesPath, int chunkRecords, long availableBytes)
            throws IOException {
        List<Bin> bins = binFiles.bins();
        try (IntWriter sources = IntWriter.create(sourcesPath, SOURCES_BUFFER_BYTES)) {
            for (int b = 0; b < bins.size(); b++) {
                if (bins.get(b).oversized()) {
                    ExternalSort.sort(binFiles.pathOf(b), chunkRecords, binFiles.directory(), sources::write, availableBytes);
                } else {
                    sortNormalBin(binFiles, b, sources);
                }
                binFiles.deleteBin(b);
            }
        }
    }

    private static void sortNormalBin(BinFiles binFiles, int binIndex, IntWriter sources) throws IOException {
        long[] packed = binFiles.loadPacked(binIndex);
        Arrays.sort(packed);
        for (long value : packed) {
            sources.write((int) value);
        }
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
}
