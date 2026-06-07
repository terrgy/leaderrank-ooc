package leaderrank.metric;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.StringReader;
import java.util.Random;
import leaderrank.generate.RmatGenerator;
import leaderrank.graph.Graph;
import leaderrank.graph.GraphFactory;
import leaderrank.graph.inmemory.InMemoryGraph;
import leaderrank.graph.outofcore.OutOfCoreGraph;
import leaderrank.io.CsvEdgeSource;
import org.junit.jupiter.api.Test;

class ParallelLeaderRankTest {

    private static String randomCsv(long seed) {
        Random random = new Random(seed);
        int n = 3 + random.nextInt(25);
        int m = 1 + random.nextInt(6 * n);
        StringBuilder csv = new StringBuilder("from,to\n");
        for (int i = 0; i < m; i++) {
            csv.append(random.nextInt(n)).append(',').append(random.nextInt(n)).append('\n');
        }
        return csv.toString();
    }

    private static Graph graph(String csv, GraphFactory factory) throws IOException {
        return factory.create(new CsvEdgeSource(() -> new StringReader(csv)));
    }

    @Test
    void matchesSingleThreadedAcrossThreadCounts() throws IOException {
        for (long seed = 1; seed <= 30; seed++) {
            Graph graph = graph(randomCsv(seed), OutOfCoreGraph::build);
            double[] single = new LeaderRank().run(graph).scores();
            for (int threads : new int[] {1, 2, 4, 8}) {
                double[] parallel = new ParallelLeaderRank(threads).run(graph).scores();
                assertThat(parallel).containsExactly(single);
            }
        }
    }

    @Test
    void inMemoryMatchesSingleThreaded() throws IOException {
        for (long seed = 1; seed <= 20; seed++) {
            Graph graph = graph(randomCsv(seed), InMemoryGraph::build);
            double[] single = new LeaderRank().run(graph).scores();
            double[] parallel = new ParallelLeaderRank(8).run(graph).scores();
            assertThat(parallel).containsExactly(single);
        }
    }

    @Test
    void matchesSingleThreadedOnHyperNodeGraph() throws IOException {
        Graph graph = new RmatGenerator(10, 20000, 1L).toGraph(OutOfCoreGraph::build);
        double[] single = new LeaderRank().run(graph).scores();
        double[] parallel = new ParallelLeaderRank(8).run(graph).scores();
        assertThat(parallel).containsExactly(single);
    }

    @Test
    void isDeterministicAcrossRuns() throws IOException {
        Graph graph = graph(randomCsv(42), OutOfCoreGraph::build);
        double[] first = new ParallelLeaderRank(8).run(graph).scores();
        double[] second = new ParallelLeaderRank(8).run(graph).scores();
        assertThat(second).containsExactly(first);
    }

    @Test
    void rejectsInvalidThreadCount() {
        assertThatThrownBy(() -> new ParallelLeaderRank(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
