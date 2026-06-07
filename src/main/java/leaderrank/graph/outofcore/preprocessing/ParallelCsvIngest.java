package leaderrank.graph.outofcore.preprocessing;

import leaderrank.graph.EdgeCursor;
import leaderrank.graph.outofcore.io.IntReader;
import leaderrank.graph.outofcore.io.IntWriter;

import java.io.IOException;
import java.nio.ByteBuffer;
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
        long[] boundaries = computeBoundaries(csvFile, Files.size(csvFile), Math.max(1, parallelism));
        int chunkCount = boundaries.length - 1;
        List<Path> rawFiles = new ArrayList<>(chunkCount);
        for (int t = 0; t < chunkCount; t++) {
            Path raw = workDirectory.resolve("raw-" + t);
            raw.toFile().deleteOnExit();
            rawFiles.add(raw);
        }
        runChunks(csvFile, boundaries, rawFiles, parallelism);
        return rawFiles;
    }

    static EdgeCursor rawCursor(List<Path> rawFiles) throws IOException {
        return new RawChunkCursor(rawFiles);
    }

    private static void runChunks(Path csvFile, long[] boundaries, List<Path> rawFiles, int parallelism)
            throws IOException {
        List<Callable<Void>> tasks = new ArrayList<>(rawFiles.size());
        for (int t = 0; t < rawFiles.size(); t++) {
            long start = boundaries[t];
            long end = boundaries[t + 1];
            Path out = rawFiles.get(t);
            tasks.add(() -> {
                parseChunk(csvFile, start, end, out);
                return null;
            });
        }
        try (ExecutorService pool = Executors.newFixedThreadPool(Math.max(1, parallelism))) {
            for (Future<Void> future : pool.invokeAll(tasks)) {
                future.get();
            }
        } catch (ExecutionException e) {
            throw asIoException(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("parallel CSV parse interrupted", e);
        }
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
        try (IntWriter writer = IntWriter.create(out, WRITE_BUFFER_BYTES);
                FileChannel in = FileChannel.open(csvFile, StandardOpenOption.READ)) {
            ByteBuffer read = ByteBuffer.allocate(READ_BUFFER_BYTES);
            RowParser parser = new RowParser();
            long position = start;
            while (position < end) {
                read.clear();
                read.limit((int) Math.min(read.capacity(), end - position));
                int n = in.read(read, position);
                if (n <= 0) {
                    break;
                }
                for (int i = 0; i < n; i++) {
                    parser.feed(read.get(i), writer);
                }
                position += n;
            }
            parser.finishRow(writer);
        }
    }

    private static IOException asIoException(Throwable cause) {
        if (cause instanceof IOException io) {
            return io;
        }
        return new IOException("parallel CSV parse failed", cause);
    }

    private static final class RowParser {

        private int column;
        private int from;
        private int value;
        private boolean negative;
        private boolean hasDigit;

        void feed(byte b, IntWriter writer) throws IOException {
            if (b >= '0' && b <= '9') {
                value = value * 10 + (b - '0');
                hasDigit = true;
            } else if (b == ',') {
                onComma(writer);
            } else if (b == '\n') {
                onNewline(writer);
            } else if (b == '-') {
                negative = true;
            } else if (b != '\r' && b != ' ' && b != '\t') {
                throw new IOException("malformed CSV byte: " + (b & 0xFF));
            }
        }

        private void onComma(IntWriter writer) throws IOException {
            if (column == 0) {
                from = signedValue();
                column = 1;
                resetField();
            } else if (column == 1) {
                writer.write(from, signedValue());
                column = 2;
                resetField();
            }
        }

        private void onNewline(IntWriter writer) throws IOException {
            finishRow(writer);
            column = 0;
            resetField();
        }

        void finishRow(IntWriter writer) throws IOException {
            if (column == 1) {
                if (!hasDigit) {
                    throw new IOException("malformed CSV row: empty destination column");
                }
                writer.write(from, signedValue());
            } else if (column == 0 && hasDigit) {
                throw new IOException("malformed CSV row: expected 'from,to'");
            }
        }

        private int signedValue() {
            return negative ? -value : value;
        }

        private void resetField() {
            value = 0;
            negative = false;
            hasDigit = false;
        }
    }

    private static final class RawChunkCursor implements EdgeCursor {

        private final List<Path> files;
        private int index = -1;
        private IntReader reader;
        private int from;
        private int to;

        RawChunkCursor(List<Path> files) throws IOException {
            this.files = files;
            openNext();
        }

        private boolean openNext() throws IOException {
            if (reader != null) {
                reader.close();
                reader = null;
            }
            index++;
            if (index >= files.size()) {
                return false;
            }
            reader = IntReader.open(files.get(index), READ_BUFFER_BYTES);
            return true;
        }

        @Override
        public boolean next() throws IOException {
            while (reader != null) {
                if (!reader.hasNext()) {
                    openNext();
                    continue;
                }
                from = reader.next();
                to = reader.next();
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
            if (reader != null) {
                reader.close();
                reader = null;
            }
        }
    }
}
