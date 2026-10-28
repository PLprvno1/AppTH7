package com.example.th7.data;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BatterySaverModeTest {
    @Test public void mapsReportedEnabledDisabledAndUnavailableStates() {
        assertEquals(BatterySaverMode.State.ENABLED, BatterySaverMode.from(Boolean.TRUE));
        assertEquals(BatterySaverMode.State.DISABLED, BatterySaverMode.from(Boolean.FALSE));
        assertEquals(BatterySaverMode.State.UNKNOWN, BatterySaverMode.from(null));
    }
}
