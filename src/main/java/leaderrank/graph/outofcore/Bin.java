package leaderrank.graph.outofcore;

public record Bin(int begin, int end, long edgeCount, boolean oversized) {}
