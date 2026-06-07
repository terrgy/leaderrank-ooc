package leaderrank.cli;

import java.util.Iterator;
import picocli.CommandLine.ITypeConverter;

enum GraphChoice implements Choices.Labelled {
    IN_MEMORY("in-memory"),
    OUT_OF_CORE("out-of-core");

    private final String label;

    GraphChoice(String label) {
        this.label = label;
    }

    @Override
    public String label() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }

    static GraphChoice fromLabel(String value) {
        return Choices.parse(values(), value);
    }

    static final class Converter implements ITypeConverter<GraphChoice> {
        @Override
        public GraphChoice convert(String value) {
            return fromLabel(value);
        }
    }

    static final class Candidates implements Iterable<String> {
        @Override
        public Iterator<String> iterator() {
            return Choices.labels(values()).iterator();
        }
    }
}
