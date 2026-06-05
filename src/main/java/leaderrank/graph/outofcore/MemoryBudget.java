package leaderrank.graph.outofcore;

public record MemoryBudget(long totalBytes) {

    private static final long RESERVE_BYTES = 32L << 20;
    private static final long BYTES_PER_VERTEX = 48;
    private static final long BYTES_PER_PACKED_EDGE = Long.BYTES;

    public MemoryBudget {
        if (totalBytes < 1) {
            throw new IllegalArgumentException("memory budget must be positive");
        }
    }

    public int maxEdgesPerBin(int vertexCount) {
        long available = totalBytes - RESERVE_BYTES - BYTES_PER_VERTEX * vertexCount;
        long edges = available / BYTES_PER_PACKED_EDGE;
        if (edges < 1) {
            return 1;
        }
        return (int) Math.min(edges, Integer.MAX_VALUE);
    }
}
