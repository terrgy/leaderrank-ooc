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
        this.iterator = parser.iterator();
    }

    @Override
    public boolean next() throws IOException {
        if (!iterator.hasNext()) {
            return false;
        }
        CSVRecord record = iterator.next();
        requireTwoColumns(record);
        from = parseVertex(record, 0);
        to = parseVertex(record, 1);
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

    private static void requireTwoColumns(CSVRecord record) throws IOException {
        if (record.size() < 2) {
            throw new IOException(rowError(record, "expected 'from,to'"));
        }
    }

    private static int parseVertex(CSVRecord record, int column) throws IOException {
        try {
            return Integer.parseInt(record.get(column));
        } catch (NumberFormatException e) {
            throw new IOException(rowError(record, "vertex ids must be int32"), e);
        }
    }

    private static String rowError(CSVRecord record, String detail) {
        return "row " + record.getRecordNumber() + ": " + detail + ", got: " + record;
    }
}
