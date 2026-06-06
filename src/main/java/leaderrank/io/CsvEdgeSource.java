package leaderrank.io;

import leaderrank.graph.edge.EdgeCursor;
import leaderrank.graph.edge.EdgeSource;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public final class CsvEdgeSource implements EdgeSource {
    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setTrim(true)
            .setIgnoreEmptyLines(true)
            .build();

    private final ReaderFactory readerFactory;
    private final Path file;

    public CsvEdgeSource(Path path) {
        this.readerFactory = () -> Files.newBufferedReader(path, StandardCharsets.UTF_8);
        this.file = path;
    }

    public CsvEdgeSource(ReaderFactory readerFactory) {
        this.readerFactory = readerFactory;
        this.file = null;
    }

    @Override
    public EdgeCursor open() throws IOException {
        Reader reader = readerFactory.open();
        return new CsvEdgeCursor(CSVParser.parse(reader, FORMAT));
    }

    @Override
    public Optional<Path> parallelFile() {
        return Optional.ofNullable(file);
    }
}
