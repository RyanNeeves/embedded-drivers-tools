package com.cdata.embeddeddrivers.core;

import java.util.ArrayList;
import java.util.List;

/** Parses and filters changelog CSV content. */
public final class Changelog {

    private Changelog() {
    }

    /** Header line plus the entry lines newer than a baseline build. */
    public record Filtered(String header, List<String> entries) {
    }

    /**
     * Filters changelog CSV to entries whose Version build number is greater
     * than {@code baselineBuild}.
     *
     * @throws IllegalArgumentException if the CSV has no 'Version' column
     */
    public static Filtered filterAfterBuild(String csvBody, int baselineBuild) {
        String[] lines = csvBody.replace("\r\n", "\n").replace("\r", "\n").split("\n");
        if (lines.length < 2) {
            return new Filtered(lines.length > 0 ? lines[0] : "", List.of());
        }

        int versionCol = Csv.columnIndex(lines[0], "Version");
        if (versionCol < 0) {
            throw new IllegalArgumentException("Changelog CSV missing 'Version' column.");
        }

        List<String> filtered = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            String[] fields = Csv.splitLine(line);
            if (versionCol < fields.length && BuildNumbers.fromVersion(fields[versionCol]) > baselineBuild) {
                filtered.add(line);
            }
        }
        return new Filtered(lines[0], filtered);
    }
}
