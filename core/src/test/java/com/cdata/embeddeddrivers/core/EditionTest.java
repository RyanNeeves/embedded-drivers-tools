package com.cdata.embeddeddrivers.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class EditionTest {

    @Test
    void parsesCanonicalNames() {
        assertEquals(Edition.JDBC, Edition.parse("JDBC"));
        assertEquals(Edition.ADO_NET_FRAMEWORK, Edition.parse("ADO .NET FRAMEWORK"));
    }

    @Test
    void parsesLenientVariants() {
        assertEquals(Edition.JDBC, Edition.parse("jdbc"));
        assertEquals(Edition.ADO_NET_FRAMEWORK, Edition.parse("ado-net-framework"));
        assertEquals(Edition.ODBC_WINDOWS, Edition.parse("odbc_windows"));
        assertEquals(Edition.PYTHON_MAC, Edition.parse("python mac"));
    }

    @Test
    void rejectsUnknownEdition() {
        assertThrows(IllegalArgumentException.class, () -> Edition.parse("COBOL"));
    }

    @Test
    void changelogPathIsFirstSegment() {
        assertEquals("jdbc", Edition.JDBC.changelogPath());
        assertEquals("ado", Edition.ADO_NET_FRAMEWORK.changelogPath());
        assertEquals("odbc", Edition.ODBC_UNIX.changelogPath());
    }

    @Test
    void constructsArtifactFilenames() {
        assertEquals(List.of("cdata.jdbc.salesforce.jar"), Edition.JDBC.artifactFilenames("Salesforce"));
        assertEquals(List.of("CData.ODBC.salesforce.dll"), Edition.ODBC_WINDOWS.artifactFilenames("Salesforce"));
        assertEquals(List.of("cdata.odbc.salesforce.ini", "libsalesforceodbc.x64.so"),
                Edition.ODBC_UNIX.artifactFilenames("salesforce"));
        assertEquals(List.of("salesforce.setup_win.zip"), Edition.PYTHON_WINDOWS.artifactFilenames("SALESFORCE"));
    }

    @Test
    void recognizesDriverArtifacts() {
        assertTrue(Edition.JDBC.isDriverArtifact("cdata.jdbc.salesforce.jar"));
        assertTrue(Edition.ADO_NET_FRAMEWORK.isDriverArtifact("system.data.cdata.salesforce.dll"));
        assertTrue(Edition.ODBC_WINDOWS.isDriverArtifact("CData.ODBC.salesforce.dll"));
        assertTrue(Edition.ODBC_UNIX.isDriverArtifact("cdata.odbc.salesforce.ini"));
        assertTrue(Edition.ODBC_UNIX.isDriverArtifact("libsalesforceodbc.x64.so"));
        assertTrue(Edition.PYTHON_WINDOWS.isDriverArtifact("salesforce.setup_win.zip"));

        assertFalse(Edition.JDBC.isDriverArtifact("bld-cdata.jdbc.salesforce.9655"));
        assertFalse(Edition.PYTHON_WINDOWS.isDriverArtifact("bld-salesforce.9655"));
    }

    @Test
    void matchesArtifactsToConnectors() {
        assertTrue(Edition.JDBC.artifactMatchesConnector("cdata.jdbc.salesforce.jar", "Salesforce"));
        assertTrue(Edition.ODBC_UNIX.artifactMatchesConnector("libsalesforceodbc.x64.so", "salesforce"));
        assertTrue(Edition.ODBC_UNIX.artifactMatchesConnector("cdata.odbc.salesforce.ini", "salesforce"));
        assertTrue(Edition.PYTHON_MAC.artifactMatchesConnector("salesforce.setup_mac.zip", "salesforce"));

        assertFalse(Edition.JDBC.artifactMatchesConnector("cdata.jdbc.mysql.jar", "salesforce"));
        // "sql" must not match "mysql" artifacts
        assertFalse(Edition.JDBC.artifactMatchesConnector("cdata.jdbc.mysql.jar", "sql"));
    }

    @Test
    void extractsBuildFromMarkers() {
        assertEquals(9655, Edition.JDBC.markerBuild("bld-cdata.jdbc.salesforce.9655", "salesforce"));
        // ADO and ODBC Windows markers use display-cased connector names
        assertEquals(9655, Edition.ADO_NET_FRAMEWORK.markerBuild("bld-System.Data.CData.SAPConcur.9655", "sapconcur"));
        assertEquals(9655, Edition.ODBC_WINDOWS.markerBuild("bld-CData.ODBC.AAS.9655", "aas"));
        assertEquals(9655, Edition.PYTHON_WINDOWS.markerBuild("bld-zendesk.9655", "Zendesk"));

        assertEquals(-1, Edition.JDBC.markerBuild("bld-cdata.jdbc.mysql.9655", "salesforce"));
        assertEquals(-1, Edition.JDBC.markerBuild("cdata.jdbc.salesforce.jar", "salesforce"));
        assertEquals(-1, Edition.JDBC.markerBuild("bld-cdata.jdbc.salesforce.notanumber", "salesforce"));
    }
}
