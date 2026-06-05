package leaderrank.graph.edge;


import java.io.IOException;

public interface EdgeSource {
    EdgeCursor open() throws IOException;
}
