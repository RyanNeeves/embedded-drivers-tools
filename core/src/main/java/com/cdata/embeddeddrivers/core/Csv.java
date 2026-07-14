package com.cdata.embeddeddrivers.core;

import java.util.ArrayList;
import java.util.List;

/** Minimal CSV parsing helpers for changelog files. */
public final class Csv {

    private Csv() {
    }

    /** Finds the index of a column name in a CSV header line. Returns -1 if not found. */
    public static int columnIndex(String headerLine, String columnName) {
        String[] fields = splitLine(headerLine);
        for (int i = 0; i < fields.length; i++) {
            if (fields[i].trim().equals(columnName)) return i;
        }
        return -1;
    }

    /** Splits a CSV line respecting quoted fields and escaped double-quotes. */
    public static String[] splitLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        char[] chars = line.toCharArray();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (c == '"') {
                if (inQuotes && i + 1 < chars.length && chars[i + 1] == '"') {
                    cur.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                fields.add(cur.toString());
                cur = new StringBuilder();
            } else {
                cur.append(c);
            }
        }
        fields.add(cur.toString());
        return fields.toArray(new String[0]);
    }
}
