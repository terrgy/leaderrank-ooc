package leaderrank.graph.outofcore.io;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

// A file read as a stream of little-endian ints. next() refills in bulk and throws at an unexpected
// end, so a caller that knows the count can skip hasNext. The buffer can be borrowed for per-thread reuse.
public final class IntReader implements AutoCloseable {

    private final FileChannel channel;
    private final ByteBuffer buffer;

    private IntReader(FileChannel channel, ByteBuffer buffer) {
        this.channel = channel;
        this.buffer = buffer.order(ByteOrder.LITTLE_ENDIAN);
        this.buffer.position(0).limit(0);
    }

    public static IntReader open(Path file, int bufferBytes) throws IOException {
        return open(file, bufferBytes, 0);
    }

    public static IntReader open(Path file, int bufferBytes, long startByteOffset) throws IOException {
        return new IntReader(openAt(file, startByteOffset), allocate(bufferBytes));
    }

    public static IntReader open(Path file, ByteBuffer reusedBuffer, long startByteOffset) throws IOException {
        return new IntReader(openAt(file, startByteOffset), reusedBuffer);
    }

    public boolean hasNext() throws IOException {
        if (buffer.remaining() >= Integer.BYTES) {
            return true;
        }
        refill();
        return buffer.remaining() >= Integer.BYTES;
    }

    public int next() throws IOException {
        if (buffer.remaining() < Integer.BYTES) {
            refill();
            if (buffer.remaining() < Integer.BYTES) {
                throw new IOException("unexpected end of " + channel);
            }
        }
        return buffer.getInt();
    }

    private void refill() throws IOException {
        buffer.compact();
        while (buffer.position() < Integer.BYTES && channel.read(buffer) >= 0) {
        }
        buffer.flip();
    }

    @Override
    public void close() throws IOException {
        channel.close();
    }

    private static FileChannel openAt(Path file, long startByteOffset) throws IOException {
        FileChannel channel = FileChannel.open(file, StandardOpenOption.READ);
        channel.position(startByteOffset);
        return channel;
    }

    private static ByteBuffer allocate(int bufferBytes) {
        return ByteBuffer.allocate(Math.max(Integer.BYTES, bufferBytes & ~3));
    }
}
