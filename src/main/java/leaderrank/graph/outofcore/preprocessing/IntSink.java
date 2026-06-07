package leaderrank.graph.outofcore.preprocessing;

import java.io.IOException;

@FunctionalInterface
interface IntSink {
    void accept(int value) throws IOException;
}
