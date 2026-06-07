package leaderrank.cli;

import java.util.Iterator;
import picocli.CommandLine.ITypeConverter;

enum EngineChoice implements Choices.Labelled {
    DENSE("dense"),
    COMMON("common"),
    PARALLEL("parallel");

    private final String label;

    EngineChoice(String label) {
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

    static EngineChoice fromLabel(String value) {
        return Choices.parse(values(), value);
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
            return Choices.labels(values()).iterator();
        }
    }
}
