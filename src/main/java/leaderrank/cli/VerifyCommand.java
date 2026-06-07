package leaderrank.cli;

import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.Callable;
import leaderrank.metric.LeaderRankResult;
import leaderrank.metric.RankingEngine;
import leaderrank.graph.Graph;
import leaderrank.graph.EdgeSource;
import leaderrank.io.CsvEdgeSource;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(name = "verify",
        mixinStandardHelpOptions = true,
        description = "Cross-check a fast engine against the dense O(n^2) oracle on the same graph.")
final class VerifyCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    @Mixin
    private final EngineOptions engineOptions = new EngineOptions();

    @Parameters(index = "0", paramLabel = "<edges.csv>",
            description = "input edge list (CSV header from,to)")
    private Path input;

    @Override
    public Integer call() throws Exception {
        engineOptions.validate(spec);
        EdgeSource source = new CsvEdgeSource(input);
        Graph graph = engineOptions.buildGraph(source);

        RankingEngine fastEngine = engineOptions.engine();
        LeaderRankResult fast = fastEngine.run(graph);
        LeaderRankResult truth = engineOptions.truthEngine().run(graph);

        double[] a = fast.scores();
        double[] b = truth.scores();
        double maxDiff = 0.0;
        for (int v = 0; v < a.length; v++) {
            maxDiff = Math.max(maxDiff, Math.abs(a[v] - b[v]));
        }

        System.out.println("vertices: " + graph.vertexCount());
        System.out.println("edges: " + graph.edgeCount());
        System.out.println("fast engine: " + engineOptions.engineChoice());
        System.out.println("fast iterations: " + fast.iterations()
                + (fast.converged() ? " (converged)" : " (max reached)"));
        System.out.println("dense iterations: " + truth.iterations()
                + (truth.converged() ? " (converged)" : " (max reached)"));
        System.out.printf(Locale.ROOT, "max abs difference: %.3e%n", maxDiff);
        return 0;
    }
}
