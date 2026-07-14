package com.cdata.embeddeddrivers.cli.commands;

import java.util.concurrent.Callable;

import com.cdata.embeddeddrivers.core.BuildNumbers;
import com.cdata.embeddeddrivers.core.Changelog;
import com.cdata.embeddeddrivers.core.Edition;
import com.cdata.embeddeddrivers.core.OemBuildsClient;

import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "changelog",
        description = "Show changelog entries for a connector since a release, date, or build.")
public class ChangelogCommand implements Callable<Integer> {

    @Option(names = {"-e", "--edition"}, required = true, converter = EditionConverter.class,
            description = "Driver edition: JDBC, ADO-NET-FRAMEWORK, ADO-NET-STANDARD, ODBC-UNIX, "
                    + "ODBC-WINDOWS, PYTHON-MAC, PYTHON-UNIX, PYTHON-WINDOWS.")
    Edition edition;

    @Option(names = {"-c", "--connector"}, required = true,
            description = "Connector name (e.g. Salesforce). Run 'cdrm connectors' to list valid names.")
    String connector;

    @Option(names = {"-v", "--major-version"}, required = true,
            description = "Major version year (e.g. 2025). Each major version has its own changelog.")
    int majorVersion;

    @ArgGroup(multiplicity = "1")
    Baseline baseline;

    static class Baseline {
        @Option(names = "--after-release",
                description = "Show entries after this U-number (e.g. 2 for U2).")
        Integer afterRelease;

        @Option(names = "--after-date",
                description = "Show entries after this date (YYYY-MM-DD).")
        String afterDate;

        @Option(names = "--after-build",
                description = "Show entries after this build number.")
        Integer afterBuild;
    }

    @Override
    public Integer call() throws Exception {
        OemBuildsClient client = new OemBuildsClient();

        int baselineBuild;
        try {
            if (baseline.afterDate != null) {
                baselineBuild = BuildNumbers.fromDate(baseline.afterDate);
            } else if (baseline.afterBuild != null) {
                if (baseline.afterBuild < 1) {
                    System.err.println("--after-build must be a positive build number.");
                    return 2;
                }
                baselineBuild = baseline.afterBuild;
            } else {
                if (baseline.afterRelease < 1) {
                    System.err.println("--after-release must be >= 1 (the U-number, e.g. 2 for U2).");
                    return 2;
                }
                baselineBuild = client.releaseToBuildNumber(majorVersion, baseline.afterRelease, edition, connector);
            }
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.err.println("Run 'cdrm connectors' to see valid connector names, or 'cdrm releases' for releases.");
            return 1;
        }

        String csv = client.fetchChangelogCsv(edition, majorVersion, connector);
        if (csv == null) {
            System.err.println("No changelog found for '" + connector + "' (" + edition.displayName()
                    + "). Run 'cdrm connectors' to see valid connector names.");
            return 1;
        }

        Changelog.Filtered result = Changelog.filterAfterBuild(csv, baselineBuild);
        if (result.entries().isEmpty()) {
            System.out.println("No changelog entries after build " + baselineBuild
                    + " for '" + connector + "' in major version " + majorVersion + ".");
            return 0;
        }

        System.out.printf("Changelog: %s (%s) v%d - %d entr%s after build %d%n%n",
                connector, edition.displayName(), majorVersion,
                result.entries().size(), result.entries().size() == 1 ? "y" : "ies", baselineBuild);
        System.out.println(result.header());
        for (String line : result.entries()) {
            System.out.println(line);
        }
        return 0;
    }
}
