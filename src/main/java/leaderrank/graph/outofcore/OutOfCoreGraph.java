package leaderrank.graph.outofcore;

import leaderrank.graph.outofcore.build.MemoryBudget;
import leaderrank.graph.outofcore.build.Preprocessor;
import leaderrank.graph.outofcore.build.PreprocessingData;
import leaderrank.graph.Graph;
import leaderrank.graph.EdgeSource;
import leaderrank.graph.SourceCursor;

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
    static final int SOURCES_BUFFER_BYTES = 1 << 16;

    private final PreprocessingData data;
    private final ThreadLocal<ByteBuffer> gatherBuffer =
            ThreadLocal.withInitial(() -> ByteBuffer.allocate(SOURCES_BUFFER_BYTES).order(ByteOrder.LITTLE_ENDIAN));

    private OutOfCoreGraph(PreprocessingData data) {
        this.data = data;
    }

    public static Graph build(EdgeSource source) throws IOException {
        return build(source, Runtime.getRuntime().availableProcessors());
    }

    public static Graph build(EdgeSource source, int parallelism) throws IOException {
        return build(source, MemoryBudget.discover(), parallelism);
    }

    public static Graph build(EdgeSource source, MemoryBudget budget) throws IOException {
        return build(source, budget, Runtime.getRuntime().availableProcessors());
    }

    public static Graph build(EdgeSource source, MemoryBudget budget, int parallelism) throws IOException {
        return new OutOfCoreGraph(
                Preprocessor.process(source, sourcesFile(), budget, parallelism));
    }

    public static Graph build(EdgeSource source, long maxEdgesPerBin) throws IOException {
        return build(source, maxEdgesPerBin, Runtime.getRuntime().availableProcessors());
    }

    public static Graph build(EdgeSource source, long maxEdgesPerBin, int parallelism) throws IOException {
        return new OutOfCoreGraph(
                Preprocessor.process(source, sourcesFile(), maxEdgesPerBin, parallelism));
    }

    private static Path sourcesFile() throws IOException {
        Path sourcesFile = Files.createTempFile("leaderrank-", ".sources");
        sourcesFile.toFile().deleteOnExit();
        return sourcesFile;
    }

    public int binCount() {
        return data.binCount();
    }

    public int maxEdgesPerBin() {
        return data.maxEdgesPerBin();
    }

    public int distributionWaves() {
        return data.distributionWaves();
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
    public SourceCursor openSourceCursor(int fromDestinationDenseId) throws IOException {
        long startByteOffset = (long) data.sourcesPtr()[fromDestinationDenseId] * Integer.BYTES;
        return new SourceFileStream(data.sourcesFile(), gatherBuffer.get(), startByteOffset);
    }
}
