package leaderrank.metric;

import leaderrank.graph.Graph;

public final class LeaderRank extends AbstractLeaderRank {

    public LeaderRank() {
        this(DEFAULT_TOLERANCE, DEFAULT_MAX_ITERATIONS);
    }

    public LeaderRank(double tolerance, int maxIterations) {
        super(tolerance, maxIterations);
    }

    @Override
    Gather openGather(Graph graph, int n) {
        return (g, contrib, base, next) -> gatherRange(g, 0, n, contrib, base, next);
    }
}
