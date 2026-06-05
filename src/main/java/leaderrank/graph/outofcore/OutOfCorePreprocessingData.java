package leaderrank.graph.outofcore;

public record OutOfCorePreprocessingData (int[] sourcesPtr, int[] sources, int[] outDegrees, int[] originalIds) {}
