package com.cdata.embeddeddrivers.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class BuildNumbersTest {

    @Test
    void extractsBuildFromVersion() {
        assertEquals(9434, BuildNumbers.fromVersion("25.0.9434"));
        assertEquals(9000, BuildNumbers.fromVersion(" 25.0.9000 "));
        assertEquals(-1, BuildNumbers.fromVersion("25.0"));
        assertEquals(-1, BuildNumbers.fromVersion("25.0.abc"));
    }

    @Test
    void convertsDateToBuildNumber() {
        // 2000-01-01 is day 0
        assertEquals(0, BuildNumbers.fromDate("2000-01-01"));
        assertEquals(366, BuildNumbers.fromDate("2001-01-01")); // 2000 was a leap year
    }

    @Test
    void rejectsInvalidDates() {
        assertThrows(IllegalArgumentException.class, () -> BuildNumbers.fromDate("2025-13-01"));
        assertThrows(IllegalArgumentException.class, () -> BuildNumbers.fromDate("not-a-date"));
        assertThrows(IllegalArgumentException.class, () -> BuildNumbers.fromDate("1999-12-31"));
    }
}
