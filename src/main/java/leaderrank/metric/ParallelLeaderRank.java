package leaderrank.metric;

import leaderrank.graph.Graph;
import leaderrank.graph.SourceCursor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class ParallelLeaderRank implements RankingEngine {

    public static final double DEFAULT_TOLERANCE = 1e-8;
    public static final int DEFAULT_MAX_ITERATIONS = 100;
    public static final int DEFAULT_SHARDS_PER_THREAD = 4;

    private final int threads;
    private final double tolerance;
    private final int maxIterations;

    public ParallelLeaderRank(int threads) {
        this(threads, DEFAULT_TOLERANCE, DEFAULT_MAX_ITERATIONS);
    }

    public ParallelLeaderRank(int threads, double tolerance, int maxIterations) {
        if (threads < 1) {
            throw new IllegalArgumentException("threads must be at least 1");
        }
        if (tolerance <= 0.0) {
            throw new IllegalArgumentException("tolerance must be positive");
        }
        if (maxIterations < 1) {
            throw new IllegalArgumentException("maxIterations must be at least 1");
        }
        this.threads = threads;
        this.tolerance = tolerance;
        this.maxIterations = maxIterations;
    }

    @Override
    public LeaderRankResult run(Graph graph) throws IOException {
        int n = graph.vertexCount();
        if (n == 0) {
            return new LeaderRankResult(new double[0], 0, true);
        }

        double[] scores = new double[n];
        Arrays.fill(scores, 1.0);
        double ground = 0.0;
        double[] next = new double[n];
        double[] contrib = new double[n];

        List<Shard> shards = planShards(graph, n);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            int iterations = 0;
            boolean converged = false;
            while (iterations < maxIterations) {
                iterations++;

                for (int v = 0; v < n; v++) {
                    contrib[v] = scores[v] / (graph.outDegree(v) + 1);
                }
                double base = ground / n;
                double nextGround = 0.0;
                for (int v = 0; v < n; v++) {
                    nextGround += contrib[v];
                }

                gather(graph, shards, contrib, base, next, pool);

                double delta = Math.abs(nextGround - ground);
                for (int v = 0; v < n; v++) {
                    delta += Math.abs(next[v] - scores[v]);
                }

                double[] swap = scores;
                scores = next;
                next = swap;
                ground = nextGround;

                if (delta < tolerance * n) {
                    converged = true;
                    break;
                }
            }

            double share = ground / n;
            for (int v = 0; v < n; v++) {
                scores[v] += share;
            }
            return new LeaderRankResult(scores, iterations, converged);
        } finally {
            pool.shutdown();
        }
    }

    private List<Shard> planShards(Graph graph, int n) {
        int[] sourcesPtr = new int[n + 1];
        for (int d = 0; d < n; d++) {
            sourcesPtr[d + 1] = sourcesPtr[d] + graph.inDegree(d);
        }
        return ShardPlanner.plan(sourcesPtr, threads * DEFAULT_SHARDS_PER_THREAD);
    }

    private static void gather(Graph graph, List<Shard> shards, double[] contrib, double base,
            double[] next, ExecutorService pool) throws IOException {
        List<Callable<Void>> tasks = new ArrayList<>(shards.size());
        for (Shard shard : shards) {
            tasks.add(() -> {
                gatherShard(graph, shard, contrib, base, next);
                return null;
            });
        }
        try {
            for (Future<Void> future : pool.invokeAll(tasks)) {
                future.get();
            }
        } catch (ExecutionException e) {
            throw asIoException(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("parallel gather interrupted", e);
        }
    }

    private static void gatherShard(Graph graph, Shard shard, double[] contrib, double base, double[] next)
            throws IOException {
        try (SourceCursor cursor = graph.openSourceCursor(shard.begin())) {
            for (int d = shard.begin(); d < shard.end(); d++) {
                int degree = graph.inDegree(d);
                double sum = base;
                for (int k = 0; k < degree; k++) {
                    sum += contrib[cursor.next()];
                }
                next[d] = sum;
            }
        }
    }

    private static IOException asIoException(Throwable cause) {
        if (cause instanceof IOException io) {
            return io;
        }
        return new IOException("parallel gather failed", cause);
    }
}
