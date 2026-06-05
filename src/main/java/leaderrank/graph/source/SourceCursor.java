package leaderrank.graph.source;

import java.io.IOException;

public interface SourceCursor extends AutoCloseable {
    int next() throws IOException;

    @Override
    void close() throws IOException;
}
