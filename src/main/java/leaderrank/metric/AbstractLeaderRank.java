package leaderrank.metric;

import leaderrank.graph.Graph;
import leaderrank.graph.SourceCursor;

import java.io.IOException;
import java.util.Arrays;

// LeaderRank as a power method on the graph plus a ground node tied both ways to every vertex.
// One iteration spreads each score over its out-neighbours and the ground, then gathers per
// destination. Subclasses choose only how that gather runs.
abstract class AbstractLeaderRank implements RankingEngine {

    public static final double DEFAULT_TOLERANCE = 1e-8;
    public static final int DEFAULT_MAX_ITERATIONS = 100;

    private final double tolerance;
    private final int maxIterations;

    AbstractLeaderRank(double tolerance, int maxIterations) {
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
    public final LeaderRankResult run(Graph graph) throws IOException {
        int n = graph.vertexCount();
        if (n == 0) {
            return new LeaderRankResult(new double[0], 0, true);
        }

        double[] scores = new double[n];
        Arrays.fill(scores, 1.0);
        double[] next = new double[n];
        double[] contrib = new double[n];
        double ground = 0.0;

        int iterations = 0;
        boolean converged = false;
        try (Gather gather = openGather(graph, n)) {
            while (iterations < maxIterations) {
                iterations++;

                contributions(graph, scores, contrib);
                double base = ground / n;
                double nextGround = sumOf(contrib);

                gather.gather(graph, contrib, base, next);

                // Seeding the sum with the ground delta keeps the exact serial order. Float addition
                // is not associative, so this is what keeps parallel and serial runs bit for bit equal.
                double delta = l1Distance(Math.abs(nextGround - ground), scores, next);
                double[] swap = scores;
                scores = next;
                next = swap;
                ground = nextGround;

                // Threshold scales with n because total mass stays at n. A fixed tolerance would
                // never fire on large graphs.
                if (delta < tolerance * n) {
                    converged = true;
                    break;
                }
            }
        }

        redistribute(scores, ground / n);
        return new LeaderRankResult(scores, iterations, converged);
    }

    abstract Gather openGather(Graph graph, int n) throws IOException;

    // Pull gather over destinations [begin, end). Each output cell has a single writer, so the result
    // does not depend on how the range is split across threads.
    static void gatherRange(Graph graph, int begin, int end, double[] contrib, double base, double[] next)
            throws IOException {
        try (SourceCursor cursor = graph.openSourceCursor(begin)) {
            for (int d = begin; d < end; d++) {
                int degree = graph.inDegree(d);
                double sum = base;
                for (int k = 0; k < degree; k++) {
                    sum += contrib[cursor.next()];
                }
                next[d] = sum;
            }
        }
    }

    private static void contributions(Graph graph, double[] scores, double[] contrib) {
        for (int v = 0; v < contrib.length; v++) {
            contrib[v] = scores[v] / (graph.outDegree(v) + 1);
        }
    }

    private static double sumOf(double[] values) {
        double total = 0.0;
        for (double value : values) {
            total += value;
        }
        return total;
    }

    private static double l1Distance(double seed, double[] previous, double[] current) {
        double delta = seed;
        for (int v = 0; v < previous.length; v++) {
            delta += Math.abs(current[v] - previous[v]);
        }
        return delta;
    }

    private static void redistribute(double[] scores, double share) {
        for (int v = 0; v < scores.length; v++) {
            scores[v] += share;
        }
    }

    // Fills next[] from contrib[] for one iteration. Opened once per run so a parallel gather can keep
    // a thread pool alive across iterations and free it on close.
    interface Gather extends AutoCloseable {
        void gather(Graph graph, double[] contrib, double base, double[] next) throws IOException;

        @Override
        default void close() throws IOException {
        }
    }
}
