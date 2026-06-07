package leaderrank.metric;

import leaderrank.graph.Graph;

import java.io.IOException;

// Reference oracle. Builds the full (n+1)x(n+1) transition matrix with the ground node at index n and
// runs plain power iteration. Kept independent of the streaming engines so the cross-check stays honest.
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
    public LeaderRankResult run(Graph graph) throws IOException {
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

            multiply(transition, scores, next);
            double delta = l1Distance(scores, next);

            double[] swap = scores;
            scores = next;
            next = swap;

            if (delta < tolerance * n) {
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

    // Entry [i][j] is the share of score that flows from j to i, so every column sums to one.
    private static double[][] buildTransition(Graph graph, int size, int ground) throws IOException {
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

    private static void multiply(double[][] transition, double[] vector, double[] result) {
        for (int i = 0; i < result.length; i++) {
            double[] row = transition[i];
            double sum = 0.0;
            for (int j = 0; j < vector.length; j++) {
                sum += row[j] * vector[j];
            }
            result[i] = sum;
        }
    }

    private static double l1Distance(double[] previous, double[] current) {
        double delta = 0.0;
        for (int i = 0; i < previous.length; i++) {
            delta += Math.abs(current[i] - previous[i]);
        }
        return delta;
    }
}
