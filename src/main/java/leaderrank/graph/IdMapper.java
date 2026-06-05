package leaderrank.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// TODO: rewrite in something more lightweight
public final class IdMapper {

    private final Map<Integer, Integer> denseByOriginal = new HashMap<>();
    private final List<Integer> originalByDense = new ArrayList<>();

    public int mapOrAssign(int originalId) {
        Integer existing = denseByOriginal.get(originalId);
        if (existing != null) {
            return existing;
        }
        int assigned = originalByDense.size();
        denseByOriginal.put(originalId, assigned);
        originalByDense.add(originalId);
        return assigned;
    }

    public int denseOf(int originalId) {
        Integer dense = denseByOriginal.get(originalId);
        if (dense == null) throw new IllegalStateException("unmapped id " + originalId);
        return dense;
    }

    public int size() {
        return originalByDense.size();
    }

    public int[] originalIds() {
        return originalByDense.stream().mapToInt(Integer::intValue).toArray();
    }
}
