package leaderrank.cli;

import java.nio.file.Path;
import java.util.concurrent.Callable;
import leaderrank.gen.RmatGenerator;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(name = "generate",
        mixinStandardHelpOptions = true,
        description = "Generate a deterministic R-MAT power-law graph (produces hyper-nodes).")
final class GenerateCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    @Parameters(index = "0", paramLabel = "<out.csv>",
            description = "output edge list (CSV header from,to)")
    private Path output;

    @Option(names = "--scale", required = true, paramLabel = "N",
            description = "vertex id space size is 2^N (1..30)")
    private int scale;

    @Option(names = "--edges", required = true, paramLabel = "M",
            description = "number of edges to generate")
    private long edges;

    @Option(names = "--seed", paramLabel = "S", description = "PRNG seed (default: 42)")
    private long seed = 42;

    @Override
    public Integer call() throws Exception {
        RmatGenerator generator;
        try {
            generator = new RmatGenerator(scale, edges, seed);
        } catch (IllegalArgumentException invalid) {
            throw new ParameterException(spec.commandLine(), invalid.getMessage());
        }
        generator.writeCsv(output);
        System.out.println("wrote " + edges + " edges over " + generator.vertexCount()
                + " vertices to " + output);
        return 0;
    }
}
