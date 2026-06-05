package leaderrank.graph.outofcore;

import java.nio.file.Path;

public record OutOfCorePreprocessingData (int[] sourcesPtr, int[] outDegrees, int[] originalIds, long edgeCount, Path sourcesFile) {}
