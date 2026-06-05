package leaderrank.core;

import leaderrank.graph.Graph;

public interface RankingEngine {

    LeaderRankResult run(Graph graph);
}
