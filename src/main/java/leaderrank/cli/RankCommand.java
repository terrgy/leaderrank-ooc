package leaderrank.cli;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.stream.IntStream;
import leaderrank.metric.LeaderRankResult;
import leaderrank.metric.RankingEngine;
import leaderrank.graph.Graph;
import leaderrank.graph.EdgeSource;
import leaderrank.graph.outofcore.OutOfCoreGraph;
import leaderrank.io.CsvEdgeSource;
import leaderrank.io.RankCsvWriter;
import leaderrank.diagnostics.Rss;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(name = "rank",
        mixinStandardHelpOptions = true,
        description = "Compute LeaderRank for every vertex and write or print the ranking.")
final class RankCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    @Mixin
    private final EngineOptions engineOptions = new EngineOptions();

    @Parameters(index = "0", paramLabel = "<edges.csv>",
            description = "input edge list (CSV header from,to)")
    private Path input;

    @Parameters(index = "1", arity = "0..1", paramLabel = "<output.csv>",
            description = "optional output CSV (vertex,rank); when set, the full console listing is "
                    + "suppressed unless --print-top is given")
    private Path output;

    @Option(names = "--repeat", paramLabel = "N",
            description = "run the engine N times for JIT warm-up; the last run is reported (default: 1)")
    private int repeat = 1;

    @Option(names = "--print-top", paramLabel = "N",
            description = "print only the top N leaders to the console")
    private Integer printTop;

    @Override
    public Integer call() throws Exception {
        engineOptions.validate(spec);
        if (repeat < 1) {
            throw new ParameterException(spec.commandLine(), "--repeat must be at least 1");
        }
        if (printTop != null && printTop < 0) {
            throw new ParameterException(spec.commandLine(), "--print-top must not be negative");
        }

        EdgeSource source = new CsvEdgeSource(input);

        long buildStart = System.nanoTime();
        Graph graph = engineOptions.buildGraph(source);
        double buildMillis = millisSince(buildStart);

        RankingEngine engine = engineOptions.engine();
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

        if (engineOptions.usesThreads()) {
            System.out.println("threads: " + engineOptions.threads());
        }
        System.out.printf(Locale.ROOT, "preprocess: %.1f ms%n", buildMillis);
        System.out.printf(Locale.ROOT, "compute: %.1f ms%n", computeMillis);
        printStats(graph, result);

        if (output != null) {
            new RankCsvWriter().write(output, graph, result.scores());
            System.out.println("wrote ranks to " + output);
        }
        if (printTop != null) {
            printRanks(graph, result.scores(), printTop);
        } else if (output == null) {
            printRanks(graph, result.scores(), -1);
        }
        return 0;
    }

    private static void printStats(Graph graph, LeaderRankResult result) {
        System.out.println("vertices: " + graph.vertexCount());
        System.out.println("edges: " + graph.edgeCount());
        if (graph instanceof OutOfCoreGraph ooc) {
            System.out.println("bins: " + ooc.binCount() + " (<= " + ooc.maxEdgesPerBin()
                    + " edges/bin, " + ooc.distributionWaves() + " distribution pass(es))");
        }
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

    private static void printRanks(Graph graph, double[] scores, int limit) {
        int[] order = IntStream.range(0, scores.length)
                .boxed()
                .sorted(Comparator.comparingDouble((Integer v) -> scores[v]).reversed()
                        .thenComparingInt(graph::originalId))
                .mapToInt(Integer::intValue)
                .toArray();
        int count = limit < 0 ? order.length : Math.min(limit, order.length);
        System.out.println(limit < 0 ? "ranks:" : "top " + count + " leaders:");
        for (int i = 0; i < count; i++) {
            int vertex = order[i];
            System.out.printf(Locale.ROOT, "  %d\t%.6f%n", graph.originalId(vertex), scores[vertex]);
        }
    }

    private static double millisSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000.0;
    }
}
