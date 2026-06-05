package leaderrank.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.PrimitiveIterator;
import leaderrank.graph.Graph;
import leaderrank.graph.inmemory.InMemoryGraph;
import org.junit.jupiter.api.Test;

class CsvEdgeSourceTest {

    private Graph read(String csv) throws IOException {
        return InMemoryGraph.build(new CsvEdgeSource(() -> new StringReader(csv)));
    }

    private static List<Integer> inNeighbors(Graph graph, int destinationDenseId) throws IOException {
        List<Integer> result = new ArrayList<>();
        PrimitiveIterator.OfInt sources = graph.sourcesOf(destinationDenseId);
        while (sources.hasNext()) {
            result.add(sources.nextInt());
        }
        return result;
    }

    @Test
    void readsSimpleChain() throws IOException {
        Graph g = read("from,to\n0,1\n1,2\n");

        assertThat(g.vertexCount()).isEqualTo(3);
        assertThat(g.edgeCount()).isEqualTo(2);
        assertThat(inNeighbors(g, 1)).containsExactly(0);
        assertThat(inNeighbors(g, 2)).containsExactly(1);
        assertThat(g.outDegree(0)).isEqualTo(1);
        assertThat(g.outDegree(1)).isEqualTo(1);
        assertThat(g.outDegree(2)).isEqualTo(0);
    }

    @Test
    void remapsSparseIdsByFirstAppearance() throws IOException {
        Graph g = read("from,to\n10,20\n20,30\n");

        assertThat(g.vertexCount()).isEqualTo(3);
        assertThat(g.originalId(0)).isEqualTo(10);
        assertThat(g.originalId(1)).isEqualTo(20);
        assertThat(g.originalId(2)).isEqualTo(30);
        assertThat(inNeighbors(g, 1)).containsExactly(0);
        assertThat(inNeighbors(g, 2)).containsExactly(1);
    }

    @Test
    void ignoresExtraColumnsAndTrimsWhitespace() throws IOException {
        Graph g = read("from,to,weight\n  0 , 1 , 9\n");

        assertThat(g.edgeCount()).isEqualTo(1);
        assertThat(g.vertexCount()).isEqualTo(2);
        assertThat(inNeighbors(g, 1)).containsExactly(0);
    }

    @Test
    void keepsSelfLoops() throws IOException {
        Graph g = read("from,to\n5,5\n");

        assertThat(g.vertexCount()).isEqualTo(1);
        assertThat(g.edgeCount()).isEqualTo(1);
        assertThat(g.outDegree(0)).isEqualTo(1);
    }

    @Test
    void keepsDuplicateEdgesAsMultiEdges() throws IOException {
        Graph g = read("from,to\n0,1\n0,1\n");

        assertThat(g.edgeCount()).isEqualTo(2);
        assertThat(g.outDegree(0)).isEqualTo(2);
    }

    @Test
    void destinationOnlyVertexHasZeroOutDegree() throws IOException {
        Graph g = read("from,to\n0,1\n");

        assertThat(g.vertexCount()).isEqualTo(2);
        assertThat(g.outDegree(1)).isEqualTo(0);
    }

    @Test
    void skipsBlankLines() throws IOException {
        Graph g = read("from,to\n0,1\n\n1,2\n");

        assertThat(g.edgeCount()).isEqualTo(2);
    }

    @Test
    void handlesHeaderOnlyFile() throws IOException {
        Graph g = read("from,to\n");

        assertThat(g.vertexCount()).isZero();
        assertThat(g.edgeCount()).isZero();
    }

    @Test
    void handlesNegativeIds() throws IOException {
        Graph g = read("from,to\n-1,-2\n");

        assertThat(g.vertexCount()).isEqualTo(2);
        assertThat(g.originalId(0)).isEqualTo(-1);
        assertThat(g.originalId(1)).isEqualTo(-2);
    }

    @Test
    void malformedNumberThrowsWithRowNumber() {
        assertThatThrownBy(() -> read("from,to\n0,1\n2,x\n"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("row 2");
    }

    @Test
    void missingSecondColumnThrowsWithRowNumber() {
        assertThatThrownBy(() -> read("from,to\n5\n"))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("row 1");
    }
}
