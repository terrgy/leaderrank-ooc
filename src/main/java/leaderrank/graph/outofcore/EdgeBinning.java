package leaderrank.graph.outofcore;

import leaderrank.graph.Shard;
import leaderrank.graph.ShardPlanner;
import leaderrank.graph.edge.EdgeSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class EdgeBinning {

    private EdgeBinning() {
    }

    public static List<Bin> plan(EdgeSource source, long maxEdgesPerBin) throws IOException {
        return BinPlanner.plan(OutOfCoreGraphPreprocessor.pass1(source).sourcesPtr(), maxEdgesPerBin);
    }

    public static List<Shard> shards(EdgeSource source, int shardCount) throws IOException {
        return ShardPlanner.plan(OutOfCoreGraphPreprocessor.pass1(source).sourcesPtr(), shardCount);
    }

    public static BinFiles bin(EdgeSource source, long maxEdgesPerBin) throws IOException {
        Path denseEdges = Files.createTempFile("leaderrank-edges-", ".bin");
        denseEdges.toFile().deleteOnExit();
        try {
            Pass1Result pass1 = OutOfCoreGraphPreprocessor.buildIdMapAndSpill(source, denseEdges);
            BinFiles files = BinFiles.create(BinPlanner.plan(pass1.sourcesPtr(), maxEdgesPerBin));
            try {
                files.distribute(denseEdges);
            } catch (IOException | RuntimeException e) {
                files.close();
                throw e;
            }
            return files;
        } finally {
            Files.deleteIfExists(denseEdges);
        }
    }
}
