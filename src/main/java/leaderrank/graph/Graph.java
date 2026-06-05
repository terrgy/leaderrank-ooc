package leaderrank.graph;

public interface Graph {

    int vertexCount();

    int edgeCount();

    VertexSources getVertexSources(int destinationDenseId);

    int outDegree(int denseId);

    int originalId(int denseId);
}
