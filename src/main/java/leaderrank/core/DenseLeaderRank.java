package leaderrank.core;

import leaderrank.graph.Graph;

public final class DenseLeaderRank implements RankingEngine {

    public static final double DEFAULT_TOLERANCE = 1e-8;
    public static final int DEFAULT_MAX_ITERATIONS = 100;

    private final double tolerance;
    private final int maxIterations;

    public DenseLeaderRank() {
        this(DEFAULT_TOLERANCE, DEFAULT_MAX_ITERATIONS);
    }

    public DenseLeaderRank(double tolerance, int maxIterations) {
        if (tolerance <= 0.0) {
            throw new IllegalArgumentException("tolerance must be positive");
        }
        if (maxIterations < 1) {
            throw new IllegalArgumentException("maxIterations must be at least 1");
        }
        this.tolerance = tolerance;
        this.maxIterations = maxIterations;
    }

    @Override
    public LeaderRankResult run(Graph graph) {
        int n = graph.vertexCount();
        if (n == 0) {
            return new LeaderRankResult(new double[0], 0, true);
        }

        int size = n + 1;
        int ground = n;
        double[][] transition = buildTransition(graph, size, ground);

        double[] scores = new double[size];
        for (int v = 0; v < n; v++) {
            scores[v] = 1.0;
        }
        double[] next = new double[size];

        int iterations = 0;
        boolean converged = false;
        while (iterations < maxIterations) {
            iterations++;

            for (int i = 0; i < size; i++) {
                double[] row = transition[i];
                double sum = 0.0;
                for (int j = 0; j < size; j++) {
                    sum += row[j] * scores[j];
                }
                next[i] = sum;
            }

            double delta = 0.0;
            for (int i = 0; i < size; i++) {
                delta += Math.abs(next[i] - scores[i]);
            }

            double[] swap = scores;
            scores = next;
            next = swap;

            if (delta < tolerance) {
                converged = true;
                break;
            }
        }

        double share = scores[ground] / n;
        double[] ranks = new double[n];
        for (int v = 0; v < n; v++) {
            ranks[v] = scores[v] + share;
        }
        return new LeaderRankResult(ranks, iterations, converged);
    }

    private static double[][] buildTransition(Graph graph, int size, int ground) {
        int n = ground;
        double[][] transition = new double[size][size];

        for (int v = 0; v < n; v++) {
            transition[ground][v] += 1.0 / (graph.outDegree(v) + 1);
        }
        for (int i = 0; i < n; i++) {
            var iterator = graph.sourcesOf(i);
            while (iterator.hasNext()) {
                int from = iterator.nextInt();
                transition[i][from] += 1.0 / (graph.outDegree(from) + 1);
            }
        }
        double groundWeight = 1.0 / n;
        for (int i = 0; i < n; i++) {
            transition[i][ground] = groundWeight;
        }
        return transition;
    }
}
