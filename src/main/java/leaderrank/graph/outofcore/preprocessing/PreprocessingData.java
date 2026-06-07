package leaderrank.graph.outofcore.preprocessing;

import java.nio.file.Path;

// Everything the streaming graph keeps resident plus the path of the on-disk in-neighbour column. The
// last three fields are reported stats.
public record PreprocessingData (int[] sourcesPtr, int[] outDegrees, int[] originalIds, long edgeCount,
        Path sourcesFile, int binCount, int maxEdgesPerBin, int distributionWaves) {}
