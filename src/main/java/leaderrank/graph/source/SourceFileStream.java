package leaderrank.graph.source;

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
        this.channel = FileChannel.open(sourcesFile, StandardOpenOption.READ);
        int capacity = Math.max(Integer.BYTES, bufferBytes & ~3);
        this.buffer = ByteBuffer.allocateDirect(capacity).order(ByteOrder.LITTLE_ENDIAN);
        this.buffer.position(0).limit(0);
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