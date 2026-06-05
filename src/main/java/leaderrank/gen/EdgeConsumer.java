package leaderrank.gen;

import java.io.IOException;

@FunctionalInterface
public interface EdgeConsumer {

    void accept(int from, int to) throws IOException;
}
