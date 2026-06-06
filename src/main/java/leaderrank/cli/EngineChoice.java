package leaderrank.cli;

import java.util.Iterator;
import java.util.List;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

enum EngineChoice {
    DENSE("dense"),
    COMMON("common"),
    PARALLEL("parallel");

    private final String label;

    EngineChoice(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }

    static EngineChoice fromLabel(String value) {
        for (EngineChoice choice : values()) {
            if (choice.label.equalsIgnoreCase(value)) {
                return choice;
            }
        }
        throw new TypeConversionException("expected one of dense, common, parallel but was '" + value + "'");
    }

    static final class Converter implements ITypeConverter<EngineChoice> {
        @Override
        public EngineChoice convert(String value) {
            return fromLabel(value);
        }
    }

    static final class Candidates implements Iterable<String> {
        @Override
        public Iterator<String> iterator() {
            return List.of("dense", "common", "parallel").iterator();
        }
    }
}
