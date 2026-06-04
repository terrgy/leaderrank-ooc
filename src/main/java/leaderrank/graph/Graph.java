package leaderrank.graph;

public final class Graph {

    private final int vertexCount;
    private final int[] sources;
    private final int[] targets;
    private final int[] outDegrees;
    private final int[] originalIds;

    public Graph(int vertexCount, int[] sources, int[] targets, int[] outDegrees, int[] originalIds) {
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

    public int vertexCount() {
        return vertexCount;
    }

    public int edgeCount() {
        return sources.length;
    }

    public int[] sources() {
        return sources;
    }

    public int[] targets() {
        return targets;
    }

    public int[] outDegrees() {
        return outDegrees;
    }

    public int outDegree(int denseId) {
        return outDegrees[denseId];
    }

    public int originalId(int denseId) {
        return originalIds[denseId];
    }
}
