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
    void changelogPathIsFirstSegment() {
        assertEquals("jdbc", Edition.JDBC.changelogPath());
        assertEquals("ado", Edition.ADO_NET_FRAMEWORK.changelogPath());
        assertEquals("odbc", Edition.ODBC_UNIX.changelogPath());
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
}
