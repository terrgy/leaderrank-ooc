package leaderrank.graph.outofcore.build;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExternalSortTest {

    private static Path writeBin(Path directory, int[] values) throws IOException {
        Path bin = directory.resolve("bin-0");
        try (FileChannel channel = FileChannel.open(bin,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            ByteBuffer buffer = ByteBuffer.allocate(values.length * Integer.BYTES + Integer.BYTES)
                    .order(ByteOrder.LITTLE_ENDIAN);
            for (int value : values) {
                buffer.putInt(value);
            }
            buffer.flip();
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
        }
        return bin;
    }

    private static List<Integer> sortToList(Path bin, int chunkRecords, Path workDirectory) throws IOException {
        List<Integer> out = new ArrayList<>();
        ExternalSort.sort(bin, chunkRecords, workDirectory, value -> out.add(value));
        return out;
    }

    @Test
    void mergesManyRunsIntoSortedOrder(@TempDir Path dir) throws IOException {
        Random random = new Random(7);
        int[] values = new int[1000];
        for (int i = 0; i < values.length; i++) {
            values[i] = random.nextInt(10_000);
        }
        Path bin = writeBin(dir, values);

        List<Integer> out = sortToList(bin, 16, dir);

        int[] expected = values.clone();
        Arrays.sort(expected);
        assertThat(out).hasSize(expected.length);
        for (int i = 0; i < expected.length; i++) {
            assertThat(out.get(i)).isEqualTo(expected[i]);
        }
    }

    @Test
    void keepsDuplicateValues(@TempDir Path dir) throws IOException {
        Path bin = writeBin(dir, new int[] {7, 7, 3, 7, 3});
        assertThat(sortToList(bin, 2, dir)).containsExactly(3, 3, 7, 7, 7);
    }

    @Test
    void singleRunWhenChunkLargerThanInput(@TempDir Path dir) throws IOException {
        Path bin = writeBin(dir, new int[] {9, 1, 5});
        assertThat(sortToList(bin, 100, dir)).containsExactly(1, 5, 9);
    }

    @Test
    void boundedFanInMatchesUnboundedMerge(@TempDir Path dir) throws IOException {
        Random random = new Random(11);
        int[] values = new int[5000];
        for (int i = 0; i < values.length; i++) {
            values[i] = random.nextInt(1_000_000);
        }
        Path bin = writeBin(dir, values);

        List<Integer> bounded = new ArrayList<>();
        ExternalSort.sort(bin, 8, dir, value -> bounded.add(value), 2L * (1 << 16));

        int[] expected = values.clone();
        Arrays.sort(expected);
        assertThat(bounded).hasSize(expected.length);
        for (int i = 0; i < expected.length; i++) {
            assertThat(bounded.get(i)).isEqualTo(expected[i]);
        }
    }
}
