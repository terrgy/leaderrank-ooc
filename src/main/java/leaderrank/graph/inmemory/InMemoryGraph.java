package leaderrank.graph.inmemory;

import leaderrank.graph.Graph;
import leaderrank.graph.IdMapper;
import leaderrank.graph.edge.EdgeCursor;
import leaderrank.graph.edge.EdgeSource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.PrimitiveIterator;

public final class InMemoryGraph implements Graph {
    private int edgeCount;
    private int vertexCount;
    private final int[] originalIds;
    private final ArrayList<Integer> outDegrees;
    private final ArrayList<ArrayList<Integer>> sources;

    private InMemoryGraph(EdgeCursor edges) throws IOException {
        outDegrees = new ArrayList<>();
        sources = new ArrayList<>();
        vertexCount = 0;
        edgeCount = 0;

        IdMapper mapper = new IdMapper();
        while (edges.next()) {
            addEdge(mapper, edges.from(), edges.to());
            ++edgeCount;
        }
        originalIds = mapper.originalIds();

        for (ArrayList<Integer> sourcesForVertex : sources) {
            sourcesForVertex.sort(Integer::compare);
        }
    }

    public static Graph build(EdgeSource source) throws IOException {
        try (EdgeCursor cursor = source.open()) {
            return new InMemoryGraph(cursor);
        }
    }

    private void growTo(int newSize) {
        while (vertexCount < newSize) {
            outDegrees.add(0);
            sources.add(new ArrayList<>());
            vertexCount++;
        }
    }

    private void addEdge(IdMapper mapper, int originalFrom, int originalTo) {
        int denseFrom = mapper.mapOrAssign(originalFrom);
        int denseTo = mapper.mapOrAssign(originalTo);

        growTo(mapper.size());

        outDegrees.set(denseFrom, outDegrees.get(denseFrom) + 1);
        sources.get(denseTo).add(denseFrom);
    }

    @Override
    public int vertexCount() {
        return vertexCount;
    }

    @Override
    public int edgeCount() {
        return edgeCount;
    }

    @Override
    public int outDegree(int denseId) {
        return outDegrees.get(denseId);
    }

    @Override
    public int originalId(int denseId) {
        return originalIds[denseId];
    }

    @Override
    public PrimitiveIterator.OfInt sourcesOf(int destinationDenseId) {
        return sources.get(destinationDenseId).stream().mapToInt(Integer::intValue).iterator();
    }
}
