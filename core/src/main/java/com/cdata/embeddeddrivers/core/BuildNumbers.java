package com.cdata.embeddeddrivers.core;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;

/** Conversions to CData build numbers (days since 2000-01-01 UTC). */
public final class BuildNumbers {

    private static final LocalDate EPOCH_2000 = LocalDate.of(2000, 1, 1);

    private BuildNumbers() {
    }

    /** Extracts the build number from a changelog version string (e.g. "25.0.9434" -> 9434). Returns -1 on failure. */
    public static int fromVersion(String versionStr) {
        String[] parts = versionStr.trim().split("\\.");
        if (parts.length >= 3) {
            try {
                return Integer.parseInt(parts[parts.length - 1]);
            } catch (NumberFormatException ignored) {
            }
        }
        return -1;
    }

    /** Converts an ISO 8601 date (e.g. "2025-10-28") to a build number. */
    public static int fromDate(String iso) {
        LocalDate date;
        try {
            date = LocalDate.parse(iso);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid date, expected YYYY-MM-DD: " + iso);
        }
        if (date.getYear() < 2000 || date.getYear() > 2100) {
            throw new IllegalArgumentException("Year out of range in date: " + iso);
        }
        return (int) ChronoUnit.DAYS.between(EPOCH_2000, date);
    }
}
