package leaderrank.cli;

import picocli.CommandLine;

public final class Main {

    private Main() {
    }

    static CommandLine commandLine() {
        return new CommandLine(new LeaderRankCommand())
                .setCaseInsensitiveEnumValuesAllowed(true);
    }

    public static void main(String[] args) {
        System.exit(commandLine().execute(args));
    }
}
