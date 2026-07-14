package com.cdata.embeddeddrivers.core;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Client for the public CData OEM builds bucket. Discovers releases,
 * connectors, build markers, and changelogs, and downloads driver builds.
 */
public class OemBuildsClient {

    public static final String BASE_URL = "https://downloads.cdata.com/cdataoembuilds";
    private static final String CHANGELOG_ROOT = BASE_URL + "/changelogs";

    /** Releases that predate bld-* markers in the bucket, mapped to their build numbers. */
    private static final Map<String, Integer> HARDCODED_RELEASES = Map.of("v25u1", 9434);

    private static final Pattern PAT_S3_CONTENTS     = Pattern.compile("<Contents>(.*?)</Contents>", Pattern.DOTALL);
    private static final Pattern PAT_S3_KEY          = Pattern.compile("<Key>([^<]+)</Key>");
    private static final Pattern PAT_S3_SIZE         = Pattern.compile("<Size>(\\d+)</Size>");
    private static final Pattern PAT_S3_CONTINUATION = Pattern.compile("<NextContinuationToken>([^<]+)</NextContinuationToken>");
    private static final Pattern PAT_S3_RELEASE      = Pattern.compile("<Prefix>v(\\d{2})u(\\d+)/</Prefix>");
    private static final Pattern PAT_RELEASE_TAG     = Pattern.compile("v(\\d{2})u(\\d+)");

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    /** Response body plus HTTP status, so callers can distinguish 404 from other failures. */
    public record HttpResult(int status, String body) {
    }

    HttpResult get(String url) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return new HttpResult(response.statusCode(), response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while fetching " + url, e);
        }
    }

    /** Unescapes XML entities in S3 ListObjectsV2 responses. */
    private static String xmlUnescape(String s) {
        return s.replace("&amp;", "&").replace("&lt;", "<")
                .replace("&gt;", ">").replace("&apos;", "'").replace("&quot;", "\"");
    }

    /** Lists all objects (with sizes) under a given S3 prefix, handling pagination via continuation tokens. */
    public List<RemoteFile> listFiles(String prefix) throws IOException {
        List<RemoteFile> files = new ArrayList<>();
        String continuationToken = null;
        do {
            StringBuilder url = new StringBuilder(BASE_URL)
                    .append("/?list-type=2&prefix=")
                    .append(URLEncoder.encode(prefix, StandardCharsets.UTF_8));
            if (continuationToken != null) {
                url.append("&continuation-token=").append(URLEncoder.encode(continuationToken, StandardCharsets.UTF_8));
            }
            HttpResult res = get(url.toString());
            if (res.status() != 200) {
                throw new IOException("S3 list returned HTTP " + res.status() + " for prefix: " + prefix);
            }
            Matcher cm = PAT_S3_CONTENTS.matcher(res.body());
            while (cm.find()) {
                String block = cm.group(1);
                Matcher km = PAT_S3_KEY.matcher(block);
                if (!km.find()) continue;
                Matcher sm = PAT_S3_SIZE.matcher(block);
                long size = sm.find() ? Long.parseLong(sm.group(1)) : -1;
                files.add(new RemoteFile(xmlUnescape(km.group(1)), size));
            }
            if (res.body().contains("<IsTruncated>true</IsTruncated>")) {
                Matcher tm = PAT_S3_CONTINUATION.matcher(res.body());
                continuationToken = tm.find() ? tm.group(1) : null;
            } else {
                continuationToken = null;
            }
        } while (continuationToken != null);
        return files;
    }

    /** Lists all object keys under a given S3 prefix. */
    public List<String> listObjects(String prefix) throws IOException {
        List<String> keys = new ArrayList<>();
        for (RemoteFile f : listFiles(prefix)) keys.add(f.key());
        return keys;
    }

    /** Discovers all available releases from S3 prefixes and hardcoded entries, newest first. */
    public List<Release> listReleases() throws IOException {
        HttpResult res = get(BASE_URL + "/?list-type=2&prefix=v&delimiter=/");
        if (res.status() != 200) {
            throw new IOException("S3 release discovery returned HTTP " + res.status());
        }

        List<Release> releases = new ArrayList<>();
        Matcher m = PAT_S3_RELEASE.matcher(res.body());
        while (m.find()) {
            releases.add(new Release(2000 + Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))));
        }
        for (String tag : HARDCODED_RELEASES.keySet()) {
            Matcher hm = PAT_RELEASE_TAG.matcher(tag);
            if (hm.matches()) {
                releases.add(new Release(2000 + Integer.parseInt(hm.group(1)), Integer.parseInt(hm.group(2))));
            }
        }
        Collections.sort(releases);
        return releases;
    }

    /** The newest available release. */
    public Release latestRelease() throws IOException {
        List<Release> releases = listReleases();
        if (releases.isEmpty()) throw new IOException("No releases found in bucket.");
        return releases.get(0);
    }

    /** Validates that a release exists in the bucket (or is a known hardcoded release). */
    public void requireRelease(Release release) throws IOException {
        List<Release> releases = listReleases();
        if (!releases.contains(release)) {
            StringBuilder sb = new StringBuilder("Release '" + release.label() + "' does not exist. Available releases:");
            for (Release r : releases) sb.append("\n  ").append(r.label());
            throw new IllegalArgumentException(sb.toString());
        }
    }

    /** Lists the connector names that have changelogs for an edition and major version, sorted. */
    public List<String> listConnectors(Edition edition, int majorVersion) throws IOException {
        String clPrefix = "changelogs/v" + (majorVersion % 100) + "/" + edition.changelogPath() + "/";
        Set<String> names = new TreeSet<>();
        for (RemoteFile f : listFiles(clPrefix)) {
            String rest = f.key().substring(clPrefix.length());
            int slash = rest.indexOf('/');
            if (slash > 0) names.add(rest.substring(0, slash));
        }
        return new ArrayList<>(names);
    }

    /**
     * Resolves a release (major version + U-number) to its build number via
     * hardcoded releases or S3 build marker lookup.
     */
    public int releaseToBuildNumber(int majorVersion, int releaseNumber, Edition edition, String connectorName)
            throws IOException {
        Release release = new Release(majorVersion, releaseNumber);

        Integer hardcoded = HARDCODED_RELEASES.get(release.tag());
        if (hardcoded != null) return hardcoded;

        requireRelease(release);

        String releasePath = release.tag() + "/" + edition.subpath();
        for (RemoteFile f : listFiles(releasePath + "/bld-")) {
            Matcher m = edition.bldPattern().matcher(f.filename());
            if (m.matches() && m.group(1).equalsIgnoreCase(connectorName)) {
                return Integer.parseInt(m.group(2));
            }
        }
        throw new IllegalArgumentException(
                "No build found for '" + connectorName + "' in " + edition.displayName() + " / " + release.tag() + ".");
    }

    /**
     * Fetches the raw changelog CSV for a connector. Returns null if the
     * changelog does not exist (HTTP 404).
     */
    public String fetchChangelogCsv(Edition edition, int majorVersion, String connectorName) throws IOException {
        String url = CHANGELOG_ROOT + "/v" + (majorVersion % 100) + "/" + edition.changelogPath()
                + "/" + connectorName.toLowerCase(Locale.ROOT) + "/changelog.csv";
        HttpResult res = get(url);
        if (res.status() == 404) return null;
        if (res.status() != 200) {
            throw new IOException("HTTP " + res.status() + " fetching changelog for '" + connectorName + "'.");
        }
        return res.body();
    }

    /** Lists the downloadable driver artifacts for a release and edition (excludes bld-* markers). */
    public List<RemoteFile> listDriverFiles(Release release, Edition edition) throws IOException {
        List<RemoteFile> artifacts = new ArrayList<>();
        for (RemoteFile f : listFiles(release.tag() + "/" + edition.subpath() + "/")) {
            if (edition.isDriverArtifact(f.filename())) artifacts.add(f);
        }
        return artifacts;
    }

    /**
     * Downloads a bucket object to {@code destDir}, keeping its filename.
     * Creates the directory if needed. Returns the path written.
     */
    public Path download(RemoteFile file, Path destDir) throws IOException {
        Files.createDirectories(destDir);
        Path dest = destDir.resolve(file.filename());
        HttpRequest request = HttpRequest.newBuilder(URI.create(file.url())).GET().build();
        try {
            HttpResponse<Path> response = http.send(request, HttpResponse.BodyHandlers.ofFile(dest));
            if (response.statusCode() != 200) {
                Files.deleteIfExists(dest);
                throw new IOException("HTTP " + response.statusCode() + " downloading " + file.url());
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Files.deleteIfExists(dest);
            throw new IOException("Interrupted while downloading " + file.url(), e);
        }
    }
}
