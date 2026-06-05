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

    public static MemoryBudget discover() {
        return new MemoryBudget(Runtime.getRuntime().maxMemory());
    }

    public long availableBytes(int vertexCount) {
        long available = totalBytes - RESERVE_BYTES - BYTES_PER_VERTEX * vertexCount;
        return available < 0 ? 0 : available;
    }

    public int maxEdgesPerBin(int vertexCount) {
        long edges = availableBytes(vertexCount) / BYTES_PER_PACKED_EDGE;
        if (edges < 1) {
            return 1;
        }
        return (int) Math.min(edges, Integer.MAX_VALUE);
    }
}
