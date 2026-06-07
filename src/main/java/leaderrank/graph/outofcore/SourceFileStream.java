package leaderrank.graph.outofcore;

import leaderrank.graph.SourceCursor;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class SourceFileStream implements SourceCursor {

    private final FileChannel channel;
    private final ByteBuffer buffer;

    public SourceFileStream(Path sourcesFile, int bufferBytes) throws IOException {
        this(sourcesFile, bufferBytes, 0);
    }

    public SourceFileStream(Path sourcesFile, int bufferBytes, long startByteOffset) throws IOException {
        int capacity = Math.max(Integer.BYTES, bufferBytes & ~3);
        this.channel = open(sourcesFile, startByteOffset);
        this.buffer = ByteBuffer.allocate(capacity).order(ByteOrder.LITTLE_ENDIAN);
        this.buffer.position(0).limit(0);
    }

    public SourceFileStream(Path sourcesFile, ByteBuffer reusedBuffer, long startByteOffset) throws IOException {
        this.channel = open(sourcesFile, startByteOffset);
        this.buffer = reusedBuffer;
        this.buffer.position(0).limit(0);
    }

    private static FileChannel open(Path sourcesFile, long startByteOffset) throws IOException {
        FileChannel channel = FileChannel.open(sourcesFile, StandardOpenOption.READ);
        channel.position(startByteOffset);
        return channel;
    }

    @Override
    public int next() throws IOException {
        if (buffer.remaining() < Integer.BYTES) {
            refill();
        }
        return buffer.getInt();
    }

    private void refill() throws IOException {
        buffer.compact();
        int read = channel.read(buffer);
        buffer.flip();
        if (read <= 0 && buffer.remaining() < Integer.BYTES) {
            throw new IOException("unexpected end of " + channel);
        }
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }
}