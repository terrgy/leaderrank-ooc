package leaderrank.graph;

import leaderrank.graph.edge.EdgeSource;

import java.io.IOException;

@FunctionalInterface
public interface GraphFactory {
    Graph create(EdgeSource source) throws IOException;
}