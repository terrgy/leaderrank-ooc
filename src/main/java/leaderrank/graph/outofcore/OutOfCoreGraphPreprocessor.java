package leaderrank.graph.outofcore;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import leaderrank.utils.IdMapper;
import leaderrank.graph.edge.EdgeCursor;
import leaderrank.graph.edge.EdgeSource;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;

final class OutOfCoreGraphPreprocessor {

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

    static OutOfCorePreprocessingData process(EdgeSource source, Path sourcesPath, long maxEdgesPerBin)
            throws IOException {
        return assemble(source, pass1(source), sourcesPath, clampToInt(maxEdgesPerBin));
    }

    static OutOfCorePreprocessingData process(EdgeSource source, Path sourcesPath, MemoryBudget budget)
            throws IOException {
        Pass1Result pass1 = pass1(source);
        return assemble(source, pass1, sourcesPath, budget.maxEdgesPerBin(pass1.outDegrees().length));
    }

    private static OutOfCorePreprocessingData assemble(EdgeSource source, Pass1Result pass1, Path sourcesPath,
            int maxEdgesPerBin) throws IOException {
        List<Bin> bins = BinPlanner.plan(pass1.sourcesPtr(), maxEdgesPerBin);

        try (BinFiles binFiles = BinFiles.create(bins)) {
            binFiles.distribute(source, pass1.mapper());
            writeSortedSources(binFiles, sourcesPath, maxEdgesPerBin);
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

    private static void writeSortedSources(BinFiles binFiles, Path sourcesPath, int chunkRecords)
            throws IOException {
        List<Bin> bins = binFiles.bins();
        try (FileChannel channel = FileChannel.open(sourcesPath,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer buffer = ByteBuffer.allocateDirect(1 << 20).order(ByteOrder.LITTLE_ENDIAN);
            IntSink sink = value -> putSource(channel, buffer, value);
            for (int b = 0; b < bins.size(); b++) {
                if (bins.get(b).oversized()) {
                    ExternalSort.sort(binFiles.pathOf(b), chunkRecords, binFiles.directory(), sink);
                } else {
                    long[] packed = binFiles.loadPacked(b);
                    Arrays.sort(packed);
                    for (long value : packed) {
                        sink.accept((int) value);
                    }
                }
            }
            drain(channel, buffer);
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
