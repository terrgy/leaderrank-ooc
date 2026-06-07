package leaderrank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import leaderrank.generate.RmatGenerator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("integration")
class MemoryCapEnforcementTest {

    private static final String CAP = "200M";
    private static final double CAP_MIB = 200.0;
    private static final String HEAP = "-Xmx48m";
    private static final Pattern PEAK_RSS = Pattern.compile("peak RSS:\\s*([0-9.]+)\\s*MiB");

    @TempDir
    static Path sharedDir;

    private static Path graph;

    @BeforeAll
    static void prepare() throws IOException {
        assumeTrue(kernelMemoryCapAvailable(),
                "systemd-run --user with MemoryMax is unavailable; skipping kernel memory-cap test");
        graph = sharedDir.resolve("memcap.csv");
        new RmatGenerator(16, 3_000_000, 7).writeCsv(graph);
    }

    @Test
    void streamingEngineCompletesUnderKernelEnforcedCap() throws Exception {
        ProcessOutcome outcome = runUnderCap(false);
        assertThat(outcome.exitCode).isZero();
        double peakRss = parsePeakRssMib(outcome.text);
        assertThat(peakRss).isPositive().isLessThan(CAP_MIB);
    }

    @Test
    void naiveInMemoryEngineFailsUnderTheSameCap() throws Exception {
        ProcessOutcome outcome = runUnderCap(true);
        assertThat(outcome.exitCode).isNotZero();
        assertThat(outcome.text).containsAnyOf("OutOfMemoryError", "Killed");
    }

    private static ProcessOutcome runUnderCap(boolean inMemory) throws Exception {
        List<String> command = new ArrayList<>(List.of(
                "systemd-run", "--user", "--scope", "--quiet",
                "-p", "MemoryMax=" + CAP, "-p", "MemorySwapMax=0",
                javaBinary(), HEAP, "-XX:+UseSerialGC",
                "-cp", System.getProperty("java.class.path"),
                "leaderrank.cli.Main", "rank", graph.toString(), "/dev/null", "--threads=2"));
        if (inMemory) {
            command.add("--graph=in-memory");
        }
        return execute(command);
    }

    private static double parsePeakRssMib(String text) {
        Matcher matcher = PEAK_RSS.matcher(text);
        assertThat(matcher.find()).as("peak RSS line present in output").isTrue();
        return Double.parseDouble(matcher.group(1));
    }

    private static boolean kernelMemoryCapAvailable() {
        try {
            ProcessOutcome probe = execute(List.of(
                    "systemd-run", "--user", "--scope", "--quiet",
                    "-p", "MemoryMax=64M", "-p", "MemorySwapMax=0", "true"));
            return probe.exitCode == 0;
        } catch (Exception unavailable) {
            return false;
        }
    }

    private static ProcessOutcome execute(List<String> command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String text;
        try (InputStream stream = process.getInputStream()) {
            text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        int exitCode = process.waitFor();
        return new ProcessOutcome(exitCode, text);
    }

    private static String javaBinary() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    private record ProcessOutcome(int exitCode, String text) {
    }
}
