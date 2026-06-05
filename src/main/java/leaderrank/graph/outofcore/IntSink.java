package leaderrank.graph.outofcore;

import java.io.IOException;

@FunctionalInterface
interface IntSink {
    void accept(int value) throws IOException;
}
