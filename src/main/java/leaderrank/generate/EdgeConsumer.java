package leaderrank.generate;

import java.io.IOException;

@FunctionalInterface
public interface EdgeConsumer {

    void accept(int from, int to) throws IOException;
}
