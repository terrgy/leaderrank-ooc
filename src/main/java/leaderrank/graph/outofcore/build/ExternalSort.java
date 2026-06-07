package leaderrank.graph.outofcore.build;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;

final class ExternalSort {

    private static final int IO_BUFFER_BYTES = 1 << 16;

    private ExternalSort() {
    }

    static void sort(Path binFile, int chunkRecords, Path workDirectory, IntSink sink) throws IOException {
        sort(binFile, chunkRecords, workDirectory, sink, Long.MAX_VALUE);
    }

    static void sort(Path binFile, int chunkRecords, Path workDirectory, IntSink sink, long availableBytes)
            throws IOException {
        List<Path> created = new ArrayList<>();
        try {
            generateRuns(binFile, chunkRecords, workDirectory, created);
            int maxFanIn = maxFanIn(availableBytes);
            List<Path> current = new ArrayList<>(created);
            int generation = 0;
            while (current.size() > maxFanIn) {
                List<Path> next = new ArrayList<>();
                for (int i = 0; i < current.size(); i += maxFanIn) {
                    List<Path> group = current.subList(i, Math.min(current.size(), i + maxFanIn));
                    Path merged = workDirectory.resolve("merge-" + generation + "-" + (i / maxFanIn));
                    created.add(merged);
                    mergeToFile(group, merged);
                    next.add(merged);
                }
                for (Path consumed : current) {
                    Files.deleteIfExists(consumed);
                }
                current = next;
                generation++;
            }
            merge(current, sink);
        } finally {
            for (Path path : created) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static int maxFanIn(long availableBytes) {
        long fanIn = availableBytes / IO_BUFFER_BYTES;
        if (fanIn < 2) {
            return 2;
        }
        return (int) Math.min(fanIn, Integer.MAX_VALUE);
    }

    private static void generateRuns(Path binFile, int chunkRecords, Path workDirectory, List<Path> runs)
            throws IOException {
        int[] chunk = new int[chunkRecords];
        try (FileChannel channel = FileChannel.open(binFile, StandardOpenOption.READ)) {
            ByteBuffer buffer = ByteBuffer.allocate(IO_BUFFER_BYTES).order(ByteOrder.LITTLE_ENDIAN);
            buffer.position(0).limit(0);
            int filled;
            while ((filled = readChunk(channel, buffer, chunk)) > 0) {
                Arrays.sort(chunk, 0, filled);
                runs.add(writeRun(workDirectory, runs.size(), chunk, filled));
            }
        }
    }

    private static int readChunk(FileChannel channel, ByteBuffer buffer, int[] chunk) throws IOException {
        int count = 0;
        while (count < chunk.length) {
            if (buffer.remaining() < Integer.BYTES && !refill(channel, buffer, Integer.BYTES)) {
                break;
            }
            chunk[count++] = buffer.getInt();
        }
        return count;
    }

    private static Path writeRun(Path workDirectory, int index, int[] chunk, int length) throws IOException {
        Path run = workDirectory.resolve("run-" + index);
        try (FileChannel channel = FileChannel.open(run,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer buffer = ByteBuffer.allocate(IO_BUFFER_BYTES).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < length; i++) {
                if (buffer.remaining() < Integer.BYTES) {
                    drain(channel, buffer);
                }
                buffer.putInt(chunk[i]);
            }
            drain(channel, buffer);
        }
        return run;
    }

    private static void mergeToFile(List<Path> runs, Path output) throws IOException {
        try (FileChannel channel = FileChannel.open(output,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer buffer = ByteBuffer.allocate(IO_BUFFER_BYTES).order(ByteOrder.LITTLE_ENDIAN);
            merge(runs, value -> {
                if (buffer.remaining() < Integer.BYTES) {
                    drain(channel, buffer);
                }
                buffer.putInt(value);
            });
            drain(channel, buffer);
        }
    }

    private static void merge(List<Path> runs, IntSink sink) throws IOException {
        PriorityQueue<RunCursor> queue = new PriorityQueue<>();
        try {
            for (Path run : runs) {
                RunCursor cursor = new RunCursor(run);
                if (cursor.advance()) {
                    queue.add(cursor);
                } else {
                    cursor.close();
                }
            }
            while (!queue.isEmpty()) {
                RunCursor cursor = queue.poll();
                sink.accept(cursor.current());
                if (cursor.advance()) {
                    queue.add(cursor);
                } else {
                    cursor.close();
                }
            }
        } finally {
            for (RunCursor cursor : queue) {
                cursor.close();
            }
        }
    }

    private static boolean refill(FileChannel channel, ByteBuffer buffer, int minimumBytes) throws IOException {
        buffer.compact();
        while (buffer.position() < minimumBytes) {
            if (channel.read(buffer) < 0) {
                break;
            }
        }
        buffer.flip();
        return buffer.remaining() >= minimumBytes;
    }

    private static void drain(FileChannel channel, ByteBuffer buffer) throws IOException {
        buffer.flip();
        while (buffer.hasRemaining()) {
            channel.write(buffer);
        }
        buffer.clear();
    }

    private static final class RunCursor implements Comparable<RunCursor>, AutoCloseable {
        private final FileChannel channel;
        private final ByteBuffer buffer;
        private int current;

        RunCursor(Path run) throws IOException {
            this.channel = FileChannel.open(run, StandardOpenOption.READ);
            this.buffer = ByteBuffer.allocate(IO_BUFFER_BYTES).order(ByteOrder.LITTLE_ENDIAN);
            this.buffer.position(0).limit(0);
        }

        boolean advance() throws IOException {
            if (buffer.remaining() < Integer.BYTES && !refill(channel, buffer, Integer.BYTES)) {
                return false;
            }
            current = buffer.getInt();
            return true;
        }

        int current() {
            return current;
        }

        @Override
        public int compareTo(RunCursor other) {
            return Integer.compare(current, other.current);
        }

        @Override
        public void close() throws IOException {
            channel.close();
        }
    }
}
