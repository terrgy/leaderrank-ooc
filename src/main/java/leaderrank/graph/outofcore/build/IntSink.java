package leaderrank.graph.outofcore.build;

import java.io.IOException;

@FunctionalInterface
interface IntSink {
    void accept(int value) throws IOException;
}
