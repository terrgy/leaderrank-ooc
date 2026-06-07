package leaderrank.metric;

import leaderrank.graph.Graph;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

// Parallel gather. Destinations split into edge-balanced shards and each shard owns a private slice
// of next[], so no two threads touch the same cell and the output matches the serial run for any P.
public final class ParallelLeaderRank extends AbstractLeaderRank {

    public static final int DEFAULT_SHARDS_PER_THREAD = 4;

    private final int threads;

    public ParallelLeaderRank(int threads) {
        this(threads, DEFAULT_TOLERANCE, DEFAULT_MAX_ITERATIONS);
    }

    public ParallelLeaderRank(int threads, double tolerance, int maxIterations) {
        super(tolerance, maxIterations);
        if (threads < 1) {
            throw new IllegalArgumentException("threads must be at least 1");
        }
        this.threads = threads;
    }

    @Override
    Gather openGather(Graph graph, int n) {
        return new ParallelGather(Executors.newFixedThreadPool(threads), planShards(graph, n));
    }

    // More shards than threads so the pool can steal work and one heavy hyper-node shard does not
    // stall the rest.
    private List<Shard> planShards(Graph graph, int n) {
        int[] sourcesPtr = new int[n + 1];
        for (int d = 0; d < n; d++) {
            sourcesPtr[d + 1] = sourcesPtr[d] + graph.inDegree(d);
        }
        return ShardPlanner.plan(sourcesPtr, threads * DEFAULT_SHARDS_PER_THREAD);
    }

    private static IOException asIoException(Throwable cause) {
        if (cause instanceof IOException io) {
            return io;
        }
        return new IOException("parallel gather failed", cause);
    }

    private static final class ParallelGather implements Gather {

        private final ExecutorService pool;
        private final List<Shard> shards;

        ParallelGather(ExecutorService pool, List<Shard> shards) {
            this.pool = pool;
            this.shards = shards;
        }

        @Override
        public void gather(Graph graph, double[] contrib, double base, double[] next) throws IOException {
            List<Callable<Void>> tasks = new ArrayList<>(shards.size());
            for (Shard shard : shards) {
                tasks.add(() -> {
                    gatherRange(graph, shard.begin(), shard.end(), contrib, base, next);
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

        @Override
        public void close() {
            pool.shutdown();
        }
    }
}
