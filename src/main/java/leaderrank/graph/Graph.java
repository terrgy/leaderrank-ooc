package leaderrank.graph;


import java.io.IOException;
import java.util.PrimitiveIterator;

// Read model for the engine. Vertices are dense ids 0..n-1 and edges are reached by destination
// (pull). That direction is what turns the out-of-core layout into a sequential scan.
public interface Graph {

    int vertexCount();

    long edgeCount();

    int outDegree(int denseId);

    int inDegree(int denseId);

    int originalId(int denseId);

    // One-shot random read of a destination's in-neighbours. Used by tests and the dense oracle.
    PrimitiveIterator.OfInt sourcesOf(int destinationDenseId) throws IOException;

    // Sequential cursor over in-neighbours from a destination onward. The engine's hot path.
    SourceCursor openSourceCursor(int fromDestinationDenseId) throws IOException;

    default SourceCursor openSourceCursor() throws IOException {
        return openSourceCursor(0);
    }
}
