package leaderrank.graph.outofcore;

import leaderrank.graph.SourceCursor;
import leaderrank.graph.outofcore.io.IntReader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;

public final class SourceFileStream implements SourceCursor {

    private final IntReader reader;

    public SourceFileStream(Path sourcesFile, int bufferBytes) throws IOException {
        this(sourcesFile, bufferBytes, 0);
    }

    public SourceFileStream(Path sourcesFile, int bufferBytes, long startByteOffset) throws IOException {
        this.reader = IntReader.open(sourcesFile, bufferBytes, startByteOffset);
    }

    public SourceFileStream(Path sourcesFile, ByteBuffer reusedBuffer, long startByteOffset) throws IOException {
        this.reader = IntReader.open(sourcesFile, reusedBuffer, startByteOffset);
    }

    @Override
    public int next() throws IOException {
        return reader.next();
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }
}
