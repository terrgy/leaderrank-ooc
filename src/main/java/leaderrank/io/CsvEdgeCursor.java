package leaderrank.io;

import leaderrank.graph.EdgeCursor;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.util.Iterator;

final class CsvEdgeCursor implements EdgeCursor {

    private final CSVParser parser;
    private final Iterator<CSVRecord> iterator;
    private int from;
    private int to;

    CsvEdgeCursor(CSVParser parser) {
        this.parser = parser;
        iterator = parser.iterator();
    }

    @Override
    public boolean next() throws IOException {
        if (!iterator.hasNext()) {
            return false;
        }
        CSVRecord record = iterator.next();
        if (record.size() < 2) {
            throw new IOException(
                    "row " + record.getRecordNumber() + ": expected 'from,to', got: " + record);
        }
        try {
            from = Integer.parseInt(record.get(0));
            to = Integer.parseInt(record.get(1));
        } catch (NumberFormatException e) {
            throw new IOException(
                    "row " + record.getRecordNumber() + ": vertex ids must be int32, got: " + record, e);
        }
        return true;
    }

    @Override
    public int from() {
        return from;
    }

    @Override
    public int to() {
        return to;
    }

    @Override
    public void close() throws IOException {
        parser.close();
    }
}
