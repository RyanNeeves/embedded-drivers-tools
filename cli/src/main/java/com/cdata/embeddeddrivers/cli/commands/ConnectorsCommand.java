package com.cdata.embeddeddrivers.cli.commands;

import java.util.List;
import java.util.concurrent.Callable;

import com.cdata.embeddeddrivers.core.Edition;
import com.cdata.embeddeddrivers.core.OemBuildsClient;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "connectors",
        description = "List the connectors available for an edition and major version.")
public class ConnectorsCommand implements Callable<Integer> {

    @Option(names = {"-e", "--edition"}, required = true,
            description = "Driver edition: JDBC, ADO-NET-FRAMEWORK, ADO-NET-STANDARD, ODBC-UNIX, "
                    + "ODBC-WINDOWS, PYTHON-MAC, PYTHON-UNIX, PYTHON-WINDOWS.")
    Edition edition;

    @Option(names = {"-v", "--major-version"}, required = true,
            description = "Major version year (e.g. 2025). Run 'cdrm releases' to see available versions.")
    int majorVersion;

    @Override
    public Integer call() throws Exception {
        OemBuildsClient client = new OemBuildsClient();
        List<String> connectors = client.listConnectors(edition, majorVersion);

        if (connectors.isEmpty()) {
            if (!client.majorVersionExists(majorVersion)) {
                System.err.println("Major version " + majorVersion
                        + " does not exist. Run 'cdrm releases' to see available versions.");
                return 1;
            }
            System.out.println("No connectors found for " + edition.displayName()
                    + " in major version " + majorVersion + ".");
            return 0;
        }

        System.out.printf("%d connector%s available for %s in major version %d:%n",
                connectors.size(), connectors.size() == 1 ? "" : "s", edition.displayName(), majorVersion);
        for (String name : connectors) {
            System.out.println("  " + name);
        }
        return 0;
    }
}
