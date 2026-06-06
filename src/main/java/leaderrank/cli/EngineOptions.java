package leaderrank.cli;

import java.io.IOException;
import leaderrank.core.DenseLeaderRank;
import leaderrank.core.LeaderRank;
import leaderrank.core.ParallelLeaderRank;
import leaderrank.core.RankingEngine;
import leaderrank.graph.Graph;
import leaderrank.graph.edge.EdgeSource;
import leaderrank.graph.inmemory.InMemoryGraph;
import leaderrank.graph.outofcore.OutOfCoreGraph;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;

final class EngineOptions {

    @Option(names = "--engine",
            converter = EngineChoice.Converter.class,
            completionCandidates = EngineChoice.Candidates.class,
            paramLabel = "<engine>",
            description = "ranking engine: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE})")
    private EngineChoice engine = EngineChoice.PARALLEL;

    @Option(names = "--graph",
            converter = GraphChoice.Converter.class,
            completionCandidates = GraphChoice.Candidates.class,
            paramLabel = "<graph>",
            description = "graph representation: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE})")
    private GraphChoice graph = GraphChoice.OUT_OF_CORE;

    @Option(names = "--threads", paramLabel = "P",
            description = "worker threads; valid only with --engine=parallel or --graph=out-of-core "
                    + "(default: all CPU cores)")
    private int threads = Runtime.getRuntime().availableProcessors();

    @Option(names = "--max-iterations", paramLabel = "N",
            description = "power-method iteration cap (default: ${DEFAULT-VALUE})")
    private int maxIterations = ParallelLeaderRank.DEFAULT_MAX_ITERATIONS;

    void validate(CommandSpec spec) {
        boolean explicitThreads = spec.commandLine().getParseResult().hasMatchedOption("--threads");
        if (explicitThreads && engine != EngineChoice.PARALLEL && graph != GraphChoice.OUT_OF_CORE) {
            throw new ParameterException(spec.commandLine(),
                    "--threads is valid only with --engine=parallel or --graph=out-of-core");
        }
        if (threads < 1) {
            throw new ParameterException(spec.commandLine(), "--threads must be at least 1");
        }
        if (maxIterations < 1) {
            throw new ParameterException(spec.commandLine(), "--max-iterations must be at least 1");
        }
    }

    boolean usesThreads() {
        return engine == EngineChoice.PARALLEL || graph == GraphChoice.OUT_OF_CORE;
    }

    int threads() {
        return threads;
    }

    EngineChoice engineChoice() {
        return engine;
    }

    Graph buildGraph(EdgeSource source) throws IOException {
        if (graph == GraphChoice.IN_MEMORY) {
            return InMemoryGraph.build(source);
        }
        return OutOfCoreGraph.build(source, threads);
    }

    RankingEngine engine() {
        return switch (engine) {
            case DENSE -> new DenseLeaderRank(DenseLeaderRank.DEFAULT_TOLERANCE, maxIterations);
            case COMMON -> new LeaderRank(LeaderRank.DEFAULT_TOLERANCE, maxIterations);
            case PARALLEL -> new ParallelLeaderRank(threads, ParallelLeaderRank.DEFAULT_TOLERANCE, maxIterations);
        };
    }

    RankingEngine truthEngine() {
        return new DenseLeaderRank(DenseLeaderRank.DEFAULT_TOLERANCE, maxIterations);
    }
}
