package leaderrank.graph.outofcore;

import leaderrank.graph.Graph;
import leaderrank.graph.edge.EdgeSource;

import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.PrimitiveIterator;

public final class OutOfCoreGraph implements Graph {
    private final OutOfCorePreprocessingData data;

    private OutOfCoreGraph(OutOfCorePreprocessingData data) {
        this.data = data;
    }

    public static Graph build(EdgeSource source) throws IOException {
        return new OutOfCoreGraph(OutOfCoreGraphPreprocessor.process(source));
    }

    @Override
    public int vertexCount() {
        return data.outDegrees().length;
    }

    @Override
    public int edgeCount() {
        return data.sources().length;
    }

    @Override
    public int outDegree(int denseId) {
        return data.outDegrees()[denseId];
    }

    @Override
    public int originalId(int denseId) {
        return data.originalIds()[denseId];
    }

    @Override
    public PrimitiveIterator.OfInt sourcesOf(int destinationDenseId) {
        return new PrimitiveIterator.OfInt() {
            private int index = data.sourcesPtr()[destinationDenseId];
            private final int end = data.sourcesPtr()[destinationDenseId + 1];

            @Override
            public boolean hasNext() {
                return index < end;
            }

            @Override
            public int nextInt() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return data.sources()[index++];
            }
        };
    }
}
