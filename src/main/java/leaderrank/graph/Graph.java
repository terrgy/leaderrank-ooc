package leaderrank.graph;

import leaderrank.graph.source.SourceCursor;

import java.io.IOException;
import java.util.PrimitiveIterator;

public interface Graph {

    int vertexCount();

    long edgeCount();

    int outDegree(int denseId);

    int inDegree(int denseId);

    int originalId(int denseId);

    PrimitiveIterator.OfInt sourcesOf(int destinationDenseId) throws IOException;

    SourceCursor openSourceCursor() throws IOException;
}
