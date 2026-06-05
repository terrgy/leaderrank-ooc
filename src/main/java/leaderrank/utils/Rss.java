package leaderrank.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Rss {

    private static final Path STATUS = Path.of("/proc/self/status");

    private Rss() {
    }

    public static long peakResidentBytes() {
        try {
            for (String line : Files.readAllLines(STATUS)) {
                if (line.startsWith("VmHWM:")) {
                    String[] parts = line.trim().split("\\s+");
                    return Long.parseLong(parts[1]) * 1024;
                }
            }
        } catch (IOException | RuntimeException ignored) {
            return -1;
        }
        return -1;
    }
}
