package leaderrank.cli;

import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Command(name = "leaderrank",
        mixinStandardHelpOptions = true,
        version = "leaderrank 0.1.0",
        description = "Out-of-core LeaderRank centrality for directed unweighted graphs.",
        subcommands = {RankCommand.class, VerifyCommand.class, GenerateCommand.class})
final class LeaderRankCommand implements Callable<Integer> {

    @Spec
    private CommandSpec spec;

    @Override
    public Integer call() {
        spec.commandLine().usage(System.out);
        return 0;
    }
}
