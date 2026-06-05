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
import leaderrank.core.RankingEngine;
import leaderrank.gen.RmatGenerator;
import leaderrank.graph.Graph;
import leaderrank.io.EdgeListReader;
import leaderrank.io.RankCsvWriter;

public final class Main {

    public static void main(String[] args) throws IOException {
        if (args.length >= 1 && args[0].equals("verify")) {
            verify(args);
        } else if (args.length >= 1 && args[0].equals("generate")) {
            generate(args);
        } else {
            rank(args);
        }
    }

    private static void rank(String[] args) throws IOException {
        List<String> positionals = new ArrayList<>();
        boolean dense = false;
        for (String arg : args) {
            if (arg.equals("--dense")) {
                dense = true;
            } else {
                positionals.add(arg);
            }
        }
        if (positionals.isEmpty()) {
            System.err.println("Usage: leaderrank <edges.csv> [output.csv] [--dense]");
            System.err.println("       leaderrank verify <edges.csv>");
            System.err.println("       leaderrank generate <out.csv> --scale=N --edges=M [--seed=S]");
            System.exit(2);
            return;
        }

        Graph graph = new EdgeListReader().read(Path.of(positionals.get(0)));
        RankingEngine engine = dense ? new DenseLeaderRank() : new LeaderRank();
        LeaderRankResult result = engine.run(graph);

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

        Graph graph = new EdgeListReader().read(Path.of(args[1]));
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

    private static void printStats(Graph graph, LeaderRankResult result) {
        System.out.println("vertices: " + graph.vertexCount());
        System.out.println("edges: " + graph.edgeCount());
        System.out.println("iterations: " + result.iterations()
                + (result.converged() ? " (converged)" : " (max reached)"));
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
