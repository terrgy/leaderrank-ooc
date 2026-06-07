package leaderrank.graph.outofcore.preprocessing;

public record Bin(int begin, int end, long edgeCount, boolean oversized) {}
