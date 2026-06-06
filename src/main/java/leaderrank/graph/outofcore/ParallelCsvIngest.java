package leaderrank.graph.outofcore;

import leaderrank.graph.edge.EdgeCursor;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

final class ParallelCsvIngest {

    private static final int READ_BUFFER_BYTES = 1 << 16;
    private static final int WRITE_BUFFER_BYTES = 1 << 16;
    private static final int SCAN_BUFFER_BYTES = 1 << 12;

    private ParallelCsvIngest() {
    }

    static List<Path> parseToRawChunks(Path csvFile, int parallelism, Path workDirectory) throws IOException {
        long size = Files.size(csvFile);
        long[] boundaries = computeBoundaries(csvFile, size, Math.max(1, parallelism));
        int chunkCount = boundaries.length - 1;
        List<Path> rawFiles = new ArrayList<>(chunkCount);
        for (int t = 0; t < chunkCount; t++) {
            Path raw = workDirectory.resolve("raw-" + t);
            raw.toFile().deleteOnExit();
            rawFiles.add(raw);
        }

        ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, parallelism));
        try {
            List<Callable<Void>> tasks = new ArrayList<>(chunkCount);
            for (int t = 0; t < chunkCount; t++) {
                long start = boundaries[t];
                long end = boundaries[t + 1];
                Path out = rawFiles.get(t);
                tasks.add(() -> {
                    parseChunk(csvFile, start, end, out);
                    return null;
                });
            }
            for (Future<Void> future : pool.invokeAll(tasks)) {
                future.get();
            }
        } catch (ExecutionException e) {
            throw asIoException(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("parallel CSV parse interrupted", e);
        } finally {
            pool.shutdown();
        }
        return rawFiles;
    }

    static EdgeCursor rawCursor(List<Path> rawFiles) throws IOException {
        return new RawChunkCursor(rawFiles);
    }

    private static long[] computeBoundaries(Path csvFile, long size, int chunkCount) throws IOException {
        long[] boundaries = new long[chunkCount + 1];
        try (FileChannel channel = FileChannel.open(csvFile, StandardOpenOption.READ)) {
            boundaries[0] = afterNextNewline(channel, 0, size);
            for (int t = 1; t < chunkCount; t++) {
                long nominal = size * t / chunkCount;
                boundaries[t] = nominal <= boundaries[0] ? boundaries[0] : afterNextNewline(channel, nominal, size);
            }
        }
        boundaries[chunkCount] = size;
        for (int t = 1; t <= chunkCount; t++) {
            if (boundaries[t] < boundaries[t - 1]) {
                boundaries[t] = boundaries[t - 1];
            }
        }
        return boundaries;
    }

    private static long afterNextNewline(FileChannel channel, long from, long size) throws IOException {
        if (from >= size) {
            return size;
        }
        ByteBuffer buffer = ByteBuffer.allocate(SCAN_BUFFER_BYTES);
        long position = from;
        while (position < size) {
            buffer.clear();
            int read = channel.read(buffer, position);
            if (read <= 0) {
                break;
            }
            for (int i = 0; i < read; i++) {
                if (buffer.get(i) == '\n') {
                    return position + i + 1;
                }
            }
            position += read;
        }
        return size;
    }

    private static void parseChunk(Path csvFile, long start, long end, Path out) throws IOException {
        try (FileChannel in = FileChannel.open(csvFile, StandardOpenOption.READ);
                FileChannel outChannel = FileChannel.open(out,
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer read = ByteBuffer.allocate(READ_BUFFER_BYTES);
            ByteBuffer write = ByteBuffer.allocate(WRITE_BUFFER_BYTES).order(ByteOrder.LITTLE_ENDIAN);
            long position = start;
            int column = 0;
            int from = 0;
            int value = 0;
            boolean negative = false;
            boolean hasDigit = false;
            while (position < end) {
                read.clear();
                read.limit((int) Math.min(read.capacity(), end - position));
                int n = in.read(read, position);
                if (n <= 0) {
                    break;
                }
                for (int i = 0; i < n; i++) {
                    byte b = read.get(i);
                    if (b >= '0' && b <= '9') {
                        value = value * 10 + (b - '0');
                        hasDigit = true;
                    } else if (b == ',') {
                        if (column == 0) {
                            from = negative ? -value : value;
                            column = 1;
                            value = 0;
                            negative = false;
                            hasDigit = false;
                        } else if (column == 1) {
                            emit(outChannel, write, from, negative ? -value : value);
                            column = 2;
                            value = 0;
                            negative = false;
                            hasDigit = false;
                        }
                    } else if (b == '\n') {
                        if (column == 1) {
                            if (!hasDigit) {
                                throw new IOException("malformed CSV row: empty destination column");
                            }
                            emit(outChannel, write, from, negative ? -value : value);
                        } else if (column == 0 && hasDigit) {
                            throw new IOException("malformed CSV row: expected 'from,to'");
                        }
                        column = 0;
                        value = 0;
                        negative = false;
                        hasDigit = false;
                    } else if (b == '-') {
                        negative = true;
                    } else if (b != '\r' && b != ' ' && b != '\t') {
                        throw new IOException("malformed CSV byte: " + (b & 0xFF));
                    }
                }
                position += n;
            }
            if (column == 1) {
                if (!hasDigit) {
                    throw new IOException("malformed CSV row: empty destination column");
                }
                emit(outChannel, write, from, negative ? -value : value);
            } else if (column == 0 && hasDigit) {
                throw new IOException("malformed CSV row: expected 'from,to'");
            }
            drain(outChannel, write);
            outChannel.force(false);
        }
    }

    private static void emit(FileChannel channel, ByteBuffer buffer, int from, int to) throws IOException {
        if (buffer.remaining() < 2 * Integer.BYTES) {
            drain(channel, buffer);
        }
        buffer.putInt(from);
        buffer.putInt(to);
    }

    private static void drain(FileChannel channel, ByteBuffer buffer) throws IOException {
        buffer.flip();
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
        buffer.clear();
    }

    private static IOException asIoException(Throwable cause) {
        if (cause instanceof IOException io) {
            return io;
        }
        return new IOException("parallel CSV parse failed", cause);
    }

    private static final class RawChunkCursor implements EdgeCursor {

        private final List<Path> files;
        private final ByteBuffer buffer;
        private int index = -1;
        private FileChannel channel;
        private int from;
        private int to;

        RawChunkCursor(List<Path> files) throws IOException {
            this.files = files;
            this.buffer = ByteBuffer.allocate(READ_BUFFER_BYTES).order(ByteOrder.LITTLE_ENDIAN);
            this.buffer.position(0).limit(0);
            openNext();
        }

        private boolean openNext() throws IOException {
            if (channel != null) {
                channel.close();
                channel = null;
            }
            index++;
            if (index >= files.size()) {
                return false;
            }
            channel = FileChannel.open(files.get(index), StandardOpenOption.READ);
            buffer.position(0).limit(0);
            return true;
        }

        @Override
        public boolean next() throws IOException {
            while (channel != null) {
                if (buffer.remaining() < 2 * Integer.BYTES) {
                    buffer.compact();
                    channel.read(buffer);
                    buffer.flip();
                    if (buffer.remaining() < 2 * Integer.BYTES) {
                        openNext();
                        continue;
                    }
                }
                from = buffer.getInt();
                to = buffer.getInt();
                return true;
            }
            return false;
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
        public void close() throws IOException {
            if (channel != null) {
                channel.close();
                channel = null;
            }
        }
    }
}
