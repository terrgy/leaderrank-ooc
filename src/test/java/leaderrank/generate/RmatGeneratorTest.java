package leaderrank.generate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.PrimitiveIterator;
import leaderrank.graph.Graph;
import leaderrank.graph.inmemory.InMemoryGraph;
import leaderrank.io.CsvEdgeSource;
import org.junit.jupiter.api.Test;

class RmatGeneratorTest {

    private List<long[]> collect(RmatGenerator generator) throws IOException {
        List<long[]> edges = new ArrayList<>();
        generator.generate((from, to) -> edges.add(new long[] {from, to}));
        return edges;
    }

    @Test
    void sameSeedProducesIdenticalEdges() throws IOException {
        List<long[]> first = collect(new RmatGenerator(8, 300, 7L));
        List<long[]> second = collect(new RmatGenerator(8, 300, 7L));
        assertThat(first).hasSize(300);
        assertThat(second).hasSize(300);
        for (int i = 0; i < first.size(); i++) {
            assertThat(second.get(i)).containsExactly(first.get(i));
        }
    }

    @Test
    void differentSeedProducesDifferentEdges() throws IOException {
        List<long[]> first = collect(new RmatGenerator(8, 300, 7L));
        List<long[]> second = collect(new RmatGenerator(8, 300, 8L));
        boolean differs = false;
        for (int i = 0; i < first.size(); i++) {
            if (first.get(i)[0] != second.get(i)[0] || first.get(i)[1] != second.get(i)[1]) {
                differs = true;
                break;
            }
        }
        assertThat(differs).isTrue();
    }

    @Test
    void endpointsStayWithinVertexRange() throws IOException {
        RmatGenerator generator = new RmatGenerator(6, 500, 1L);
        int n = generator.vertexCount();
        for (long[] edge : collect(generator)) {
            assertThat(edge[0]).isBetween(0L, (long) n - 1);
            assertThat(edge[1]).isBetween(0L, (long) n - 1);
        }
    }

    @Test
    void toGraphReportsExpectedCounts() throws IOException {
        Graph graph = new RmatGenerator(8, 1000, 3L).toGraph(InMemoryGraph::build);
        assertThat(graph.edgeCount()).isEqualTo(1000);
        assertThat(graph.vertexCount()).isBetween(1, 256);
    }

    @Test
    void producesHyperNodes() throws IOException {
        int scale = 10;
        int m = 20000;
        Graph graph = new RmatGenerator(scale, m, 99L).toGraph(InMemoryGraph::build);
        int n = graph.vertexCount();
        int[] inDegrees = new int[n];
        for (int dest = 0; dest < n; dest++) {
            PrimitiveIterator.OfInt sources = graph.sourcesOf(dest);
            while (sources.hasNext()) {
                sources.nextInt();
                inDegrees[dest]++;
            }
        }
        int maxInDegree = 0;
        for (int degree : inDegrees) {
            maxInDegree = Math.max(maxInDegree, degree);
        }
        double meanInDegree = (double) m / n;
        assertThat(maxInDegree).isGreaterThan((int) (20 * meanInDegree));
    }

    @Test
    void writeCsvRoundTripsThroughReader() throws IOException {
        StringWriter out = new StringWriter();
        new RmatGenerator(7, 250, 5L).writeCsv(out);

        String csv = out.toString();
        assertThat(csv).startsWith("from,to\n");

        Graph graph = InMemoryGraph.build(new CsvEdgeSource(() -> new StringReader(csv)));
        assertThat(graph.edgeCount()).isEqualTo(250);
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThatThrownBy(() -> new RmatGenerator(0, 10, 1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RmatGenerator(31, 10, 1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RmatGenerator(8, -1, 1L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RmatGenerator(8, 10, 0.5, 0.2, 0.2, 0.2, 1L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
