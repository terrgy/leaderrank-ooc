package leaderrank.graph.outofcore.preprocessing;

import java.io.IOException;

// Receives sorted ints from a merge, either the final sources writer or an intermediate run file.
@FunctionalInterface
interface IntSink {
    void accept(int value) throws IOException;
}
