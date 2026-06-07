package leaderrank.graph.outofcore.preprocessing;

import java.util.ArrayList;
import java.util.List;

public final class BinPlanner {

    private BinPlanner() {
    }

    public static List<Bin> plan(int[] sourcesPtr, long maxEdgesPerBin) {
        if (maxEdgesPerBin < 1) {
            throw new IllegalArgumentException("maxEdgesPerBin must be at least 1");
        }

        int vertexCount = sourcesPtr.length - 1;
        List<Bin> bins = new ArrayList<>();
        int begin = 0;
        long accumulated = 0;

        for (int destination = 0; destination < vertexCount; destination++) {
            long inDegree = sourcesPtr[destination + 1] - sourcesPtr[destination];
            if (inDegree > maxEdgesPerBin) {
                if (destination > begin) {
                    bins.add(new Bin(begin, destination, accumulated, false));
                }
                bins.add(new Bin(destination, destination + 1, inDegree, true));
                begin = destination + 1;
                accumulated = 0;
            } else if (accumulated + inDegree > maxEdgesPerBin) {
                bins.add(new Bin(begin, destination, accumulated, false));
                begin = destination;
                accumulated = inDegree;
            } else {
                accumulated += inDegree;
            }
        }

        if (begin < vertexCount) {
            bins.add(new Bin(begin, vertexCount, accumulated, false));
        }
        return bins;
    }
}
