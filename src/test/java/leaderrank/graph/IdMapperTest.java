package leaderrank.graph;

import static org.assertj.core.api.Assertions.assertThat;

import leaderrank.utils.IdMapper;
import org.junit.jupiter.api.Test;

class IdMapperTest {

    @Test
    void assignsDenseIdsInFirstAppearanceOrder() {
        IdMapper mapper = new IdMapper();

        assertThat(mapper.mapOrAssign(10)).isEqualTo(0);
        assertThat(mapper.mapOrAssign(20)).isEqualTo(1);
        assertThat(mapper.mapOrAssign(30)).isEqualTo(2);
    }

    @Test
    void returnsSameDenseIdForRepeatedOriginal() {
        IdMapper mapper = new IdMapper();
        int first = mapper.mapOrAssign(99);

        assertThat(mapper.mapOrAssign(99)).isEqualTo(first);
        assertThat(mapper.size()).isEqualTo(1);
    }

    @Test
    void handlesSparseAndNegativeIds() {
        IdMapper mapper = new IdMapper();
        mapper.mapOrAssign(-5);
        mapper.mapOrAssign(1_000_000);
        mapper.mapOrAssign(-5);

        assertThat(mapper.size()).isEqualTo(2);
        assertThat(mapper.originalIds()).containsExactly(-5, 1_000_000);
    }

    @Test
    void reverseMappingMatchesAssignment() {
        IdMapper mapper = new IdMapper();
        mapper.mapOrAssign(42);
        mapper.mapOrAssign(7);

        int[] originals = mapper.originalIds();
        assertThat(originals[mapper.mapOrAssign(42)]).isEqualTo(42);
        assertThat(originals[mapper.mapOrAssign(7)]).isEqualTo(7);
    }
}
