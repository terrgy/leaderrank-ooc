package leaderrank.graph;


import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

public interface EdgeSource {
    EdgeCursor open() throws IOException;

    default Optional<Path> parallelFile() {
        return Optional.empty();
    }
}
