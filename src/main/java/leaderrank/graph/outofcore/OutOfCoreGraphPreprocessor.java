package leaderrank.graph.outofcore;

import leaderrank.graph.IdMapper;
import leaderrank.graph.edge.EdgeCursor;
import leaderrank.graph.edge.EdgeSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class OutOfCoreGraphPreprocessor {

    private OutOfCoreGraphPreprocessor() {
    }

    static OutOfCorePreprocessingData process(EdgeSource source) throws IOException {
        IdMapper mapper = new IdMapper();
        List<Integer> inDegrees = new ArrayList<>();
        List<Integer> outDegrees = new ArrayList<>();

        try (EdgeCursor cursor = source.open()) {
            while (cursor.next()) {
                int denseFrom = mapper.mapOrAssign(cursor.from());
                int denseTo = mapper.mapOrAssign(cursor.to());
                growTo(inDegrees, outDegrees, mapper.size());
                inDegrees.set(denseTo, inDegrees.get(denseTo) + 1);
                outDegrees.set(denseFrom, outDegrees.get(denseFrom) + 1);
            }
        }

        int[] sourcesPtr = prefixSums(inDegrees);
        int[] sources = new int[sourcesPtr[inDegrees.size()]];
        int[] writePos = sourcesPtr.clone();

        try (EdgeCursor cursor = source.open()) {
            while (cursor.next()) {
                int denseTo = mapper.denseOf(cursor.to());
                sources[writePos[denseTo]++] = mapper.denseOf(cursor.from());
            }
        }

        for (int d = 0; d < inDegrees.size(); d++) {
            Arrays.sort(sources, sourcesPtr[d], sourcesPtr[d + 1]);
        }

        return new OutOfCorePreprocessingData(
                sourcesPtr,
                sources,
                toIntArray(outDegrees),
                mapper.originalIds()
        );
    }

    private static void growTo(List<Integer> inDegrees, List<Integer> outDegrees, int newSize) {
        while (outDegrees.size() < newSize) {
            inDegrees.add(0);
            outDegrees.add(0);
        }
    }

    private static int[] prefixSums(List<Integer> values) {
        int n = values.size();
        int[] sums = new int[n + 1];
        for (int i = 0; i < n; i++) {
            sums[i + 1] = sums[i] + values.get(i);
        }
        return sums;
    }

    private static int[] toIntArray(List<Integer> values) {
        return values.stream().mapToInt(Integer::intValue).toArray();
    }
}
