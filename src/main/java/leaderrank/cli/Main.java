package leaderrank.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;
import leaderrank.core.DenseLeaderRank;
import leaderrank.core.LeaderRank;
import leaderrank.core.LeaderRankResult;
import leaderrank.core.ParallelLeaderRank;
import leaderrank.core.RankingEngine;
import leaderrank.gen.RmatGenerator;
import leaderrank.graph.Graph;
import leaderrank.graph.Shard;
import leaderrank.graph.edge.EdgeSource;
import leaderrank.graph.inmemory.InMemoryGraph;
import leaderrank.graph.outofcore.Bin;
import leaderrank.graph.outofcore.BinFiles;
import leaderrank.graph.outofcore.EdgeBinning;
import leaderrank.graph.outofcore.OutOfCoreGraph;
import leaderrank.io.CsvEdgeSource;
import leaderrank.io.RankCsvWriter;
import leaderrank.utils.Rss;

public final class Main {

    private static final long DEFAULT_BIN_BUDGET = 1L << 20;

    public static void main(String[] args) throws IOException {
        if (args.length >= 1 && args[0].equals("verify")) {
            verify(args);
        } else if (args.length >= 1 && args[0].equals("generate")) {
            generate(args);
        } else if (args.length >= 1 && args[0].equals("plan")) {
            plan(args);
        } else {
            rank(args);
        }
    }

    private static void rank(String[] args) throws IOException {
        List<String> positionals = new ArrayList<>();
        boolean dense = false;
        boolean inMemory = false;
        long budget = -1;
        int threads = Runtime.getRuntime().availableProcessors();
        int repeat = 1;
        for (String arg : args) {
            if (arg.equals("--dense")) {
                dense = true;
            } else if (arg.equals("--in-memory")) {
                inMemory = true;
            } else if (arg.startsWith("--budget=")) {
                budget = Long.parseLong(arg.substring("--budget=".length()));
            } else if (arg.startsWith("--threads=")) {
                threads = Integer.parseInt(arg.substring("--threads=".length()));
            } else if (arg.startsWith("--repeat=")) {
                repeat = Integer.parseInt(arg.substring("--repeat=".length()));
            } else {
                positionals.add(arg);
            }
        }
        if (positionals.isEmpty()) {
            System.err.println("Usage: leaderrank <edges.csv> [output.csv] [--dense] [--in-memory] [--budget=EDGES_PER_BIN] [--threads=P] [--repeat=N]");
            System.err.println("       leaderrank verify <edges.csv>");
            System.err.println("       leaderrank generate <out.csv> --scale=N --edges=M [--seed=S]");
            System.err.println("       leaderrank plan <edges.csv> [--budget=EDGES_PER_BIN] [--distribute] [--shards=S]");
            System.exit(2);
            return;
        }

        EdgeSource source = new CsvEdgeSource(Path.of(positionals.getFirst()));
        long buildStart = System.nanoTime();
        Graph graph;
        if (inMemory) {
            graph = InMemoryGraph.build(source);
        } else if (budget > 0) {
            graph = OutOfCoreGraph.build(source, budget);
        } else {
            graph = OutOfCoreGraph.build(source);
        }
        double buildMillis = millisSince(buildStart);

        RankingEngine engine = dense ? new DenseLeaderRank() : new ParallelLeaderRank(threads);
        LeaderRankResult result = null;
        double computeMillis = 0.0;
        for (int r = 0; r < repeat; r++) {
            long computeStart = System.nanoTime();
            result = engine.run(graph);
            computeMillis = millisSince(computeStart);
            if (repeat > 1) {
                System.out.printf(Locale.ROOT, "compute[%d]: %.1f ms%n", r, computeMillis);
            }
        }

        if (!dense) {
            System.out.println("threads: " + threads);
        }
        System.out.printf(Locale.ROOT, "preprocess: %.1f ms%n", buildMillis);
        System.out.printf(Locale.ROOT, "compute: %.1f ms%n", computeMillis);
        printStats(graph, result);
        if (positionals.size() >= 2) {
            Path output = Path.of(positionals.get(1));
            new RankCsvWriter().write(output, graph, result.scores());
            System.out.println("wrote ranks to " + output);
        } else {
            printRanks(graph, result.scores());
        }
    }

    private static void verify(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("Usage: leaderrank verify <edges.csv>");
            System.exit(2);
            return;
        }

        EdgeSource source = new CsvEdgeSource(Path.of(args[1]));
        Graph graph = OutOfCoreGraph.build(source);
        LeaderRankResult fast = new LeaderRank().run(graph);
        LeaderRankResult truth = new DenseLeaderRank().run(graph);

        double[] a = fast.scores();
        double[] b = truth.scores();
        double maxDiff = 0.0;
        for (int v = 0; v < a.length; v++) {
            maxDiff = Math.max(maxDiff, Math.abs(a[v] - b[v]));
        }

        System.out.println("vertices: " + graph.vertexCount());
        System.out.println("edges: " + graph.edgeCount());
        System.out.println("streaming iterations: " + fast.iterations()
                + (fast.converged() ? " (converged)" : " (max reached)"));
        System.out.println("dense iterations: " + truth.iterations()
                + (truth.converged() ? " (converged)" : " (max reached)"));
        System.out.printf(Locale.ROOT, "max abs difference: %.3e%n", maxDiff);
    }

    private static void generate(String[] args) throws IOException {
        int scale = -1;
        long edges = -1;
        long seed = 42;
        Path output = null;
        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--scale=")) {
                scale = Integer.parseInt(arg.substring("--scale=".length()));
            } else if (arg.startsWith("--edges=")) {
                edges = Long.parseLong(arg.substring("--edges=".length()));
            } else if (arg.startsWith("--seed=")) {
                seed = Long.parseLong(arg.substring("--seed=".length()));
            } else if (output == null) {
                output = Path.of(arg);
            } else {
                System.err.println("unexpected argument: " + arg);
                System.exit(2);
                return;
            }
        }
        if (output == null || scale < 1 || edges < 0) {
            System.err.println("Usage: leaderrank generate <out.csv> --scale=N --edges=M [--seed=S]");
            System.exit(2);
            return;
        }

        RmatGenerator generator = new RmatGenerator(scale, edges, seed);
        generator.writeCsv(output);
        System.out.println("wrote " + edges + " edges over " + generator.vertexCount()
                + " vertices to " + output);
    }

    private static void plan(String[] args) throws IOException {
        String input = null;
        long budget = DEFAULT_BIN_BUDGET;
        boolean distribute = false;
        int shardCount = -1;
        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--budget=")) {
                budget = Long.parseLong(arg.substring("--budget=".length()));
            } else if (arg.equals("--distribute")) {
                distribute = true;
            } else if (arg.startsWith("--shards=")) {
                shardCount = Integer.parseInt(arg.substring("--shards=".length()));
            } else if (input == null) {
                input = arg;
            } else {
                System.err.println("unexpected argument: " + arg);
                System.exit(2);
                return;
            }
        }
        if (input == null) {
            System.err.println("Usage: leaderrank plan <edges.csv> [--budget=EDGES_PER_BIN] [--distribute] [--shards=S]");
            System.exit(2);
            return;
        }

        EdgeSource source = new CsvEdgeSource(Path.of(input));
        if (shardCount > 0) {
            printShards(EdgeBinning.shards(source, shardCount));
            return;
        }
        if (!distribute) {
            printPlan(EdgeBinning.plan(source, budget), budget);
            return;
        }

        try (BinFiles files = EdgeBinning.bin(source, budget)) {
            printPlan(files.bins(), budget);
            System.out.println("distributed to bin files:");
            for (int i = 0; i < files.bins().size(); i++) {
                Bin bin = files.bins().get(i);
                System.out.printf(Locale.ROOT, "  bin %d: planned=%d written=%d%s%n",
                        i, bin.edgeCount(), files.recordCount(i),
                        bin.oversized() ? " OVERSIZED" : "");
            }
        }
    }

    private static void printPlan(List<Bin> bins, long budget) {
        long vertices = bins.isEmpty() ? 0 : bins.get(bins.size() - 1).end();
        long edges = bins.stream().mapToLong(Bin::edgeCount).sum();
        System.out.println("vertices: " + vertices);
        System.out.println("edges: " + edges);
        System.out.println("budget (edges/bin): " + budget);
        System.out.println("bins: " + bins.size());
        for (int i = 0; i < bins.size(); i++) {
            Bin bin = bins.get(i);
            System.out.printf(Locale.ROOT, "  bin %d: dests [%d, %d) edges=%d %s%n",
                    i, bin.begin(), bin.end(), bin.edgeCount(),
                    bin.oversized() ? "OVERSIZED" : "normal");
        }
    }

    private static void printShards(List<Shard> shards) {
        long vertices = shards.isEmpty() ? 0 : shards.get(shards.size() - 1).end();
        long total = shards.stream().mapToLong(Shard::edgeCount).sum();
        long max = shards.stream().mapToLong(Shard::edgeCount).max().orElse(0);
        long min = shards.stream().mapToLong(Shard::edgeCount).min().orElse(0);
        double mean = shards.isEmpty() ? 0.0 : (double) total / shards.size();
        System.out.println("vertices: " + vertices);
        System.out.println("edges: " + total);
        System.out.println("shards: " + shards.size());
        for (int i = 0; i < shards.size(); i++) {
            Shard shard = shards.get(i);
            System.out.printf(Locale.ROOT, "  shard %d: dests [%d, %d) edges=%d%n",
                    i, shard.begin(), shard.end(), shard.edgeCount());
        }
        System.out.printf(Locale.ROOT, "balance: min=%d mean=%.1f max=%d (max/mean=%.2f)%n",
                min, mean, max, mean > 0.0 ? max / mean : 0.0);
    }

    private static void printStats(Graph graph, LeaderRankResult result) {
        System.out.println("vertices: " + graph.vertexCount());
        System.out.println("edges: " + graph.edgeCount());
        System.out.println("iterations: " + result.iterations()
                + (result.converged() ? " (converged)" : " (max reached)"));
        double mib = 1024.0 * 1024.0;
        System.out.printf(Locale.ROOT, "heap ceiling (-Xmx): %.1f MiB%n",
                Runtime.getRuntime().maxMemory() / mib);
        long peakRss = Rss.peakResidentBytes();
        if (peakRss >= 0) {
            System.out.printf(Locale.ROOT, "peak RSS: %.1f MiB%n", peakRss / mib);
        }
    }

    private static double millisSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000.0;
    }

    private static void printRanks(Graph graph, double[] scores) {
        int[] order = IntStream.range(0, scores.length)
                .boxed()
                .sorted(Comparator.comparingDouble((Integer v) -> scores[v]).reversed()
                        .thenComparingInt(graph::originalId))
                .mapToInt(Integer::intValue)
                .toArray();
        System.out.println("ranks:");
        for (int vertex : order) {
            System.out.printf(Locale.ROOT, "  %d\t%.6f%n", graph.originalId(vertex), scores[vertex]);
        }
    }
}
