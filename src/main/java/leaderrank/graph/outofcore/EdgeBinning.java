package leaderrank.graph.outofcore;

import leaderrank.graph.edge.EdgeSource;

import java.io.IOException;
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
        Pass1Result pass1 = OutOfCoreGraphPreprocessor.pass1(source);
        BinFiles files = BinFiles.create(BinPlanner.plan(pass1.sourcesPtr(), maxEdgesPerBin));
        try {
            files.distribute(source, pass1.mapper());
        } catch (IOException | RuntimeException e) {
            files.close();
            throw e;
        }
        return files;
    }
}
