package leaderrank.graph;

import java.io.IOException;

// Streams the in-neighbours of consecutive destinations back to back. The caller reads inDegree of
// them per destination before moving on.
public interface SourceCursor extends AutoCloseable {
    int next() throws IOException;

    @Override
    void close() throws IOException;
}
