package leaderrank.metric;

public record LeaderRankResult(double[] scores, int iterations, boolean converged) {
}
