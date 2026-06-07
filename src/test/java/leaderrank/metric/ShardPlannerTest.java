package leaderrank.metric;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class ShardPlannerTest {

    private static int[] sourcesPtr(int... inDegrees) {
        int[] ptr = new int[inDegrees.length + 1];
        for (int i = 0; i < inDegrees.length; i++) {
            ptr[i + 1] = ptr[i] + inDegrees[i];
        }
        return ptr;
    }

    private static int[] uniform(int vertexCount, int inDegree) {
        int[] inDegrees = new int[vertexCount];
        for (int i = 0; i < vertexCount; i++) {
            inDegrees[i] = inDegree;
        }
        return sourcesPtr(inDegrees);
    }

    @Test
    void partitionsDestinationsContiguously() {
        int[] ptr = uniform(100, 3);
        List<Shard> shards = ShardPlanner.plan(ptr, 8);

        assertThat(shards.get(0).begin()).isZero();
        assertThat(shards.get(shards.size() - 1).end()).isEqualTo(100);
        for (int i = 1; i < shards.size(); i++) {
            assertThat(shards.get(i).begin()).isEqualTo(shards.get(i - 1).end());
        }
    }

    @Test
    void producesAtMostRequestedShards() {
        assertThat(ShardPlanner.plan(uniform(100, 3), 8)).hasSizeLessThanOrEqualTo(8);
    }

    @Test
    void conservesEdges() {
        int[] ptr = sourcesPtr(2, 7, 1, 9, 3, 0, 4, 5);
        long total = ShardPlanner.plan(ptr, 4).stream().mapToLong(Shard::edgeCount).sum();
        assertThat(total).isEqualTo(ptr[ptr.length - 1]);
    }

    @Test
    void splitsEvenlyWhenDivisible() {
        List<Shard> shards = ShardPlanner.plan(uniform(100, 1), 10);
        assertThat(shards).hasSize(10);
        assertThat(shards).allSatisfy(shard -> assertThat(shard.edgeCount()).isEqualTo(10));
    }

    @Test
    void coversHyperNodeWithoutSplittingIt() {
        int[] ptr = sourcesPtr(1, 1, 5000, 1, 1, 1, 1, 1);
        List<Shard> shards = ShardPlanner.plan(ptr, 4);

        assertThat(shards.get(0).begin()).isZero();
        assertThat(shards.get(shards.size() - 1).end()).isEqualTo(8);
        assertThat(shards).anySatisfy(shard ->
                assertThat(shard.begin()).isLessThanOrEqualTo(2).isLessThan(shard.end()));
        assertThat(shards.stream().mapToLong(Shard::edgeCount).sum()).isEqualTo(ptr[ptr.length - 1]);
    }

    @Test
    void singleShardWhenCountIsOne() {
        assertThat(ShardPlanner.plan(uniform(50, 2), 1)).singleElement().satisfies(shard -> {
            assertThat(shard.begin()).isZero();
            assertThat(shard.end()).isEqualTo(50);
            assertThat(shard.edgeCount()).isEqualTo(100);
        });
    }

    @Test
    void zeroEdgesYieldOneCoveringShard() {
        assertThat(ShardPlanner.plan(uniform(20, 0), 8)).singleElement().satisfies(shard -> {
            assertThat(shard.begin()).isZero();
            assertThat(shard.end()).isEqualTo(20);
            assertThat(shard.edgeCount()).isZero();
        });
    }

    @Test
    void emptyGraphYieldsNoShards() {
        assertThat(ShardPlanner.plan(sourcesPtr(), 8)).isEmpty();
    }

    @Test
    void rejectsNonPositiveShardCount() {
        assertThatThrownBy(() -> ShardPlanner.plan(uniform(10, 1), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
