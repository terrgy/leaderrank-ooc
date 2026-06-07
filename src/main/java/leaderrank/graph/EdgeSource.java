package leaderrank.graph;


import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

public interface EdgeSource {
    EdgeCursor open() throws IOException;

    // Present when the source is a plain file that can be split into byte ranges for parallel parsing.
    default Optional<Path> parallelFile() {
        return Optional.empty();
    }
}
