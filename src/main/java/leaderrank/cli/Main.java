package leaderrank.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.stream.IntStream;
import leaderrank.core.LeaderRank;
import leaderrank.core.LeaderRankResult;
import leaderrank.graph.Graph;
import leaderrank.io.EdgeListReader;
import leaderrank.io.RankCsvWriter;

public final class Main {

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: leaderrank <edges.csv> [output.csv]");
            System.exit(2);
            return;
        }

        Graph graph = new EdgeListReader().read(Path.of(args[0]));
        LeaderRankResult result = new LeaderRank().run(graph);

        printStats(graph, result);
        if (args.length >= 2) {
            Path output = Path.of(args[1]);
            new RankCsvWriter().write(output, graph, result.scores());
            System.out.println("wrote ranks to " + output);
        } else {
            printRanks(graph, result.scores());
        }
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
