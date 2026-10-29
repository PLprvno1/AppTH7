package com.example.th7.data;

import android.os.BatteryManager;
import com.example.th7.model.BatterySnapshot;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class BatteryEventPolicyTest {
    @Test public void alertsWhenEnteringOverheatAndNotOnEveryRepeatedReading() {
        assertEquals(Collections.singletonList(BatteryEventPolicy.Event.OVERHEAT), BatteryEventPolicy.events(
                70, false, BatteryThermalState.State.NORMAL, false, 70,
                BatteryStateMapper.Status.DISCHARGING, false, BatteryThermalState.State.OVERHEAT,
                false, true, true, true));
        assertTrue(BatteryEventPolicy.events(70, false, BatteryThermalState.State.OVERHEAT, false,
                70, BatteryStateMapper.Status.DISCHARGING, false, BatteryThermalState.State.OVERHEAT,
                false, true, true, true).isEmpty());
    }

    @Test public void alertsWhenLevelCrossesTwentyAndDoesNotRepeatBelowThreshold() {
        assertEquals(Collections.singletonList(BatteryEventPolicy.Event.LOW_BATTERY), BatteryEventPolicy.events(
                21, false, BatteryThermalState.State.NORMAL, false, 20,
                BatteryStateMapper.Status.DISCHARGING, false, BatteryThermalState.State.NORMAL,
                false, true, true, true));
        assertTrue(BatteryEventPolicy.events(20, false, BatteryThermalState.State.NORMAL, false, 19,
                BatteryStateMapper.Status.DISCHARGING, false, BatteryThermalState.State.NORMAL,
                false, true, true, true).isEmpty());
    }

    @Test public void fullAlertSupportsFullStatusOrPluggedAtOneHundredPercent() {
        List<BatteryEventPolicy.Event> expected = Collections.singletonList(BatteryEventPolicy.Event.BATTERY_FULL);
        assertEquals(expected, BatteryEventPolicy.events(99, false, BatteryThermalState.State.NORMAL, false, 100,
                BatteryStateMapper.Status.CHARGING, true, BatteryThermalState.State.NORMAL, false, true, true, true));
        assertEquals(expected, BatteryEventPolicy.events(99, false, BatteryThermalState.State.NORMAL, false, 100,
                BatteryStateMapper.Status.FULL, true, BatteryThermalState.State.NORMAL, false, true, true, true));
        assertTrue(BatteryEventPolicy.events(100, true, BatteryThermalState.State.NORMAL, false, 100,
                BatteryStateMapper.Status.FULL, true, BatteryThermalState.State.NORMAL, false, true, true, true).isEmpty());
    }

    @Test public void multipleQualifyingConditionsCanNotifyTogether() {
        List<BatteryEventPolicy.Event> events = BatteryEventPolicy.events(21, false,
                BatteryThermalState.State.NORMAL, false, 20, BatteryStateMapper.Status.DISCHARGING,
                false, BatteryThermalState.State.OVERHEAT, true, true, true, true);
        assertEquals(Arrays.asList(BatteryEventPolicy.Event.OVERHEAT, BatteryEventPolicy.Event.RAPID_DRAIN,
                BatteryEventPolicy.Event.LOW_BATTERY), events);
    }

    @Test public void lowAlertStillRespectsItsSettingsSwitch() {
        assertTrue(BatteryEventPolicy.events(21, false, BatteryThermalState.State.NORMAL, false, 20,
                BatteryStateMapper.Status.DISCHARGING, false, BatteryThermalState.State.NORMAL,
                false, false, true, true).isEmpty());
    }

    @Test public void rapidDrainRequiresThreeDischargingSamplesAndAtLeastTenPointsInFifteenMinutes() {
        List<BatterySnapshot> samples = Arrays.asList(
                snapshot(90, 0), snapshot(85, 10 * 60_000L), snapshot(78, 20 * 60_000L));
        assertTrue(BatteryEventPolicy.isRapidDrain(samples, 0, 20 * 60_000L));
        assertFalse(BatteryEventPolicy.isRapidDrain(samples.subList(0, 2), 0, 20 * 60_000L));
        assertFalse(BatteryEventPolicy.isRapidDrain(Arrays.asList(snapshot(90, 0), snapshot(85, 10 * 60_000L),
                snapshot(82, 20 * 60_000L)), 0, 20 * 60_000L));
    }

    private BatterySnapshot snapshot(int level, long time) {
        return new BatterySnapshot(level, BatteryManager.BATTERY_STATUS_DISCHARGING, 0,
                null, null, null, null, BatteryManager.BATTERY_HEALTH_GOOD, time);
    }
}
