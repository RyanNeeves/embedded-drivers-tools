package com.cdata.embeddeddrivers.core;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A CData driver edition and its layout within the OEM builds bucket. */
public enum Edition {
    JDBC("JDBC", "jdbc",
            Pattern.compile("^bld-cdata\\.jdbc\\.(.+)\\.(\\d+)$"),
            List.of(Pattern.compile("(?i)^cdata\\.jdbc\\.(.+)\\.jar$"))),
    ADO_NET_FRAMEWORK("ADO .NET FRAMEWORK", "ado/net40",
            Pattern.compile("^bld-System\\.Data\\.CData\\.(.+)\\.(\\d+)$"),
            List.of(Pattern.compile("(?i)^system\\.data\\.cdata\\.(.+)\\.dll$"))),
    ADO_NET_STANDARD("ADO .NET STANDARD", "ado/netstandard20",
            Pattern.compile("^bld-System\\.Data\\.CData\\.(.+)\\.(\\d+)$"),
            List.of(Pattern.compile("(?i)^system\\.data\\.cdata\\.(.+)\\.dll$"))),
    ODBC_UNIX("ODBC UNIX", "odbc/linux/x64",
            Pattern.compile("^bld-[Cc][Dd]ata\\.[Oo][Dd][Bb][Cc]\\.(.+)\\.(\\d+)$"),
            List.of(Pattern.compile("(?i)^cdata\\.odbc\\.(.+)\\.ini$"),
                    Pattern.compile("(?i)^lib(.+)odbc\\.x64\\.so$"))),
    ODBC_WINDOWS("ODBC WINDOWS", "odbc/net40/x64",
            Pattern.compile("^bld-[Cc][Dd]ata\\.[Oo][Dd][Bb][Cc]\\.(.+)\\.(\\d+)$"),
            List.of(Pattern.compile("(?i)^cdata\\.odbc\\.(.+)\\.dll$"))),
    PYTHON_MAC("PYTHON MAC", "python/mac",
            Pattern.compile("^bld-(.+)\\.(\\d+)$"),
            List.of(Pattern.compile("(?i)^(.+)\\.setup_mac\\.zip$"))),
    PYTHON_UNIX("PYTHON UNIX", "python/unix",
            Pattern.compile("^bld-(.+)\\.(\\d+)$"),
            List.of(Pattern.compile("(?i)^(.+)\\.setup_unix\\.zip$"))),
    PYTHON_WINDOWS("PYTHON WINDOWS", "python/win",
            Pattern.compile("^bld-(.+)\\.(\\d+)$"),
            List.of(Pattern.compile("(?i)^(.+)\\.setup_win\\.zip$")));

    private final String displayName;
    private final String subpath;
    private final Pattern bldPattern;
    private final List<Pattern> artifactPatterns;

    Edition(String displayName, String subpath, Pattern bldPattern, List<Pattern> artifactPatterns) {
        this.displayName = displayName;
        this.subpath = subpath;
        this.bldPattern = bldPattern;
        this.artifactPatterns = artifactPatterns;
    }

    public String displayName() {
        return displayName;
    }

    /** Path under a release prefix where this edition's builds live (e.g. "ado/net40"). */
    public String subpath() {
        return subpath;
    }

    /** Pattern matching this edition's bld-* marker filenames; group 1 = connector, group 2 = build number. */
    public Pattern bldPattern() {
        return bldPattern;
    }

    /** Top-level changelog dir for this edition (e.g. "ADO .NET FRAMEWORK" -> "ado"). */
    public String changelogPath() {
        int slash = subpath.indexOf('/');
        return slash >= 0 ? subpath.substring(0, slash) : subpath;
    }

    /** Whether a driver artifact filename in this edition belongs to the given connector. */
    public boolean artifactMatchesConnector(String filename, String connectorName) {
        for (Pattern p : artifactPatterns) {
            Matcher m = p.matcher(filename);
            if (m.matches() && m.group(1).equalsIgnoreCase(connectorName)) return true;
        }
        return false;
    }

    /** Whether a filename is a downloadable driver artifact of this edition (as opposed to a bld-* marker). */
    public boolean isDriverArtifact(String filename) {
        for (Pattern p : artifactPatterns) {
            if (p.matcher(filename).matches()) return true;
        }
        return false;
    }

    /**
     * Parses user input leniently: case-insensitive, with spaces, dots, hyphens,
     * and underscores treated as equivalent (e.g. "ado-net-framework").
     */
    public static Edition parse(String input) {
        String normalized = normalize(input);
        for (Edition e : values()) {
            if (normalize(e.displayName).equals(normalized) || e.name().equalsIgnoreCase(input.trim())) {
                return e;
            }
        }
        StringBuilder valid = new StringBuilder();
        for (Edition e : values()) {
            if (valid.length() > 0) valid.append(", ");
            valid.append(e.displayName);
        }
        throw new IllegalArgumentException("Unknown edition '" + input + "'. Valid editions: " + valid);
    }

    private static String normalize(String s) {
        return s.trim().toUpperCase(Locale.ROOT).replaceAll("[\\s._-]+", " ");
    }
}
