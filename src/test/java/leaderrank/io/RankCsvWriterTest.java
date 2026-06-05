package leaderrank.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.StringWriter;
import java.util.PrimitiveIterator;
import java.util.stream.IntStream;
import leaderrank.graph.Graph;
import org.junit.jupiter.api.Test;

class RankCsvWriterTest {

    private static Graph graphWithOriginalIds(int... originalIds) {
        return new Graph() {
            @Override
            public int vertexCount() {
                return originalIds.length;
            }

            @Override
            public int edgeCount() {
                return 0;
            }

            @Override
            public PrimitiveIterator.OfInt sourcesOf(int destinationDenseId) {
                return IntStream.empty().iterator();
            }

            @Override
            public int outDegree(int denseId) {
                return 0;
            }

            @Override
            public int originalId(int denseId) {
                return originalIds[denseId];
            }
        };
    }

    private String write(Graph graph, double[] scores) throws IOException {
        StringWriter out = new StringWriter();
        new RankCsvWriter().write(out, graph, scores);
        return out.toString();
    }

    @Test
    void writesRowsSortedByRankDescending() throws IOException {
        Graph graph = graphWithOriginalIds(50, 10, 30);
        double[] scores = {0.5, 0.25, 0.75};

        assertThat(write(graph, scores)).isEqualTo("vertex,rank\n30,0.75\n50,0.5\n10,0.25\n");
    }

    @Test
    void breaksRankTiesByOriginalIdAscending() throws IOException {
        Graph graph = graphWithOriginalIds(50, 10, 30);
        double[] scores = {0.5, 0.5, 0.5};

        assertThat(write(graph, scores)).isEqualTo("vertex,rank\n10,0.5\n30,0.5\n50,0.5\n");
    }

    @Test
    void valuesAreFullPrecision() throws IOException {
        Graph graph = graphWithOriginalIds(7, 9);
        double[] scores = {1.3333333333333333, 0.6666666666666666};

        assertThat(write(graph, scores))
                .isEqualTo("vertex,rank\n7,1.3333333333333333\n9,0.6666666666666666\n");
    }

    @Test
    void rejectsLengthMismatch() {
        Graph graph = graphWithOriginalIds(0, 1);

        assertThatThrownBy(() -> write(graph, new double[] {1.0}))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
