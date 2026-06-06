package leaderrank.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import picocli.CommandLine;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliTest {

    @TempDir
    static Path dir;

    private static Path graph;

    @BeforeAll
    static void prepare() throws Exception {
        graph = dir.resolve("triangle.csv");
        Files.writeString(graph, "from,to\n0,1\n1,2\n2,0\n");
    }

    private record Result(int code, String out, String err) {
    }

    private static Result run(String... args) {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream outBuffer = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuffer = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(outBuffer, true, StandardCharsets.UTF_8);
        PrintStream err = new PrintStream(errBuffer, true, StandardCharsets.UTF_8);
        System.setOut(out);
        System.setErr(err);
        int code;
        try {
            CommandLine commandLine = Main.commandLine();
            commandLine.setOut(new PrintWriter(out, true));
            commandLine.setErr(new PrintWriter(err, true));
            code = commandLine.execute(args);
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
        return new Result(code, outBuffer.toString(StandardCharsets.UTF_8), errBuffer.toString(StandardCharsets.UTF_8));
    }

    @Test
    void topLevelHelpListsSubcommands() {
        Result result = run("--help");
        assertThat(result.code).isZero();
        assertThat(result.out).contains("rank", "verify", "generate");
    }

    @Test
    void rankHelpListsEngineAndGraphChoices() {
        Result result = run("rank", "--help");
        assertThat(result.code).isZero();
        assertThat(result.out)
                .contains("--engine", "--graph", "--threads", "--print-top")
                .contains("dense", "common", "parallel")
                .contains("in-memory", "out-of-core");
    }

    @Test
    void threadsRejectedWhenNeitherParallelNorOutOfCore() {
        Result result = run("rank", graph.toString(), "--engine=common", "--graph=in-memory", "--threads=2");
        assertThat(result.code).isNotZero();
        assertThat(result.err).contains("--threads");
    }

    @Test
    void threadsAcceptedWithParallelEngine() {
        Result result = run("rank", graph.toString(), "--engine=parallel", "--graph=in-memory", "--threads=2");
        assertThat(result.code).isZero();
    }

    @Test
    void invalidEngineIsRejected() {
        Result result = run("rank", graph.toString(), "--engine=bogus", "--graph=in-memory");
        assertThat(result.code).isNotZero();
        assertThat(result.err).contains("bogus");
    }

    @Test
    void fullListingPrintedWhenNoOutputAndNoTop() {
        Result result = run("rank", graph.toString(), "--engine=common", "--graph=in-memory");
        assertThat(result.code).isZero();
        assertThat(result.out).contains("ranks:").doesNotContain("top ");
    }

    @Test
    void printTopLimitsConsoleListing() {
        Result result = run("rank", graph.toString(), "--engine=common", "--graph=in-memory", "--print-top=1");
        assertThat(result.code).isZero();
        assertThat(result.out).contains("top 1 leaders:").doesNotContain("ranks:");
    }

    @Test
    void outputPathSuppressesConsoleListing() throws Exception {
        Path out = dir.resolve("ranks.csv");
        Result result = run("rank", graph.toString(), out.toString(), "--engine=common", "--graph=in-memory");
        assertThat(result.code).isZero();
        assertThat(result.out).contains("wrote ranks to").doesNotContain("ranks:");
        assertThat(Files.readString(out)).startsWith("vertex,rank");
    }

    @Test
    void generateRequiresScaleAndEdges() {
        Result result = run("generate", dir.resolve("g.csv").toString());
        assertThat(result.code).isNotZero();
        assertThat(result.err).containsAnyOf("--scale", "--edges", "Missing");
    }
}
