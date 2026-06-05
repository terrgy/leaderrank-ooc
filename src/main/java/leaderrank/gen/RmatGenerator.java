package leaderrank.gen;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import leaderrank.graph.Graph;
import leaderrank.graph.InMemoryGraph;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

public final class RmatGenerator {

    public static final double DEFAULT_A = 0.57;
    public static final double DEFAULT_B = 0.19;
    public static final double DEFAULT_C = 0.19;
    public static final double DEFAULT_D = 0.05;

    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader("from", "to")
            .setRecordSeparator('\n')
            .build();

    private final int scale;
    private final long edges;
    private final double a;
    private final double b;
    private final double c;
    private final double d;
    private final long seed;

    public RmatGenerator(int scale, long edges, long seed) {
        this(scale, edges, DEFAULT_A, DEFAULT_B, DEFAULT_C, DEFAULT_D, seed);
    }

    public RmatGenerator(int scale, long edges, double a, double b, double c, double d, long seed) {
        if (scale < 1 || scale > 30) {
            throw new IllegalArgumentException("scale must be between 1 and 30");
        }
        if (edges < 0) {
            throw new IllegalArgumentException("edges must not be negative");
        }
        double total = a + b + c + d;
        if (a < 0 || b < 0 || c < 0 || d < 0 || Math.abs(total - 1.0) > 1e-9) {
            throw new IllegalArgumentException("probabilities must be non-negative and sum to 1");
        }
        this.scale = scale;
        this.edges = edges;
        this.a = a;
        this.b = b;
        this.c = c;
        this.d = d;
        this.seed = seed;
    }

    public int vertexCount() {
        return 1 << scale;
    }

    public long edgeCount() {
        return edges;
    }

    public void generate(EdgeConsumer consumer) throws IOException {
        Random random = new Random(seed);
        for (long i = 0; i < edges; i++) {
            long edge = nextEdge(random);
            consumer.accept((int) (edge >>> 32), (int) edge);
        }
    }

    public Graph toGraph() {
        if (edges > Integer.MAX_VALUE) {
            throw new IllegalStateException("edge count too large for an in-memory graph");
        }
        int n = vertexCount();
        int m = (int) edges;
        int[] sources = new int[m];
        int[] targets = new int[m];
        int[] outDegrees = new int[n];
        Random random = new Random(seed);
        for (int i = 0; i < m; i++) {
            long edge = nextEdge(random);
            int from = (int) (edge >>> 32);
            int to = (int) edge;
            sources[i] = from;
            targets[i] = to;
            outDegrees[from]++;
        }
        int[] originalIds = new int[n];
        for (int v = 0; v < n; v++) {
            originalIds[v] = v;
        }
        return new InMemoryGraph(n, sources, targets, outDegrees, originalIds);
    }

    public void writeCsv(Path path) throws IOException {
        try (BufferedWriter out = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writeCsv(out);
        }
    }

    public void writeCsv(Appendable out) throws IOException {
        try (CSVPrinter printer = new CSVPrinter(out, FORMAT)) {
            generate((from, to) -> printer.printRecord(from, to));
        }
    }

    private long nextEdge(Random random) {
        int from = 0;
        int to = 0;
        double ab = a + b;
        double abc = a + b + c;
        for (int bit = scale - 1; bit >= 0; bit--) {
            double r = random.nextDouble();
            int step = 1 << bit;
            if (r < a) {
                continue;
            }
            if (r < ab) {
                to += step;
            } else if (r < abc) {
                from += step;
            } else {
                from += step;
                to += step;
            }
        }
        return ((long) from << 32) | (to & 0xffffffffL);
    }
}
