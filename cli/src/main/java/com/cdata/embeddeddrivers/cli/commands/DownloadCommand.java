package com.cdata.embeddeddrivers.cli.commands;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import com.cdata.embeddeddrivers.core.Edition;
import com.cdata.embeddeddrivers.core.OemBuildsClient;
import com.cdata.embeddeddrivers.core.Release;
import com.cdata.embeddeddrivers.core.RemoteFile;

import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(
        name = "download",
        description = "Download driver builds for a release, either single connectors or the full edition.")
public class DownloadCommand implements Callable<Integer> {

    @Option(names = {"-e", "--edition"}, required = true, converter = EditionConverter.class,
            description = "Driver edition: JDBC, ADO-NET-FRAMEWORK, ADO-NET-STANDARD, ODBC-UNIX, "
                    + "ODBC-WINDOWS, PYTHON-MAC, PYTHON-UNIX, PYTHON-WINDOWS.")
    Edition edition;

    @Option(names = {"-r", "--release"}, required = true,
            description = "Release to download from: '2025u3', 'v25u3', or 'latest'.")
    String release;

    @ArgGroup(multiplicity = "1")
    Selection selection;

    static class Selection {
        @Option(names = {"-c", "--connector"},
                description = "Connector name to download (repeatable, e.g. -c Salesforce -c MySQL).")
        List<String> connectors;

        @Option(names = "--all",
                description = "Download every driver in the edition (bulk).")
        boolean all;
    }

    @Option(names = {"-o", "--output"}, defaultValue = ".",
            description = "Output directory (default: current directory).")
    Path output;

    @Option(names = {"-p", "--parallel"}, defaultValue = "4",
            description = "Number of concurrent downloads, 1-16 (default: ${DEFAULT-VALUE}).")
    int parallel;

    @Override
    public Integer call() throws Exception {
        if (parallel < 1 || parallel > 16) {
            System.err.println("--parallel must be between 1 and 16.");
            return 2;
        }

        OemBuildsClient client = new OemBuildsClient();

        Release rel;
        try {
            if ("latest".equalsIgnoreCase(release.trim())) {
                rel = client.latestRelease();
            } else {
                rel = Release.parse(release);
                client.requireRelease(rel);
            }
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            return 1;
        }

        List<RemoteFile> available = client.listDriverFiles(rel, edition);
        if (available.isEmpty()) {
            System.err.println("No driver builds found for " + edition.displayName() + " in " + rel.label() + ".");
            return 1;
        }

        List<RemoteFile> toDownload;
        if (selection.all) {
            toDownload = available;
        } else {
            // Keyed by bucket key so repeated -c values can't queue the same
            // file twice (two threads writing one path would corrupt it).
            LinkedHashMap<String, RemoteFile> selected = new LinkedHashMap<>();
            List<String> missing = new ArrayList<>();
            for (String connector : selection.connectors) {
                List<RemoteFile> matches = available.stream()
                        .filter(f -> edition.artifactMatchesConnector(f.filename(), connector))
                        .toList();
                if (matches.isEmpty()) {
                    missing.add(connector);
                } else {
                    for (RemoteFile f : matches) selected.putIfAbsent(f.key(), f);
                }
            }
            if (!missing.isEmpty()) {
                System.err.println("No " + edition.displayName() + " build found in " + rel.label()
                        + " for: " + String.join(", ", missing)
                        + ". Run 'cdrm connectors' to see valid connector names.");
                return 1;
            }
            toDownload = new ArrayList<>(selected.values());
        }

        long totalBytes = toDownload.stream().mapToLong(RemoteFile::size).sum();
        int workers = Math.min(parallel, toDownload.size());
        System.out.printf("Downloading %d file%s (%s) from %s / %s to %s (%d at a time)%n",
                toDownload.size(), toDownload.size() == 1 ? "" : "s", humanSize(totalBytes),
                rel.label(), edition.displayName(), output.toAbsolutePath(), workers);

        AtomicInteger completed = new AtomicInteger();
        ConcurrentLinkedQueue<String> failures = new ConcurrentLinkedQueue<>();
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (RemoteFile file : toDownload) {
                futures.add(pool.submit(() -> {
                    try {
                        client.download(file, output);
                        System.out.printf("  [%d/%d] %s (%s)%n",
                                completed.incrementAndGet(), toDownload.size(),
                                file.filename(), humanSize(file.size()));
                    } catch (Exception e) {
                        completed.incrementAndGet();
                        failures.add(file.filename() + ": " + e.getMessage());
                        System.err.println("  FAILED " + file.filename() + ": " + e.getMessage());
                    }
                }));
            }
            for (Future<?> f : futures) {
                f.get();
            }
        } finally {
            pool.shutdown();
        }

        if (!failures.isEmpty()) {
            System.err.printf("%d of %d download%s failed.%n",
                    failures.size(), toDownload.size(), toDownload.size() == 1 ? "" : "s");
            return 1;
        }
        System.out.println("Done.");
        return 0;
    }

    private static String humanSize(long bytes) {
        if (bytes < 0) return "unknown size";
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.ROOT, "%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format(Locale.ROOT, "%.1f MB", mb);
        return String.format(Locale.ROOT, "%.2f GB", mb / 1024.0);
    }
}
