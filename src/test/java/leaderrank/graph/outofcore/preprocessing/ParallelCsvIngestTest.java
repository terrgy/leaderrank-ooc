package leaderrank.graph.outofcore.preprocessing;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import leaderrank.metric.LeaderRank;
import leaderrank.generate.RmatGenerator;
import leaderrank.graph.EdgeSource;
import leaderrank.graph.inmemory.InMemoryGraph;
import leaderrank.io.CsvEdgeSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ParallelCsvIngestTest {

    private static final MemoryBudget BUDGET = new MemoryBudget(256L << 20);

    private static byte[] sources(EdgeSource source, Path out, MemoryBudget budget, int parallelism)
            throws IOException {
        Preprocessor.process(source, out, budget, parallelism);
        return Files.readAllBytes(out);
    }

    @Test
    void parallelParseIsByteIdenticalToSequential(@TempDir Path dir) throws IOException {
        for (long seed = 1; seed <= 10; seed++) {
            Path csv = dir.resolve("g-" + seed + ".csv");
            new RmatGenerator(12, 40_000, seed).writeCsv(csv);
            EdgeSource fileSource = new CsvEdgeSource(csv);

            byte[] sequential = sources(fileSource, dir.resolve("seq-" + seed), BUDGET, 1);
            for (int parallelism : new int[] {2, 4, 8, 16}) {
                byte[] parallel = sources(fileSource, dir.resolve("par-" + seed + "-" + parallelism), BUDGET, parallelism);
                assertThat(parallel).as("seed %d, parallelism %d", seed, parallelism).isEqualTo(sequential);
            }
        }
    }

    @Test
    void parallelParseMatchesCommonsCsvParser(@TempDir Path dir) throws IOException {
        String text = "from,to,weight\n1,2,5\n3,4,9\n\n5,6,1\n2, 3 ,7";
        Path csv = dir.resolve("edge.csv");
        Files.writeString(csv, text);

        byte[] oracle = sources(new CsvEdgeSource(() -> new StringReader(text)), dir.resolve("oracle"), BUDGET, 1);
        EdgeSource fileSource = new CsvEdgeSource(csv);
        for (int parallelism : new int[] {1, 2, 4, 8}) {
            byte[] parsed = sources(fileSource, dir.resolve("p" + parallelism), BUDGET, parallelism);
            assertThat(parsed).as("parallelism %d", parallelism).isEqualTo(oracle);
        }
    }

    @Test
    void parallelParseHandlesFileSmallerThanThreadCount(@TempDir Path dir) throws IOException {
        Path csv = dir.resolve("tiny.csv");
        Files.writeString(csv, "from,to\n0,1\n1,2\n");
        EdgeSource fileSource = new CsvEdgeSource(csv);

        byte[] sequential = sources(fileSource, dir.resolve("seq"), BUDGET, 1);
        byte[] parallel = sources(fileSource, dir.resolve("par"), BUDGET, 16);
        assertThat(parallel).isEqualTo(sequential);
    }

    @Test
    void parallelParseHandlesHyperNodeUnderTightBudget(@TempDir Path dir) throws IOException {
        StringBuilder text = new StringBuilder("from,to\n");
        for (int i = 1; i <= 500; i++) {
            text.append(i).append(",0\n");
        }
        Path csv = dir.resolve("hub.csv");
        Files.writeString(csv, text.toString());
        EdgeSource fileSource = new CsvEdgeSource(csv);

        byte[] sequential = sources(fileSource, dir.resolve("seq"), BUDGET, 1);
        byte[] parallel = sources(fileSource, dir.resolve("par"), new MemoryBudget(1 << 20), 8);
        assertThat(parallel).isEqualTo(sequential);
    }

    @Test
    void parallelParseRanksMatchInMemory(@TempDir Path dir) throws IOException {
        for (long seed = 1; seed <= 5; seed++) {
            Path csv = dir.resolve("r-" + seed + ".csv");
            new RmatGenerator(11, 20_000, seed).writeCsv(csv);
            EdgeSource fileSource = new CsvEdgeSource(csv);

            double[] inMemory = new LeaderRank().run(InMemoryGraph.build(fileSource)).scores();
            double[] outOfCore = new LeaderRank().run(
                    leaderrank.graph.outofcore.OutOfCoreGraph.build(fileSource, 8)).scores();
            assertThat(outOfCore).containsExactly(inMemory);
        }
    }
}
