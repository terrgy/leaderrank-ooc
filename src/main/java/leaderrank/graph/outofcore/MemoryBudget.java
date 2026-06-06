package leaderrank.graph.outofcore;

public record MemoryBudget(long totalBytes) {

    private static final long RESERVE_BYTES = 32L << 20;
    private static final long RESIDENT_BYTES_PER_VERTEX = 16;
    private static final long BYTES_PER_PACKED_EDGE = Long.BYTES;
    private static final long OLD_GEN_NUMERATOR = 3;
    private static final long OLD_GEN_DENOMINATOR = 5;

    public MemoryBudget {
        if (totalBytes < 1) {
            throw new IllegalArgumentException("memory budget must be positive");
        }
    }

    public static MemoryBudget discover() {
        return new MemoryBudget(Runtime.getRuntime().maxMemory());
    }

    public long availableBytes(int vertexCount) {
        long available = totalBytes - RESERVE_BYTES - RESIDENT_BYTES_PER_VERTEX * vertexCount;
        return available < 0 ? 0 : available;
    }

    public int maxEdgesPerBin(int vertexCount) {
        long oldGenBytes = totalBytes * OLD_GEN_NUMERATOR / OLD_GEN_DENOMINATOR;
        long sortPool = oldGenBytes - RESIDENT_BYTES_PER_VERTEX * vertexCount;
        long pool = Math.min(availableBytes(vertexCount), sortPool);
        long edges = pool / BYTES_PER_PACKED_EDGE;
        if (edges < 1) {
            return 1;
        }
        return (int) Math.min(edges, Integer.MAX_VALUE);
    }
}
