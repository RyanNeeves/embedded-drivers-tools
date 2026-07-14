package com.cdata.embeddeddrivers.cli;

import com.cdata.embeddeddrivers.cli.commands.ChangelogCommand;
import com.cdata.embeddeddrivers.cli.commands.ConnectorsCommand;
import com.cdata.embeddeddrivers.cli.commands.DownloadCommand;
import com.cdata.embeddeddrivers.cli.commands.ReleasesCommand;

import picocli.CommandLine.Command;

@Command(
        name = "cdrm",
        description = "CData Release Manager - manage releases of CData embedded/OEM drivers.",
        mixinStandardHelpOptions = true,
        versionProvider = VersionProvider.class,
        subcommands = {
                ReleasesCommand.class,
                ConnectorsCommand.class,
                ChangelogCommand.class,
                DownloadCommand.class,
        })
public class RootCommand implements Runnable {

    @Override
    public void run() {
        // No subcommand given: show usage.
        new picocli.CommandLine(this).usage(System.out);
    }
}
