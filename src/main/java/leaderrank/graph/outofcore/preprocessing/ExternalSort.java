package leaderrank.graph.outofcore.preprocessing;

import leaderrank.graph.outofcore.io.IntReader;
import leaderrank.graph.outofcore.io.IntWriter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
            List<Path> runs = reduceToFanIn(created, workDirectory, maxFanIn(availableBytes));
            merge(runs, sink);
        } finally {
            deleteAll(created);
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
        try (IntReader reader = IntReader.open(binFile, IO_BUFFER_BYTES)) {
            int filled;
            while ((filled = readChunk(reader, chunk)) > 0) {
                Arrays.sort(chunk, 0, filled);
                runs.add(writeRun(workDirectory, runs.size(), chunk, filled));
            }
        }
    }

    private static int readChunk(IntReader reader, int[] chunk) throws IOException {
        int count = 0;
        while (count < chunk.length && reader.hasNext()) {
            chunk[count++] = reader.next();
        }
        return count;
    }

    private static Path writeRun(Path workDirectory, int index, int[] chunk, int length) throws IOException {
        Path run = workDirectory.resolve("run-" + index);
        try (IntWriter writer = IntWriter.create(run, IO_BUFFER_BYTES)) {
            for (int i = 0; i < length; i++) {
                writer.write(chunk[i]);
            }
        }
        return run;
    }

    private static List<Path> reduceToFanIn(List<Path> created, Path workDirectory, int maxFanIn) throws IOException {
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
            deleteAll(current);
            current = next;
            generation++;
        }
        return current;
    }

    private static void mergeToFile(List<Path> runs, Path output) throws IOException {
        try (IntWriter writer = IntWriter.create(output, IO_BUFFER_BYTES)) {
            merge(runs, writer::write);
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

    private static void deleteAll(List<Path> paths) throws IOException {
        for (Path path : paths) {
            Files.deleteIfExists(path);
        }
    }

    private static final class RunCursor implements Comparable<RunCursor>, AutoCloseable {

        private final IntReader reader;
        private int current;

        RunCursor(Path run) throws IOException {
            this.reader = IntReader.open(run, IO_BUFFER_BYTES);
        }

        boolean advance() throws IOException {
            if (!reader.hasNext()) {
                return false;
            }
            current = reader.next();
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
            reader.close();
        }
    }
}
