package leaderrank.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.io.StringReader;
import java.util.Random;
import leaderrank.gen.RmatGenerator;
import leaderrank.graph.Graph;
import leaderrank.io.EdgeListReader;
import org.junit.jupiter.api.Test;

class LeaderRankCrossCheckTest {

    private static final double TOLERANCE = 1e-6;

    private Graph randomGraph(long seed) throws IOException {
        Random random = new Random(seed);
        int n = 2 + random.nextInt(15);
        int m = 1 + random.nextInt(3 * n);
        StringBuilder csv = new StringBuilder("from,to\n");
        for (int i = 0; i < m; i++) {
            csv.append(random.nextInt(n)).append(',').append(random.nextInt(n)).append('\n');
        }
        return new EdgeListReader().read(new StringReader(csv.toString()));
    }

    private void assertEnginesAgree(Graph graph) {
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
            assertEnginesAgree(randomGraph(seed));
        }
    }

    @Test
    void matchesDenseOracleOnRmatGraph() {
        assertEnginesAgree(new RmatGenerator(7, 400, 12345L).toGraph());
    }

    @Test
    void matchesDenseOracleOnExampleGraph() throws IOException {
        Graph g = new EdgeListReader().read(
                new StringReader("from,to\n1,2\n1,3\n2,3\n3,1\n3,4\n4,3\n5,3\n6,3\n"));
        assertEnginesAgree(g);
    }
}
