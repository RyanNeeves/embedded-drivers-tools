package com.cdata.embeddeddrivers.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CsvTest {

    @Test
    void splitsSimpleLine() {
        assertArrayEquals(new String[] {"a", "b", "c"}, Csv.splitLine("a,b,c"));
    }

    @Test
    void respectsQuotedCommas() {
        assertArrayEquals(new String[] {"a", "b,c", "d"}, Csv.splitLine("a,\"b,c\",d"));
    }

    @Test
    void unescapesDoubledQuotes() {
        assertArrayEquals(new String[] {"say \"hi\"", "x"}, Csv.splitLine("\"say \"\"hi\"\"\",x"));
    }

    @Test
    void findsColumnIndex() {
        assertEquals(1, Csv.columnIndex("Date,Version,Notes", "Version"));
        assertEquals(-1, Csv.columnIndex("Date,Notes", "Version"));
    }
}
