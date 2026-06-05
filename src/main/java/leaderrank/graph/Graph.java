package leaderrank.graph;

import java.util.PrimitiveIterator;

public interface Graph {

    int vertexCount();

    int edgeCount();

    PrimitiveIterator.OfInt sourcesOf(int destinationDenseId);

    int outDegree(int denseId);

    int originalId(int denseId);
}
