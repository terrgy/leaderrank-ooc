package leaderrank.graph.outofcore;

import leaderrank.graph.Graph;
import leaderrank.graph.edge.EdgeSource;
import leaderrank.graph.source.SourceCursor;
import leaderrank.graph.source.SourceFileStream;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.PrimitiveIterator;

public final class OutOfCoreGraph implements Graph {
    static final int SOURCES_BUFFER_BYTES = 1024;
    static final MemoryBudget DEFAULT_BUDGET = new MemoryBudget(256L << 20);

    private final OutOfCorePreprocessingData data;

    private OutOfCoreGraph(OutOfCorePreprocessingData data) {
        this.data = data;
    }

    public static Graph build(EdgeSource source) throws IOException {
        return build(source, DEFAULT_BUDGET);
    }

    public static Graph build(EdgeSource source, MemoryBudget budget) throws IOException {
        return new OutOfCoreGraph(OutOfCoreGraphPreprocessor.process(source, sourcesFile(), budget));
    }

    public static Graph build(EdgeSource source, long maxEdgesPerBin) throws IOException {
        return new OutOfCoreGraph(OutOfCoreGraphPreprocessor.process(source, sourcesFile(), maxEdgesPerBin));
    }

    private static Path sourcesFile() throws IOException {
        Path sourcesFile = Files.createTempFile("leaderrank-", ".sources");
        sourcesFile.toFile().deleteOnExit();
        return sourcesFile;
    }

    @Override
    public int vertexCount() {
        return data.outDegrees().length;
    }

    @Override
    public long edgeCount() {
        return data.edgeCount();
    }

    @Override
    public int outDegree(int denseId) {
        return data.outDegrees()[denseId];
    }

    @Override
    public int inDegree(int denseId) {
        return data.sourcesPtr()[denseId + 1] - data.sourcesPtr()[denseId];
    }

    @Override
    public int originalId(int denseId) {
        return data.originalIds()[denseId];
    }

    @Override
    public PrimitiveIterator.OfInt sourcesOf(int destinationDenseId) throws IOException {
        int begin = data.sourcesPtr()[destinationDenseId];
        int count = data.sourcesPtr()[destinationDenseId + 1] - begin;
        int[] window = new int[count];
        try (FileChannel channel = FileChannel.open(data.sourcesFile(), StandardOpenOption.READ)) {
            ByteBuffer buffer = ByteBuffer.allocate(count * Integer.BYTES).order(ByteOrder.LITTLE_ENDIAN);
            long position = (long) begin * Integer.BYTES;
            while (buffer.hasRemaining()) {
                int read = channel.read(buffer, position);
                if (read < 0) {
                    throw new IOException("unexpected end of " + data.sourcesFile());
                }
                position += read;
            }
            buffer.flip();
            for (int i = 0; i < count; i++) {
                window[i] = buffer.getInt();
            }
        }
        return Arrays.stream(window).iterator();
    }

    @Override
    public SourceCursor openSourceCursor() throws IOException {
        return new SourceFileStream(data.sourcesFile(), SOURCES_BUFFER_BYTES);
    }
}
