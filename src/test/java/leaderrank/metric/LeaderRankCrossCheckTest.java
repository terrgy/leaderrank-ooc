package leaderrank.metric;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

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

class LeaderRankCrossCheckTest {

    private static final double TOLERANCE = 1e-6;

    private String randomCsv(long seed) {
        Random random = new Random(seed);
        int n = 2 + random.nextInt(15);
        int m = 1 + random.nextInt(3 * n);
        StringBuilder csv = new StringBuilder("from,to\n");
        for (int i = 0; i < m; i++) {
            csv.append(random.nextInt(n)).append(',').append(random.nextInt(n)).append('\n');
        }
        return csv.toString();
    }

    private Graph graph(String csv, GraphFactory factory) throws IOException {
        return factory.create(new CsvEdgeSource(() -> new StringReader(csv)));
    }

    private void assertEnginesAgree(Graph graph) throws IOException{
        double[] streaming = new LeaderRank().run(graph).scores();
        double[] truth = new DenseLeaderRank().run(graph).scores();
        assertThat(streaming).hasSameSizeAs(truth);
        for (int v = 0; v < streaming.length; v++) {
            assertThat(streaming[v]).isCloseTo(truth[v], within(TOLERANCE));
        }
    }

    @Test
    void matchesDenseOracleOnRandomGraphs() throws IOException {
        for (long seed = 1; seed <= 200; seed++) {
            assertEnginesAgree(graph(randomCsv(seed), InMemoryGraph::build));
        }
    }

    @Test
    void matchesDenseOracleOnRmatGraph() throws IOException {
        assertEnginesAgree(new RmatGenerator(7, 400, 12345L).toGraph(InMemoryGraph::build));
    }

    @Test
    void matchesDenseOracleOnExampleGraph() throws IOException {
        assertEnginesAgree(
                graph("from,to\n1,2\n1,3\n2,3\n3,1\n3,4\n4,3\n5,3\n6,3\n", InMemoryGraph::build));
    }

    @Test
    void inMemoryAndOutOfCoreProduceIdenticalRanks() throws IOException {
        for (long seed = 1; seed <= 50; seed++) {
            String csv = randomCsv(seed);
            double[] inMemory = new LeaderRank().run(graph(csv, InMemoryGraph::build)).scores();
            double[] outOfCore = new LeaderRank().run(graph(csv, OutOfCoreGraph::build)).scores();
            assertThat(outOfCore).containsExactly(inMemory);
        }
    }
}
