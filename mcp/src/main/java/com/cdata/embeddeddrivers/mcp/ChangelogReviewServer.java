package com.cdata.embeddeddrivers.mcp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.cdata.embeddeddrivers.core.BuildNumbers;
import com.cdata.embeddeddrivers.core.Changelog;
import com.cdata.embeddeddrivers.core.Edition;
import com.cdata.embeddeddrivers.core.OemBuildsClient;
import com.cdata.embeddeddrivers.core.Release;

import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.ServerCapabilities;
import io.modelcontextprotocol.spec.McpSchema.TextContent;

/**
 * CData Changelog Review — MCP Server.
 * Thin MCP layer over embedded-drivers-core; see README.md for setup.
 */
public class ChangelogReviewServer {

    private static final OemBuildsClient CLIENT = new OemBuildsClient();

    // ============================================================
    //  SHARED UTILITIES
    // ============================================================

    private static CallToolResult ok(String text) {
        return CallToolResult.builder()
                .content(Collections.singletonList((McpSchema.Content) new TextContent(text)))
                .isError(false)
                .build();
    }

    private static CallToolResult err(String message) {
        return CallToolResult.builder()
                .content(Collections.singletonList((McpSchema.Content) new TextContent(message)))
                .isError(true)
                .build();
    }

    private static String stripTrailing(String s) {
        int i = s.length() - 1;
        while (i >= 0 && Character.isWhitespace(s.charAt(i))) i--;
        return i < 0 ? "" : s.substring(0, i + 1);
    }

    private static List<String> editionDisplayNames() {
        List<String> names = new ArrayList<>();
        for (Edition e : Edition.values()) names.add(e.displayName());
        return names;
    }

    // ============================================================
    //  SCHEMA HELPERS
    // ============================================================

    private static Map<String, Object> schemaProperty(String type, String description) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("description", description);
        return m;
    }

    private static Map<String, Object> schemaEnum(String description, Collection<String> values) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "string");
        m.put("description", description);
        m.put("enum", new ArrayList<>(values));
        return m;
    }

    // ============================================================
    //  ARGUMENT PARSING
    // ============================================================

    private static String stringArg(Map<String, Object> args, String key) {
        Object val = args.get(key);
        if (val == null) return null;
        String s = val.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static Integer optIntArg(Map<String, Object> args, String key) {
        Object val = args.get(key);
        if (val == null) return null;
        if (val instanceof Number n) return n.intValue();
        if (val instanceof String s) {
            String sv = s.trim();
            return sv.isEmpty() ? null : Integer.parseInt(sv);
        }
        return null;
    }

    private static int requireMajorVersion(Map<String, Object> args) {
        Integer mv = optIntArg(args, "major_version");
        if (mv == null) {
            throw new IllegalArgumentException("major_version is required. Call list_releases to see available major versions.");
        }
        return mv;
    }

    private static Edition requireEdition(Map<String, Object> args) {
        String editionRaw = stringArg(args, "edition");
        if (editionRaw == null) {
            throw new IllegalArgumentException("edition is required.");
        }
        return Edition.parse(editionRaw);
    }

    private static String appendConnectorHint(String message, Edition edition, int majorVersion) {
        return message + " Call list_connectors with edition='" + edition.displayName()
                + "' and major_version=" + majorVersion + " to see all valid connector names.";
    }

    // ============================================================
    //  TOOL: list_releases
    // ============================================================

    private static McpSchema.JsonSchema listReleasesSchema() {
        return new McpSchema.JsonSchema("object",
                Collections.emptyMap(), Collections.emptyList(), null, null, null);
    }

    private static CallToolResult handleListReleases(Map<String, Object> args) {
        try {
            List<Release> releases = CLIENT.listReleases();
            if (releases.isEmpty()) return ok("No releases found.");
            StringBuilder sb = new StringBuilder("Available releases (newest first):\n");
            for (Release r : releases) {
                sb.append(String.format("  %s  (major_version: %d, release_number: %d)%n",
                        r.label(), r.year(), r.releaseNumber()));
            }
            return ok(stripTrailing(sb.toString()));
        } catch (Exception e) {
            e.printStackTrace(System.err);
            return err("Error listing releases: " + e.getMessage());
        }
    }

    // ============================================================
    //  TOOL: list_connectors
    // ============================================================

    private static McpSchema.JsonSchema listConnectorsSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("edition", schemaEnum("Driver edition.", editionDisplayNames()));
        props.put("major_version", schemaProperty("integer", "Major version year from list_releases (e.g. 2025)."));
        return new McpSchema.JsonSchema("object", props,
                Arrays.asList("edition", "major_version"), null, null, null);
    }

    private static CallToolResult handleListConnectors(Map<String, Object> args) {
        int majorVersion;
        Edition edition;
        try {
            majorVersion = requireMajorVersion(args);
            edition = requireEdition(args);
        } catch (IllegalArgumentException e) {
            return err(e.getMessage());
        }

        List<String> connectors;
        try {
            connectors = CLIENT.listConnectors(edition, majorVersion);
        } catch (Exception e) {
            e.printStackTrace(System.err);
            return err("Error listing connectors: " + e.getMessage());
        }

        if (connectors.isEmpty()) {
            try {
                boolean exists = CLIENT.listReleases().stream().anyMatch(r -> r.year() == majorVersion);
                if (!exists) {
                    return err("Major version " + majorVersion + " does not exist. Call list_releases to see available major versions.");
                }
            } catch (Exception ignored) {
            }
            return ok("No connectors found for " + edition.displayName() + " in major version " + majorVersion + ".");
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%d connector%s available for %s in major version %d (use one verbatim as connector_name in get_changelog):%n",
                connectors.size(), connectors.size() == 1 ? "" : "s", edition.displayName(), majorVersion));
        for (String s : connectors) {
            sb.append("  ").append(s).append('\n');
        }
        return ok(stripTrailing(sb.toString()));
    }

    // ============================================================
    //  TOOL: get_changelog
    // ============================================================

    private static McpSchema.JsonSchema getChangelogSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("edition",              schemaEnum("Driver edition.", editionDisplayNames()));
        props.put("connector_name",       schemaProperty("string",  "Connector name (e.g. Salesforce)"));
        props.put("major_version",        schemaProperty("integer", "Major version year from list_releases (e.g. 2025). Each major version has its own independent changelog."));
        props.put("after_release_number", schemaProperty("integer", "The U-number exactly as shown by list_releases. For '2025 U1' use 1, for '2025 U2' use 2. Must be >= 1. Do NOT subtract or compute — use the number directly."));
        props.put("after_date",           schemaProperty("string",  "Return entries after this date (ISO 8601 format, e.g. '2025-10-28'). Use for date-based queries like 'changes in the last month'."));
        props.put("after_build",          schemaProperty("integer", "Return entries after this build number. Only use if the user provides a specific build number. Prefer after_date or after_release_number instead."));
        return new McpSchema.JsonSchema("object", props,
                Arrays.asList("edition", "connector_name", "major_version"), null, null, null);
    }

    private static CallToolResult handleGetChangelog(Map<String, Object> args) {
        int majorVersion;
        Edition edition;
        try {
            majorVersion = requireMajorVersion(args);
            edition = requireEdition(args);
        } catch (IllegalArgumentException e) {
            return err(e.getMessage());
        }

        String objName = stringArg(args, "connector_name");
        if (objName == null) {
            return err("connector_name is required.");
        }

        // -- Resolve baseline build number from exactly one "after" param --
        Integer afterBuild         = optIntArg(args, "after_build");
        Integer afterReleaseNumber = optIntArg(args, "after_release_number");
        String  afterDate          = stringArg(args, "after_date");

        int afterCount = (afterBuild != null ? 1 : 0) + (afterReleaseNumber != null ? 1 : 0) + (afterDate != null ? 1 : 0);
        if (afterCount == 0) {
            return err("Provide exactly one of: after_release_number, after_date, or after_build.");
        }
        if (afterCount > 1) {
            return err("Provide only one of: after_release_number, after_date, or after_build.");
        }

        int baselineBuild;
        try {
            if (afterDate != null) {
                baselineBuild = BuildNumbers.fromDate(afterDate);
            } else if (afterBuild != null) {
                if (afterBuild < 1) return err("after_build must be a positive build number.");
                baselineBuild = afterBuild;
            } else {
                if (afterReleaseNumber < 1) {
                    return err("after_release_number must be >= 1. Use the U-number directly from list_releases (e.g. 1 for U1, 2 for U2).");
                }
                baselineBuild = CLIENT.releaseToBuildNumber(majorVersion, afterReleaseNumber, edition, objName);
            }
        } catch (IllegalArgumentException e) {
            return err(appendConnectorHint(e.getMessage(), edition, majorVersion));
        } catch (Exception e) {
            e.printStackTrace(System.err);
            return err("Error resolving baseline build: " + e.getMessage());
        }

        // -- Fetch and filter changelog --
        String csv;
        try {
            csv = CLIENT.fetchChangelogCsv(edition, majorVersion, objName);
        } catch (Exception e) {
            e.printStackTrace(System.err);
            return err("Error fetching changelog: " + e.getMessage());
        }
        if (csv == null) {
            return err(appendConnectorHint(
                    "No changelog found for '" + objName + "' (" + edition.displayName() + ").",
                    edition, majorVersion));
        }

        Changelog.Filtered result;
        try {
            result = Changelog.filterAfterBuild(csv, baselineBuild);
        } catch (IllegalArgumentException e) {
            return err(e.getMessage());
        }

        if (result.entries().isEmpty()) {
            return ok("No changelog entries after build " + baselineBuild + " for '" + objName
                    + "' in major version " + majorVersion + ".");
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Changelog: %s (%s) v%d — %d entr%s after build %d%n%n",
                objName, edition.displayName(), majorVersion,
                result.entries().size(), result.entries().size() == 1 ? "y" : "ies", baselineBuild));
        sb.append(result.header()).append('\n');
        for (String line : result.entries()) {
            sb.append(line).append('\n');
        }
        return ok(stripTrailing(sb.toString()));
    }

    // ============================================================
    //  MAIN
    // ============================================================

    public static void main(String[] args) throws Exception {
        StdioServerTransportProvider transport =
                new StdioServerTransportProvider(McpJsonDefaults.getMapper());

        McpServer.sync(transport)
                .serverInfo("cdata-changelog-review-mcp", "1.2.0")
                .capabilities(ServerCapabilities.builder().tools(true).build())

                .toolCall(
                        McpSchema.Tool.builder()
                                .name("list_releases")
                                .description(
                                        "List available CData connector releases, newest first. " +
                                        "Step 1 of the workflow: list_releases → list_connectors → get_changelog. " +
                                        "MUST be called before get_changelog — only use release numbers returned here. " +
                                        "Do NOT guess or assume release numbers — only releases returned by this tool are valid. " +
                                        "No arguments required.")
                                .inputSchema(listReleasesSchema())
                                .build(),
                        (exchange, request) -> handleListReleases(
                                request.arguments() != null ? request.arguments() : Collections.emptyMap()))

                .toolCall(
                        McpSchema.Tool.builder()
                                .name("list_connectors")
                                .description(
                                        "List the valid connectors for an edition and major version. " +
                                        "Step 2 of the workflow: list_releases → list_connectors → get_changelog. " +
                                        "Call this before get_changelog whenever you are unsure of the exact connector name — " +
                                        "do NOT guess connector names. Use a returned name verbatim as get_changelog's connector_name. " +
                                        "Requires edition and major_version.")
                                .inputSchema(listConnectorsSchema())
                                .build(),
                        (exchange, request) -> handleListConnectors(
                                request.arguments() != null ? request.arguments() : Collections.emptyMap()))

                .toolCall(
                        McpSchema.Tool.builder()
                                .name("get_changelog")
                                .description(
                                        "Get changelog entries for a CData connector since a release, date, or build. " +
                                        "Each major version has its own independent changelog. " +
                                        "IMPORTANT: Call list_releases first. Do NOT invent or guess release numbers. " +
                                        "If unsure of the exact connector_name, call list_connectors first and use a name from it verbatim. " +
                                        "The major_version is NOT the current calendar year — it is the version year from list_releases. " +
                                        "Requires EXACTLY ONE of: " +
                                        "after_release_number (U-number, e.g. 2 for U2), " +
                                        "after_date (ISO 8601 date, e.g. '2025-10-28' — use for 'changes in the last month'), or " +
                                        "after_build (integer build number). " +
                                        "If the user doesn't specify a release, date, or build, ASK. " +
                                        "If edition not specified, ASK.")
                                .inputSchema(getChangelogSchema())
                                .build(),
                        (exchange, request) -> handleGetChangelog(
                                request.arguments() != null ? request.arguments() : Collections.emptyMap()))

                .build();

        System.err.println("CData Changelog Review MCP server started.");
        Thread.currentThread().join();
    }
}
