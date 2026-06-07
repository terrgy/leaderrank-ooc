package leaderrank.graph;


import java.io.IOException;
import java.util.PrimitiveIterator;

public interface Graph {

    int vertexCount();

    long edgeCount();

    int outDegree(int denseId);

    int inDegree(int denseId);

    int originalId(int denseId);

    PrimitiveIterator.OfInt sourcesOf(int destinationDenseId) throws IOException;

    SourceCursor openSourceCursor(int fromDestinationDenseId) throws IOException;

    default SourceCursor openSourceCursor() throws IOException {
        return openSourceCursor(0);
    }
}
