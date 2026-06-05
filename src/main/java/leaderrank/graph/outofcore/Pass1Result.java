package leaderrank.graph.outofcore;

import leaderrank.utils.IdMapper;

record Pass1Result(IdMapper mapper, int[] sourcesPtr, int[] outDegrees, long edgeCount) {}
