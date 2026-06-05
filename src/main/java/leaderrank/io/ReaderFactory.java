package leaderrank.io;

import java.io.IOException;
import java.io.Reader;

@FunctionalInterface
public interface ReaderFactory {
    Reader open() throws IOException;
}
