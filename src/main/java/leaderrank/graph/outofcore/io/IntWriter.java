package leaderrank.graph.outofcore.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public final class IntWriter implements AutoCloseable {

    private final FileChannel channel;
    private final ByteBuffer buffer;

    private IntWriter(FileChannel channel, int bufferBytes) {
        this.channel = channel;
        this.buffer = ByteBuffer.allocate(bufferBytes).order(ByteOrder.LITTLE_ENDIAN);
    }

    public static IntWriter create(Path file, int bufferBytes) throws IOException {
        return new IntWriter(FileChannel.open(file,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING), bufferBytes);
    }

    public void write(int value) throws IOException {
        if (buffer.remaining() < Integer.BYTES) {
            flush();
        }
        buffer.putInt(value);
    }

    public void write(int first, int second) throws IOException {
        if (buffer.remaining() < 2 * Integer.BYTES) {
            flush();
        }
        buffer.putInt(first);
        buffer.putInt(second);
    }

    private void flush() throws IOException {
        buffer.flip();
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
        buffer.clear();
    }

    @Override
    public void close() throws IOException {
        try (channel) {
            flush();
            channel.force(false);
        }
    }
}
