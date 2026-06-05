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

public final class CsvEdgeSource implements EdgeSource {
    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setTrim(true)
            .setIgnoreEmptyLines(true)
            .build();

    private final ReaderFactory readerFactory;

    public CsvEdgeSource(Path path) {
        this(() -> Files.newBufferedReader(path, StandardCharsets.UTF_8));
    }

    public CsvEdgeSource(ReaderFactory readerFactory) {
        this.readerFactory = readerFactory;
    }

    @Override
    public EdgeCursor open() throws IOException {
        Reader reader = readerFactory.open();
        return new CsvEdgeCursor(CSVParser.parse(reader, FORMAT));
    }
}
