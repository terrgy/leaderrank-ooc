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

final class OutOfCoreGraphPreprocessor {

    static OutOfCorePreprocessingData process(EdgeSource source, Path sourcesPath) throws IOException {
        IdMapper mapper = new IdMapper();
        IntArrayList inDegrees = new IntArrayList();
        IntArrayList outDegrees = new IntArrayList();

        try (EdgeCursor cursor = source.open()) {
            while (cursor.next()) {
                int denseFrom = mapper.mapOrAssign(cursor.from());
                int denseTo = mapper.mapOrAssign(cursor.to());
                growTo(inDegrees, outDegrees, mapper.size());
                inDegrees.set(denseTo, inDegrees.getInt(denseTo) + 1);
                outDegrees.set(denseFrom, outDegrees.getInt(denseFrom) + 1);
            }
        }

        int[] sourcesPtr = prefixSums(inDegrees);
        long edgesCount = 0;
        int[] sources = new int[sourcesPtr[inDegrees.size()]];
        int[] writePos = sourcesPtr.clone();

        try (EdgeCursor cursor = source.open()) {
            while (cursor.next()) {
                int denseTo = mapper.denseOf(cursor.to());
                sources[writePos[denseTo]++] = mapper.denseOf(cursor.from());
                ++edgesCount;
            }
        }

        for (int d = 0; d < inDegrees.size(); d++) {
            Arrays.sort(sources, sourcesPtr[d], sourcesPtr[d + 1]);
        }

        writeSources(sources, sourcesPath);

        return new OutOfCorePreprocessingData(
                sourcesPtr,
                outDegrees.toIntArray(),
                mapper.originalIds(),
                edgesCount,
                sourcesPath
        );
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

    private static void writeSources(int[] sources, Path path) throws IOException {
        try (FileChannel channel = FileChannel.open(path,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer buffer = ByteBuffer.allocateDirect(1 << 20).order(ByteOrder.LITTLE_ENDIAN);
            for (int value : sources) {
                if (!buffer.hasRemaining()) {
                    drain(channel, buffer);
                }
                buffer.putInt(value);
            }
            drain(channel, buffer);
        }
    }

    private static void drain(FileChannel channel, ByteBuffer buffer) throws IOException {
        buffer.flip();
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
        buffer.clear();
    }
}
