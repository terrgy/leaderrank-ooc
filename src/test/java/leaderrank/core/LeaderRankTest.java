package leaderrank.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.io.StringReader;
import leaderrank.graph.Graph;
import leaderrank.graph.inmemory.InMemoryGraph;
import leaderrank.io.CsvEdgeSource;
import org.junit.jupiter.api.Test;

class LeaderRankTest {

    private Graph read(String csv) throws IOException {
        return InMemoryGraph.build(new CsvEdgeSource(() -> new StringReader(csv)));
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
        LeaderRankResult result = new LeaderRank().run(g);
        assertThat(sum(result.scores())).isCloseTo(g.vertexCount(), within(1e-6));
    }

    @Test
    void allScoresArePositive() throws IOException {
        Graph g = read("from,to\n0,1\n1,2\n2,0\n3,0\n");
        for (double score : new LeaderRank().run(g).scores()) {
            assertThat(score).isPositive();
        }
    }

    @Test
    void starCenterRanksHighest() throws IOException {
        Graph g = read("from,to\n1,0\n2,0\n3,0\n4,0\n");
        int top = argmax(new LeaderRank().run(g).scores());
        assertThat(g.originalId(top)).isEqualTo(0);
    }

    @Test
    void directedTriangleIsSymmetric() throws IOException {
        Graph g = read("from,to\n0,1\n1,2\n2,0\n");
        double[] scores = new LeaderRank().run(g).scores();
        assertThat(scores[1]).isCloseTo(scores[0], within(1e-9));
        assertThat(scores[2]).isCloseTo(scores[0], within(1e-9));
    }

    @Test
    void handlesDanglingNode() throws IOException {
        Graph g = read("from,to\n0,1\n1,2\n");
        LeaderRankResult result = new LeaderRank().run(g);
        assertThat(result.converged()).isTrue();
        assertThat(sum(result.scores())).isCloseTo(g.vertexCount(), within(1e-6));
    }

    @Test
    void handlesSelfLoop() throws IOException {
        Graph g = read("from,to\n0,0\n0,1\n");
        LeaderRankResult result = new LeaderRank().run(g);
        for (double score : result.scores()) {
            assertThat(score).isPositive();
        }
        assertThat(sum(result.scores())).isCloseTo(g.vertexCount(), within(1e-6));
    }

    @Test
    void convergesWithinIterationBudget() throws IOException {
        Graph g = read("from,to\n0,1\n1,2\n2,0\n3,0\n");
        assertThat(new LeaderRank().run(g).converged()).isTrue();
    }

    @Test
    void isDeterministicAcrossRuns() throws IOException {
        Graph g = read("from,to\n0,1\n1,2\n2,0\n3,0\n");
        double[] first = new LeaderRank().run(g).scores();
        double[] second = new LeaderRank().run(g).scores();
        assertThat(second).containsExactly(first);
    }

    @Test
    void handlesEmptyGraph() throws IOException {
        Graph empty = read("from,to\n");
        LeaderRankResult result = new LeaderRank().run(empty);
        assertThat(result.scores()).isEmpty();
        assertThat(result.converged()).isTrue();
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThatThrownBy(() -> new LeaderRank(0.0, 100)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LeaderRank(1e-8, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
