package leaderrank.graph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    public int size() {
        return originalByDense.size();
    }

    public int[] originalIds() {
        return originalByDense.stream().mapToInt(Integer::intValue).toArray();
    }
}
