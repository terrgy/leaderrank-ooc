package leaderrank.gen;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import leaderrank.graph.Graph;
import leaderrank.graph.GraphFactory;
import leaderrank.graph.edge.EdgeCursor;
import leaderrank.graph.edge.EdgeSource;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

public final class RmatGenerator implements EdgeSource {

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
        this.seed = seed;
    }

    public int vertexCount() {
        return 1 << scale;
    }

    public long edgeCount() {
        return edges;
    }

    @Override
    public EdgeCursor open() {
        return new EdgeCursor() {
            private final Random random = new Random(seed);
            private long produced = 0;
            private int from;
            private int to;

            @Override
            public boolean next() {
                if (produced >= edges) {
                    return false;
                }
                long edge = nextEdge(random);
                from = (int) (edge >>> 32);
                to = (int) edge;
                produced++;
                return true;
            }

            @Override
            public int from() {
                return from;
            }

            @Override
            public int to() {
                return to;
            }

            @Override
            public void close() {
            }
        };
    }

    public Graph toGraph(GraphFactory factory) throws IOException {
        return factory.create(this);
    }

    public void generate(EdgeConsumer consumer) throws IOException {
        Random random = new Random(seed);
        for (long i = 0; i < edges; i++) {
            long edge = nextEdge(random);
            consumer.accept((int) (edge >>> 32), (int) edge);
        }
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
