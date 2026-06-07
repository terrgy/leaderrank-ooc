package leaderrank.graph;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;

// Maps sparse int32 vertex ids to a dense 0..n-1 range in first-appearance order. Every array in the
// engine is indexed by that dense id.
public final class IdMapper {

    private static final int ABSENT = -1;

    private final Int2IntOpenHashMap denseByOriginal = new Int2IntOpenHashMap();
    private final IntArrayList originalByDense = new IntArrayList();

    public IdMapper() {
        denseByOriginal.defaultReturnValue(ABSENT);
    }

    public int mapOrAssign(int originalId) {
        int existing = denseByOriginal.get(originalId);
        if (existing != ABSENT) {
            return existing;
        }
        int assigned = originalByDense.size();
        denseByOriginal.put(originalId, assigned);
        originalByDense.add(originalId);
        return assigned;
    }

    public int denseOf(int originalId) {
        int dense = denseByOriginal.get(originalId);
        if (dense == ABSENT) {
            throw new IllegalStateException("unmapped id " + originalId);
        }
        return dense;
    }

    public int size() {
        return originalByDense.size();
    }

    public int[] originalIds() {
        return originalByDense.toIntArray();
    }
}
