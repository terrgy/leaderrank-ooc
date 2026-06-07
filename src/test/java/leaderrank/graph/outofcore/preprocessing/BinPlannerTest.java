package leaderrank.graph.outofcore.preprocessing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class BinPlannerTest {

    private static int[] sourcesPtr(int... inDegrees) {
        int[] ptr = new int[inDegrees.length + 1];
        for (int i = 0; i < inDegrees.length; i++) {
            ptr[i + 1] = ptr[i] + inDegrees[i];
        }
        return ptr;
    }

    @Test
    void partitionsEveryDestinationExactlyOnce() {
        List<Bin> bins = BinPlanner.plan(sourcesPtr(2, 2, 2, 2, 2), 4);

        assertThat(bins.get(0).begin()).isZero();
        assertThat(bins.get(bins.size() - 1).end()).isEqualTo(5);
        for (int i = 1; i < bins.size(); i++) {
            assertThat(bins.get(i).begin()).isEqualTo(bins.get(i - 1).end());
        }
    }

    @Test
    void normalBinsNeverExceedBudget() {
        long budget = 5;
        for (Bin bin : BinPlanner.plan(sourcesPtr(1, 3, 2, 4, 1, 1, 2), budget)) {
            if (!bin.oversized()) {
                assertThat(bin.edgeCount()).isLessThanOrEqualTo(budget);
            }
        }
    }

    @Test
    void edgeCountMatchesRangeSum() {
        int[] ptr = sourcesPtr(1, 3, 2, 4, 1, 1, 2);
        for (Bin bin : BinPlanner.plan(ptr, 5)) {
            assertThat(bin.edgeCount()).isEqualTo(ptr[bin.end()] - ptr[bin.begin()]);
        }
    }

    @Test
    void oversizedDestinationGetsOwnFlaggedBin() {
        List<Bin> bins = BinPlanner.plan(sourcesPtr(1, 10, 1), 4);

        Bin oversized = bins.stream().filter(Bin::oversized).findFirst().orElseThrow();
        assertThat(oversized.begin()).isEqualTo(1);
        assertThat(oversized.end()).isEqualTo(2);
        assertThat(oversized.edgeCount()).isEqualTo(10);
    }

    @Test
    void packsIntoSingleBinWhenBudgetExceedsTotal() {
        assertThat(BinPlanner.plan(sourcesPtr(1, 1, 1), 100)).singleElement().satisfies(bin -> {
            assertThat(bin.begin()).isZero();
            assertThat(bin.end()).isEqualTo(3);
            assertThat(bin.edgeCount()).isEqualTo(3);
            assertThat(bin.oversized()).isFalse();
        });
    }

    @Test
    void coversZeroInDegreeDestinations() {
        List<Bin> bins = BinPlanner.plan(sourcesPtr(0, 0, 3, 0), 2);

        assertThat(bins.get(0).begin()).isZero();
        assertThat(bins.get(bins.size() - 1).end()).isEqualTo(4);
        for (int i = 1; i < bins.size(); i++) {
            assertThat(bins.get(i).begin()).isEqualTo(bins.get(i - 1).end());
        }
        assertThat(bins.stream().mapToLong(Bin::edgeCount).sum()).isEqualTo(3);
    }

    @Test
    void everyEdgeAccountedForAcrossBins() {
        int[] ptr = sourcesPtr(2, 7, 1, 9, 3, 0, 4);
        long total = BinPlanner.plan(ptr, 5).stream().mapToLong(Bin::edgeCount).sum();
        assertThat(total).isEqualTo(ptr[ptr.length - 1]);
    }

    @Test
    void emptyGraphYieldsNoBins() {
        assertThat(BinPlanner.plan(sourcesPtr(), 4)).isEmpty();
    }

    @Test
    void rejectsNonPositiveBudget() {
        assertThatThrownBy(() -> BinPlanner.plan(sourcesPtr(1, 1), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
