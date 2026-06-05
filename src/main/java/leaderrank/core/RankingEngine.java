package leaderrank.core;

import leaderrank.graph.Graph;

import java.io.IOException;

public interface RankingEngine {

    LeaderRankResult run(Graph graph) throws IOException;
}
