package leaderrank.graph;

public final class InMemoryGraph implements Graph {
    private final int vertexCount;
    private final int[] sources;
    private final int[] targets;
    private final int[] outDegrees;
    private final int[] originalIds;

    public InMemoryGraph(int vertexCount, int[] sources, int[] targets, int[] outDegrees, int[] originalIds) {
        if (vertexCount < 0) {
            throw new IllegalArgumentException("vertexCount must be non-negative");
        }
        if (sources.length != targets.length) {
            throw new IllegalArgumentException("sources and targets must have equal length");
        }
        if (outDegrees.length != vertexCount || originalIds.length != vertexCount) {
            throw new IllegalArgumentException("outDegrees and originalIds must have length vertexCount");
        }
        this.vertexCount = vertexCount;
        this.sources = sources;
        this.targets = targets;
        this.outDegrees = outDegrees;
        this.originalIds = originalIds;
    }

    @Override
    public int vertexCount() {
        return vertexCount;
    }

    @Override
    public int edgeCount() {
        return sources.length;
    }

    @Override
    public int outDegree(int denseId) {
        return outDegrees[denseId];
    }

    @Override
    public int originalId(int denseId) {
        return originalIds[denseId];
    }

    @Override
    public VertexSources getVertexSources(int destinationDenseId) {
        return new InMemoryVertexSources(this.sources, this.targets, destinationDenseId);
    }
}
