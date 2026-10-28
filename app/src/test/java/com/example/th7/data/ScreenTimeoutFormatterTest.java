package com.example.th7.data;

import org.junit.Test;
import static org.junit.Assert.*;

public class ScreenTimeoutFormatterTest {
    @Test public void keepsSubMinuteTimeoutsInSeconds() {
        assertParts(15_000, 0, 15);
        assertParts(30_000, 0, 30);
        assertParts(59_000, 0, 59);
    }

    @Test public void splitsNonWholeMinuteTimeouts() {
        assertParts(60_000, 1, 0);
        assertParts(90_000, 1, 30);
    }

    @Test public void formatsCommonMinuteValues() {
        assertParts(120_000, 2, 0);
        assertParts(600_000, 10, 0);
        assertParts(1_800_000, 30, 0);
    }

    @Test public void invalidValuesAreUnknown() {
        assertNull(ScreenTimeoutFormatter.format(0));
        assertNull(ScreenTimeoutFormatter.format(-1));
    }

    private void assertParts(long millis, long minutes, int seconds) {
        ScreenTimeoutFormatter.Parts parts = ScreenTimeoutFormatter.format(millis);
        assertNotNull(parts);
        assertEquals(minutes, parts.minutes);
        assertEquals(seconds, parts.seconds);
    }
}
