package leaderrank.io;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import leaderrank.graph.Graph;
import leaderrank.graph.IdMapper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

public final class EdgeListReader {

    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setTrim(true)
            .setIgnoreEmptyLines(true)
            .build();

    public Graph read(Path path) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return read(reader);
        }
    }

    public Graph read(Reader source) throws IOException {
        IdMapper mapper = new IdMapper();
        List<Integer> sources = new ArrayList<>();
        List<Integer> targets = new ArrayList<>();

        try (CSVParser parser = CSVParser.parse(source, FORMAT)) {
            for (CSVRecord record : parser) {
                if (record.size() < 2) {
                    throw new IOException(
                            "row " + record.getRecordNumber() + ": expected 'from,to', got: " + record);
                }
                int from;
                int to;
                try {
                    from = Integer.parseInt(record.get(0));
                    to = Integer.parseInt(record.get(1));
                } catch (NumberFormatException e) {
                    throw new IOException(
                            "row " + record.getRecordNumber() + ": vertex ids must be int32, got: " + record, e);
                }
                sources.add(mapper.mapOrAssign(from));
                targets.add(mapper.mapOrAssign(to));
            }
        }

        int vertexCount = mapper.size();
        int[] sourceIds = sources.stream().mapToInt(Integer::intValue).toArray();
        int[] targetIds = targets.stream().mapToInt(Integer::intValue).toArray();
        int[] outDegrees = new int[vertexCount];
        for (int sourceId : sourceIds) {
            outDegrees[sourceId]++;
        }
        return new Graph(vertexCount, sourceIds, targetIds, outDegrees, mapper.originalIds());
    }
}
