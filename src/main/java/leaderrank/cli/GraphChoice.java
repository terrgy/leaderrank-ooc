package leaderrank.cli;

import java.util.Iterator;
import java.util.List;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

enum GraphChoice {
    IN_MEMORY("in-memory"),
    OUT_OF_CORE("out-of-core");

    private final String label;

    GraphChoice(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }

    static GraphChoice fromLabel(String value) {
        for (GraphChoice choice : values()) {
            if (choice.label.equalsIgnoreCase(value)) {
                return choice;
            }
        }
        throw new TypeConversionException("expected one of in-memory, out-of-core but was '" + value + "'");
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
            return List.of("in-memory", "out-of-core").iterator();
        }
    }
}
