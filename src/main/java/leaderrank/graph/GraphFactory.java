package leaderrank.graph;


import java.io.IOException;

@FunctionalInterface
public interface GraphFactory {
    Graph create(EdgeSource source) throws IOException;
}