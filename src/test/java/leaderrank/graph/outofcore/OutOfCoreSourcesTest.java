package leaderrank.graph.outofcore;

import leaderrank.graph.outofcore.preprocessing.MemoryBudget;
import leaderrank.graph.outofcore.preprocessing.Preprocessor;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.PrimitiveIterator;
import java.util.Random;
import leaderrank.metric.LeaderRank;
import leaderrank.graph.Graph;
import leaderrank.graph.EdgeSource;
import leaderrank.graph.inmemory.InMemoryGraph;
import leaderrank.io.CsvEdgeSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OutOfCoreSourcesTest {

    private static EdgeSource csv(String text) {
        return new CsvEdgeSource(() -> new StringReader(text));
    }

    private static String randomCsv(long seed) {
        Random random = new Random(seed);
        int n = 3 + random.nextInt(20);
        int m = 1 + random.nextInt(5 * n);
        StringBuilder csv = new StringBuilder("from,to\n");
        for (int i = 0; i < m; i++) {
            csv.append(random.nextInt(n)).append(',').append(random.nextInt(n)).append('\n');
        }
        return csv.toString();
    }

    private static List<Integer> collect(PrimitiveIterator.OfInt iterator) {
        List<Integer> result = new ArrayList<>();
        while (iterator.hasNext()) {
            result.add(iterator.nextInt());
        }
        return result;
    }

    @Test
    void sourcesFileIsByteIdenticalAcrossBudgets(@TempDir Path dir) throws IOException {
        for (long seed = 1; seed <= 40; seed++) {
            EdgeSource source = csv(randomCsv(seed));
            Path whole = dir.resolve("whole-" + seed);
            Path tiny = dir.resolve("tiny-" + seed);
            Preprocessor.process(source, whole, 1L << 30);
            Preprocessor.process(source, tiny, 1);
            assertThat(Files.readAllBytes(tiny)).isEqualTo(Files.readAllBytes(whole));
        }
    }

    @Test
    void hyperNodeSortedExternallyMatchesInRam(@TempDir Path dir) throws IOException {
        StringBuilder text = new StringBuilder("from,to\n");
        for (int i = 1; i <= 200; i++) {
            text.append(i).append(",0\n");
        }
        EdgeSource source = csv(text.toString());

        Path external = dir.resolve("external");
        Path inRam = dir.resolve("inram");
        Preprocessor.process(source, external, 8);
        Preprocessor.process(source, inRam, 1L << 30);

        assertThat(Files.readAllBytes(external)).isEqualTo(Files.readAllBytes(inRam));
    }

    @Test
    void boundedBudgetSourcesAreByteIdenticalToUnbounded(@TempDir Path dir) throws IOException {
        for (long seed = 1; seed <= 20; seed++) {
            EdgeSource source = csv(randomCsv(seed));
            Path bounded = dir.resolve("bounded-" + seed);
            Path whole = dir.resolve("whole-" + seed);
            Preprocessor.process(source, bounded, new MemoryBudget(1 << 20));
            Preprocessor.process(source, whole, 1L << 30);
            assertThat(Files.readAllBytes(bounded)).isEqualTo(Files.readAllBytes(whole));
        }
    }

    @Test
    void boundedBudgetHyperNodeIsByteIdenticalToUnbounded(@TempDir Path dir) throws IOException {
        StringBuilder text = new StringBuilder("from,to\n");
        for (int i = 1; i <= 500; i++) {
            text.append(i).append(",0\n");
        }
        EdgeSource source = csv(text.toString());
        Path bounded = dir.resolve("bounded");
        Path whole = dir.resolve("whole");
        Preprocessor.process(source, bounded, new MemoryBudget(1 << 20));
        Preprocessor.process(source, whole, 1L << 30);
        assertThat(Files.readAllBytes(bounded)).isEqualTo(Files.readAllBytes(whole));
    }

    @Test
    void binnedSourcesMatchInMemoryInNeighbours() throws IOException {
        EdgeSource source = csv("from,to\n1,2\n1,3\n2,3\n3,1\n3,4\n4,3\n5,3\n6,3\n");
        Graph truth = InMemoryGraph.build(source);
        Graph outOfCore = OutOfCoreGraph.build(source, 2);
        for (int d = 0; d < truth.vertexCount(); d++) {
            assertThat(collect(outOfCore.sourcesOf(d))).isEqualTo(collect(truth.sourcesOf(d)));
        }
    }

    @Test
    void ranksMatchInMemoryWhenForcedIntoManyBins() throws IOException {
        for (long seed = 1; seed <= 30; seed++) {
            EdgeSource source = csv(randomCsv(seed));
            double[] inMemory = new LeaderRank().run(InMemoryGraph.build(source)).scores();
            double[] outOfCore = new LeaderRank().run(OutOfCoreGraph.build(source, 1)).scores();
            assertThat(outOfCore).containsExactly(inMemory);
        }
    }

    @Test
    void ranksMatchInMemoryUnderTightMemoryBudget() throws IOException {
        for (long seed = 1; seed <= 20; seed++) {
            EdgeSource source = csv(randomCsv(seed));
            double[] inMemory = new LeaderRank().run(InMemoryGraph.build(source)).scores();
            double[] outOfCore =
                    new LeaderRank().run(OutOfCoreGraph.build(source, new MemoryBudget(1 << 20))).scores();
            assertThat(outOfCore).containsExactly(inMemory);
        }
    }
}
