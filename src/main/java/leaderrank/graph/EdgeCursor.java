package leaderrank.graph;

import java.io.IOException;

public interface EdgeCursor extends AutoCloseable {
    boolean next() throws IOException;
    int from();
    int to();
    @Override void close() throws IOException;
}