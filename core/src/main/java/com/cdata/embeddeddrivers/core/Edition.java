package com.cdata.embeddeddrivers.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A CData driver edition and its layout within the OEM builds bucket.
 *
 * Bucket naming is consistent per edition: artifact filenames are a fixed
 * wrapper around the lowercase connector name (e.g. "cdata.jdbc.{name}.jar"),
 * and bld-* build markers are a fixed prefix plus the connector name and
 * build number. Marker connector names are display-cased in the ADO and
 * ODBC Windows editions (e.g. "SAPConcur"), so name comparison is always
 * case-insensitive.
 */
public enum Edition {
    JDBC("JDBC", "jdbc", "bld-cdata.jdbc.",
            List.of(new ArtifactTemplate("cdata.jdbc.", ".jar"))),
    ADO_NET_FRAMEWORK("ADO .NET FRAMEWORK", "ado/net40", "bld-System.Data.CData.",
            List.of(new ArtifactTemplate("system.data.cdata.", ".dll"))),
    ADO_NET_STANDARD("ADO .NET STANDARD", "ado/netstandard20", "bld-System.Data.CData.",
            List.of(new ArtifactTemplate("system.data.cdata.", ".dll"))),
    ODBC_UNIX("ODBC UNIX", "odbc/linux/x64", "bld-cdata.odbc.",
            List.of(new ArtifactTemplate("cdata.odbc.", ".ini"),
                    new ArtifactTemplate("lib", "odbc.x64.so"))),
    ODBC_WINDOWS("ODBC WINDOWS", "odbc/net40/x64", "bld-CData.ODBC.",
            List.of(new ArtifactTemplate("CData.ODBC.", ".dll"))),
    PYTHON_MAC("PYTHON MAC", "python/mac", "bld-",
            List.of(new ArtifactTemplate("", ".setup_mac.zip"))),
    PYTHON_UNIX("PYTHON UNIX", "python/unix", "bld-",
            List.of(new ArtifactTemplate("", ".setup_unix.zip"))),
    PYTHON_WINDOWS("PYTHON WINDOWS", "python/win", "bld-",
            List.of(new ArtifactTemplate("", ".setup_win.zip")));

    /** A driver artifact filename shape: fixed prefix + lowercase connector name + fixed suffix. */
    public record ArtifactTemplate(String prefix, String suffix) {

        public String filenameFor(String connectorName) {
            return prefix + connectorName.toLowerCase(Locale.ROOT) + suffix;
        }

        /** The connector-name portion of a matching filename, or null if the filename doesn't fit this template. */
        public String connectorOf(String filename) {
            if (filename.length() <= prefix.length() + suffix.length()) return null;
            if (!filename.regionMatches(true, 0, prefix, 0, prefix.length())) return null;
            if (!filename.regionMatches(true, filename.length() - suffix.length(), suffix, 0, suffix.length())) return null;
            return filename.substring(prefix.length(), filename.length() - suffix.length());
        }
    }

    private final String displayName;
    private final String subpath;
    private final String bldMarkerPrefix;
    private final List<ArtifactTemplate> artifactTemplates;

    Edition(String displayName, String subpath, String bldMarkerPrefix, List<ArtifactTemplate> artifactTemplates) {
        this.displayName = displayName;
        this.subpath = subpath;
        this.bldMarkerPrefix = bldMarkerPrefix;
        this.artifactTemplates = artifactTemplates;
    }

    public String displayName() {
        return displayName;
    }

    /** Path under a release prefix where this edition's builds live (e.g. "ado/net40"). */
    public String subpath() {
        return subpath;
    }

    /** Exact-cased bld-* marker prefix, usable directly as an S3 key prefix (e.g. "bld-System.Data.CData."). */
    public String bldMarkerPrefix() {
        return bldMarkerPrefix;
    }

    /** Top-level changelog dir for this edition (e.g. "ADO .NET FRAMEWORK" -> "ado"). */
    public String changelogPath() {
        int slash = subpath.indexOf('/');
        return slash >= 0 ? subpath.substring(0, slash) : subpath;
    }

    /** The exact artifact filenames a connector publishes in this edition. */
    public List<String> artifactFilenames(String connectorName) {
        List<String> names = new ArrayList<>(artifactTemplates.size());
        for (ArtifactTemplate t : artifactTemplates) {
            names.add(t.filenameFor(connectorName));
        }
        return names;
    }

    /** Whether a filename is a downloadable driver artifact of this edition (as opposed to a bld-* marker). */
    public boolean isDriverArtifact(String filename) {
        for (ArtifactTemplate t : artifactTemplates) {
            if (t.connectorOf(filename) != null) return true;
        }
        return false;
    }

    /** Whether a driver artifact filename in this edition belongs to the given connector. */
    public boolean artifactMatchesConnector(String filename, String connectorName) {
        for (ArtifactTemplate t : artifactTemplates) {
            String name = t.connectorOf(filename);
            if (name != null && name.equalsIgnoreCase(connectorName)) return true;
        }
        return false;
    }

    /**
     * Extracts the build number from a bld-* marker filename if it belongs to
     * the given connector (name compared case-insensitively). Returns -1 otherwise.
     */
    public int markerBuild(String filename, String connectorName) {
        if (!filename.regionMatches(true, 0, bldMarkerPrefix, 0, bldMarkerPrefix.length())) return -1;
        String rest = filename.substring(bldMarkerPrefix.length());
        int dot = rest.lastIndexOf('.');
        if (dot <= 0 || dot == rest.length() - 1) return -1;
        if (!rest.substring(0, dot).equalsIgnoreCase(connectorName)) return -1;
        try {
            return Integer.parseInt(rest.substring(dot + 1));
        } catch (NumberFormatException e) {
            return -1;
        }
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
