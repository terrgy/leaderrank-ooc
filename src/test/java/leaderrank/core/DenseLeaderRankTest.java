package leaderrank.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.io.StringReader;
import leaderrank.graph.Graph;
import leaderrank.graph.InMemoryGraph;
import leaderrank.io.EdgeListReader;
import org.junit.jupiter.api.Test;

class DenseLeaderRankTest {

    private Graph read(String csv) throws IOException {
        return new EdgeListReader().read(new StringReader(csv));
    }

    private static int argmax(double[] values) {
        int best = 0;
        for (int i = 1; i < values.length; i++) {
            if (values[i] > values[best]) {
                best = i;
            }
        }
        return best;
    }

    private static double sum(double[] values) {
        double total = 0.0;
        for (double value : values) {
            total += value;
        }
        return total;
    }

    @Test
    void conservesTotalMass() throws IOException {
        Graph g = read("from,to\n0,1\n1,2\n2,0\n3,0\n");
        LeaderRankResult result = new DenseLeaderRank().run(g);
        assertThat(sum(result.scores())).isCloseTo(g.vertexCount(), within(1e-6));
    }

    @Test
    void allScoresArePositive() throws IOException {
        Graph g = read("from,to\n0,1\n1,2\n2,0\n3,0\n");
        for (double score : new DenseLeaderRank().run(g).scores()) {
            assertThat(score).isPositive();
        }
    }

    @Test
    void starCenterRanksHighest() throws IOException {
        Graph g = read("from,to\n1,0\n2,0\n3,0\n4,0\n");
        int top = argmax(new DenseLeaderRank().run(g).scores());
        assertThat(g.originalId(top)).isEqualTo(0);
    }

    @Test
    void handlesEmptyGraph() {
        Graph empty = new InMemoryGraph(0, new int[0], new int[0], new int[0], new int[0]);
        LeaderRankResult result = new DenseLeaderRank().run(empty);
        assertThat(result.scores()).isEmpty();
        assertThat(result.converged()).isTrue();
    }
}
