package leaderrank.graph.outofcore;

import java.util.ArrayList;
import java.util.List;

public final class ShardPlanner {

    private ShardPlanner() {
    }

    public static List<Shard> plan(int[] sourcesPtr, int shardCount) {
        if (shardCount < 1) {
            throw new IllegalArgumentException("shardCount must be at least 1");
        }

        int vertexCount = sourcesPtr.length - 1;
        List<Shard> shards = new ArrayList<>();
        if (vertexCount == 0) {
            return shards;
        }

        long edges = sourcesPtr[vertexCount];
        long target = Math.max(1, edges / shardCount);
        int begin = 0;
        long accumulated = 0;

        for (int destination = 0; destination < vertexCount; destination++) {
            accumulated += sourcesPtr[destination + 1] - sourcesPtr[destination];
            if (shards.size() < shardCount - 1 && accumulated >= target) {
                shards.add(new Shard(begin, destination + 1, accumulated));
                begin = destination + 1;
                accumulated = 0;
            }
        }

        if (begin < vertexCount) {
            shards.add(new Shard(begin, vertexCount, edges - sourcesPtr[begin]));
        }
        return shards;
    }
}
