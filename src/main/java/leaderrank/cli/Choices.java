package leaderrank.cli;

import java.util.Arrays;
import java.util.List;
import picocli.CommandLine.TypeConversionException;

// Shared parsing for the small label-backed enums. Keeps the accepted tokens, the completion list and
// the error message in one place, all derived from the enum values.
final class Choices {

    private Choices() {
    }

    interface Labelled {
        String label();
    }

    static <E extends Enum<E> & Labelled> E parse(E[] values, String value) {
        for (E choice : values) {
            if (choice.label().equalsIgnoreCase(value)) {
                return choice;
            }
        }
        throw new TypeConversionException(
                "expected one of " + String.join(", ", labels(values)) + " but was '" + value + "'");
    }

    static <E extends Enum<E> & Labelled> List<String> labels(E[] values) {
        return Arrays.stream(values).map(Labelled::label).toList();
    }
}
