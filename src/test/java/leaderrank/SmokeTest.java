package leaderrank;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SmokeTest {

    @Test
    void runsOnJava21OrNewer() {
        assertThat(Runtime.version().feature())
                .as("LeaderRank targets Java 21")
                .isGreaterThanOrEqualTo(21);
    }
}
