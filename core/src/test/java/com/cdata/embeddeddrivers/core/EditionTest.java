package com.cdata.embeddeddrivers.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void buildsBucketPrefixes() {
        Release r = new Release(2026, 0);
        assertEquals("v26u0/jdbc/", Edition.JDBC.releasePrefix(r));
        assertEquals("v26u0/ado/net40/", Edition.ADO_NET_FRAMEWORK.releasePrefix(r));
        assertEquals("v26u0/ado/net40/bld-System.Data.CData.", Edition.ADO_NET_FRAMEWORK.markerPrefix(r));
        assertEquals("changelogs/v25/jdbc/", Edition.JDBC.changelogPrefix(2025));
        assertEquals("changelogs/v25/odbc/", Edition.ODBC_UNIX.changelogPrefix(2025));
    }

    @Test
    void extractsConnectorFromArtifact() {
        assertEquals("salesforce", Edition.JDBC.artifactConnector("cdata.jdbc.salesforce.jar"));
        assertEquals("salesforce", Edition.ODBC_UNIX.artifactConnector("libsalesforceodbc.x64.so"));
        assertEquals(null, Edition.JDBC.artifactConnector("bld-cdata.jdbc.salesforce.9655"));
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
