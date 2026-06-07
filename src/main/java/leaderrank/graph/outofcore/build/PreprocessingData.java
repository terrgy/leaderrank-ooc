package leaderrank.graph.outofcore.build;

import java.nio.file.Path;

public record PreprocessingData (int[] sourcesPtr, int[] outDegrees, int[] originalIds, long edgeCount,
        Path sourcesFile, int binCount, int maxEdgesPerBin, int distributionWaves) {}
