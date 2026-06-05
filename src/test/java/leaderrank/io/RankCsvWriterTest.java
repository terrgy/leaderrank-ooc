package leaderrank.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.StringWriter;
import leaderrank.graph.Graph;
import leaderrank.graph.InMemoryGraph;
import org.junit.jupiter.api.Test;

class RankCsvWriterTest {

    private String write(Graph graph, double[] scores) throws IOException {
        StringWriter out = new StringWriter();
        new RankCsvWriter().write(out, graph, scores);
        return out.toString();
    }

    @Test
    void writesHeaderAndRowsSortedByOriginalVertexId() throws IOException {
        Graph graph = new InMemoryGraph(3, new int[] {0, 1}, new int[] {1, 2}, new int[] {1, 1, 0},
                new int[] {50, 10, 30});
        double[] scores = {0.5, 0.25, 0.125};

        assertThat(write(graph, scores)).isEqualTo("vertex,rank\n10,0.25\n30,0.125\n50,0.5\n");
    }

    @Test
    void valuesRoundTripBackToScores() throws IOException {
        Graph graph = new InMemoryGraph(2, new int[] {0}, new int[] {1}, new int[] {1, 0}, new int[] {7, 9});
        double[] scores = {1.3333333333333333, 0.6666666666666666};

        String output = write(graph, scores);
        for (String line : output.lines().skip(1).toList()) {
            String[] cells = line.split(",");
            int originalId = Integer.parseInt(cells[0]);
            double rank = Double.parseDouble(cells[1]);
            int dense = originalId == 7 ? 0 : 1;
            assertThat(rank).isEqualTo(scores[dense]);
        }
    }

    @Test
    void rejectsLengthMismatch() {
        Graph graph = new InMemoryGraph(2, new int[] {0}, new int[] {1}, new int[] {1, 0}, new int[] {0, 1});

        assertThatThrownBy(() -> write(graph, new double[] {1.0}))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
