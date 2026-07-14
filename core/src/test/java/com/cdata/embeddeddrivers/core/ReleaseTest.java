package com.cdata.embeddeddrivers.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

class ReleaseTest {

    @Test
    void parsesLenientForms() {
        assertEquals(new Release(2025, 3), Release.parse("2025u3"));
        assertEquals(new Release(2025, 3), Release.parse("v25u3"));
        assertEquals(new Release(2025, 3), Release.parse("2025 U3"));
        assertEquals(new Release(2025, 3), Release.parse("2025-u3"));
        assertEquals(new Release(2026, 0), Release.parse("26u0"));
    }

    @Test
    void rejectsInvalidForms() {
        assertThrows(IllegalArgumentException.class, () -> Release.parse("2025"));
        assertThrows(IllegalArgumentException.class, () -> Release.parse("u3"));
        assertThrows(IllegalArgumentException.class, () -> Release.parse("latest"));
    }

    @Test
    void formatsTagAndLabel() {
        Release r = new Release(2025, 2);
        assertEquals("v25u2", r.tag());
        assertEquals("2025 U2", r.label());
    }

    @Test
    void sortsNewestFirst() {
        List<Release> releases = new ArrayList<>(List.of(
                new Release(2025, 1), new Release(2026, 0), new Release(2025, 3)));
        Collections.sort(releases);
        assertEquals(new Release(2026, 0), releases.get(0));
        assertEquals(new Release(2025, 3), releases.get(1));
        assertEquals(new Release(2025, 1), releases.get(2));
    }
}
