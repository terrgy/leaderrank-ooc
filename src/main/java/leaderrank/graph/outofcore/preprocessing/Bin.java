package leaderrank.graph.outofcore.preprocessing;

// A contiguous destination range [begin, end) spilled together. oversized marks a single hyper-node
// destination whose in-degree passes the bin limit, which then needs external sorting.
public record Bin(int begin, int end, long edgeCount, boolean oversized) {}
