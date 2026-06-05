package leaderrank.io;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import leaderrank.graph.Graph;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

public final class RankCsvWriter {

    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader("vertex", "rank")
            .setRecordSeparator('\n')
            .build();

    public void write(Path path, Graph graph, double[] scores) throws IOException {
        try (BufferedWriter out = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            write(out, graph, scores);
        }
    }

    public void write(Appendable out, Graph graph, double[] scores) throws IOException {
        if (scores.length != graph.vertexCount()) {
            throw new IllegalArgumentException("scores length must equal vertexCount");
        }
        try (CSVPrinter printer = new CSVPrinter(out, FORMAT)) {
            for (int vertex : orderByRank(graph, scores)) {
                printer.printRecord(graph.originalId(vertex), Double.toString(scores[vertex]));
            }
        }
    }

    private static int[] orderByRank(Graph graph, double[] scores) {
        int n = graph.vertexCount();
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        Arrays.sort(order, Comparator.comparingDouble((Integer v) -> scores[v]).reversed()
                .thenComparingInt(graph::originalId));
        return Arrays.stream(order).mapToInt(Integer::intValue).toArray();
    }
}
