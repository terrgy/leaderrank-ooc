package leaderrank.graph;

public final class InMemoryVertexSources implements VertexSources {
    private final int[] sources;
    private final int[] targets;
    private final int target;
    private int curIdx;

    InMemoryVertexSources(int[] sources, int[] targets, int destinationDenseId) {
        this.sources = sources;
        this.targets = targets;

        target = destinationDenseId;
        curIdx = 0;
    }

    private void searchForNextEntry() {
        while ((curIdx < targets.length) && (targets[curIdx] != target)) {
            ++curIdx;
        }
    }

    @Override
    public int getNextSource() {
        searchForNextEntry();
        if (curIdx >= targets.length) {
            return -1;
        }
        return sources[curIdx++];
    }

    @Override
    public boolean isEnd() {
        searchForNextEntry();
        return curIdx >= targets.length;
    }
}
