package leaderrank.graph.outofcore;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import leaderrank.graph.Graph;
import leaderrank.graph.edge.EdgeSource;
import leaderrank.graph.inmemory.InMemoryGraph;
import leaderrank.io.CsvEdgeSource;
import org.junit.jupiter.api.Test;

class BinFilesTest {

    private static EdgeSource csv(String text) {
        return new CsvEdgeSource(() -> new StringReader(text));
    }

    private static int[] readInts(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int[] values = new int[bytes.length / Integer.BYTES];
        for (int i = 0; i < values.length; i++) {
            values[i] = buffer.getInt();
        }
        return values;
    }

    private static List<Integer> inNeighbors(Graph graph, int destination) throws IOException {
        List<Integer> sources = new ArrayList<>();
        var iterator = graph.sourcesOf(destination);
        while (iterator.hasNext()) {
            sources.add(iterator.nextInt());
        }
        sources.sort(Integer::compareTo);
        return sources;
    }

    @Test
    void routesEveryEdgeToTheBinOwningItsDestination() throws IOException {
        EdgeSource source = csv("from,to\n1,2\n1,3\n2,3\n3,1\n3,4\n4,3\n5,3\n6,3\n");
        Graph truth = InMemoryGraph.build(source);

        try (BinFiles files = EdgeBinning.bin(source, 3)) {
            List<List<Integer>> routed = new ArrayList<>();
            for (int d = 0; d < truth.vertexCount(); d++) {
                routed.add(new ArrayList<>());
            }

            long totalWritten = 0;
            List<Bin> bins = files.bins();
            for (int b = 0; b < bins.size(); b++) {
                Bin bin = bins.get(b);
                assertThat(files.recordCount(b)).isEqualTo(bin.edgeCount());
                if (bin.oversized()) {
                    for (int sourceVertex : readInts(files.pathOf(b))) {
                        routed.get(bin.begin()).add(sourceVertex);
                        totalWritten++;
                    }
                } else {
                    for (long packed : files.loadPacked(b)) {
                        int destination = (int) (packed >>> 32);
                        int sourceVertex = (int) packed;
                        assertThat(destination).isGreaterThanOrEqualTo(bin.begin()).isLessThan(bin.end());
                        routed.get(destination).add(sourceVertex);
                        totalWritten++;
                    }
                }
            }

            assertThat(totalWritten).isEqualTo(truth.edgeCount());
            for (int d = 0; d < truth.vertexCount(); d++) {
                routed.get(d).sort(Integer::compareTo);
                assertThat(routed.get(d)).isEqualTo(inNeighbors(truth, d));
            }
        }
    }

    @Test
    void singleBinHoldsAllEdgesWhenBudgetLarge() throws IOException {
        try (BinFiles files = EdgeBinning.bin(csv("from,to\n0,1\n1,2\n2,0\n"), 1_000)) {
            assertThat(files.bins()).hasSize(1);
            assertThat(files.recordCount(0)).isEqualTo(3);
        }
    }

    @Test
    void waveDistributionMatchesSinglePass() throws IOException {
        EdgeSource source = csv("from,to\n1,2\n1,3\n2,3\n3,1\n3,4\n4,3\n5,3\n6,3\n");
        Pass1Result pass1 = OutOfCoreGraphPreprocessor.pass1(source);
        List<Bin> plan = BinPlanner.plan(pass1.sourcesPtr(), 1);
        try (BinFiles onePass = BinFiles.create(plan);
                BinFiles waved = BinFiles.create(plan)) {
            onePass.distribute(source, pass1.mapper());
            waved.distribute(source, pass1.mapper(), 1L);
            assertThat(plan.size()).isGreaterThan(1);
            for (int b = 0; b < plan.size(); b++) {
                assertThat(Files.readAllBytes(waved.pathOf(b)))
                        .isEqualTo(Files.readAllBytes(onePass.pathOf(b)));
            }
        }
    }

    @Test
    void hyperNodeLandsInItsOwnOversizedBin() throws IOException {
        StringBuilder text = new StringBuilder("from,to\n");
        for (int i = 1; i <= 10; i++) {
            text.append(i).append(",0\n");
        }

        try (BinFiles files = EdgeBinning.bin(csv(text.toString()), 4)) {
            List<Bin> bins = files.bins();
            int oversized = -1;
            for (int i = 0; i < bins.size(); i++) {
                if (bins.get(i).oversized()) {
                    oversized = i;
                }
            }
            assertThat(oversized).isNotNegative();
            assertThat(files.recordCount(oversized)).isEqualTo(10);
        }
    }
}
