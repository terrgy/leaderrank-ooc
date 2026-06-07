package leaderrank.graph.outofcore.build;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MemoryBudgetTest {

    @Test
    void largerBudgetAllowsMoreEdgesPerBin() {
        int small = new MemoryBudget(128L << 20).maxEdgesPerBin(1_000);
        int large = new MemoryBudget(512L << 20).maxEdgesPerBin(1_000);
        assertThat(large).isGreaterThan(small);
    }

    @Test
    void moreVerticesShrinkEdgesPerBin() {
        MemoryBudget budget = new MemoryBudget(256L << 20);
        assertThat(budget.maxEdgesPerBin(10_000_000)).isLessThan(budget.maxEdgesPerBin(1_000));
    }

    @Test
    void neverDropsBelowOne() {
        assertThat(new MemoryBudget(1).maxEdgesPerBin(1_000_000)).isEqualTo(1);
    }

    @Test
    void rejectsNonPositiveBudget() {
        assertThatThrownBy(() -> new MemoryBudget(0)).isInstanceOf(IllegalArgumentException.class);
    }
}
