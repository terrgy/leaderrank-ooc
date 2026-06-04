package leaderrank.core;

import java.util.Arrays;
import leaderrank.graph.Graph;

public final class LeaderRank {

    public static final double DEFAULT_TOLERANCE = 1e-8;
    public static final int DEFAULT_MAX_ITERATIONS = 100;

    private final double tolerance;
    private final int maxIterations;

    public LeaderRank() {
        this(DEFAULT_TOLERANCE, DEFAULT_MAX_ITERATIONS);
    }

    public LeaderRank(double tolerance, int maxIterations) {
        if (tolerance <= 0.0) {
            throw new IllegalArgumentException("tolerance must be positive");
        }
        if (maxIterations < 1) {
            throw new IllegalArgumentException("maxIterations must be at least 1");
        }
        this.tolerance = tolerance;
        this.maxIterations = maxIterations;
    }

    public LeaderRankResult run(Graph graph) {
        int n = graph.vertexCount();
        if (n == 0) {
            return new LeaderRankResult(new double[0], 0, true);
        }

        int[] sources = graph.sources();
        int[] targets = graph.targets();
        int[] outDegrees = graph.outDegrees();

        double[] scores = new double[n];
        Arrays.fill(scores, 1.0);
        double ground = 0.0;

        double[] next = new double[n];
        double[] contrib = new double[n];

        int iterations = 0;
        boolean converged = false;
        while (iterations < maxIterations) {
            iterations++;

            for (int v = 0; v < n; v++) {
                contrib[v] = scores[v] / (outDegrees[v] + 1);
            }

            double base = ground / n;
            Arrays.fill(next, base);

            double nextGround = 0.0;
            for (int v = 0; v < n; v++) {
                nextGround += contrib[v];
            }

            for (int e = 0; e < sources.length; e++) {
                next[targets[e]] += contrib[sources[e]];
            }

            double delta = Math.abs(nextGround - ground);
            for (int v = 0; v < n; v++) {
                delta += Math.abs(next[v] - scores[v]);
            }

            double[] swap = scores;
            scores = next;
            next = swap;
            ground = nextGround;

            if (delta < tolerance) {
                converged = true;
                break;
            }
        }

        double share = ground / n;
        for (int v = 0; v < n; v++) {
            scores[v] += share;
        }

        return new LeaderRankResult(scores, iterations, converged);
    }
}
