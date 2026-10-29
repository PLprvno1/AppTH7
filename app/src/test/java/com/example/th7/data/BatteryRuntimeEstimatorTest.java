package com.example.th7.data;

import android.os.BatteryManager;
import com.example.th7.model.BatterySnapshot;
import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.assertEquals;

public class BatteryRuntimeEstimatorTest {
    private static final long HOUR = 60L * 60L * 1000L;

    @Test public void estimatesFromObservedDischargeRate() {
        long start = 1_000_000L;
        BatterySnapshot current = sample(50, start + 3 * HOUR);
        long estimate = BatteryRuntimeEstimator.estimateRemainingMinutes(combine(
                dischargeSeries(80, 70, start), dischargeSeries(70, 60, start + HOUR),
                dischargeSeries(60, 50, start + 2 * HOUR)),
                current, current.timestamp);
        assertEquals(300L, estimate);
    }

    @Test public void doesNotEstimateFromOneOrShortObservation() {
        long start = 1_000_000L;
        BatterySnapshot current = sample(79, start + 30L * 60L * 1000L);
        assertEquals(-1L, BatteryRuntimeEstimator.estimateRemainingMinutes(
                Collections.singletonList(sample(80, start)), current, current.timestamp));
    }

    @Test public void chargingSamplesCannotContributeToDischargeRate() {
        long start = 1_000_000L;
        BatterySnapshot current = sample(60, start + 4 * HOUR);
        BatterySnapshot charging = new BatterySnapshot(82, BatteryManager.BATTERY_STATUS_CHARGING,
                BatteryManager.BATTERY_PLUGGED_USB, null, null, null, null, -1, start + HOUR + HOUR / 2);
        long estimate = BatteryRuntimeEstimator.estimateRemainingMinutes(combine(
                dischargeSeries(90, 80, start), Collections.singletonList(charging),
                dischargeSeries(80, 70, start + 2 * HOUR),
                dischargeSeries(70, 60, start + 3 * HOUR)),
                current, current.timestamp);
        assertEquals(360L, estimate);
    }

    @Test public void rejectsInvalidCurrentAndNonDischargingCurrent() {
        long now = 2_000_000L;
        BatterySnapshot charging = new BatterySnapshot(50, BatteryManager.BATTERY_STATUS_CHARGING,
                BatteryManager.BATTERY_PLUGGED_AC, null, null, null, null, -1, now);
        BatterySnapshot invalidLevel = sample(101, now);
        assertEquals(-1L, BatteryRuntimeEstimator.estimateRemainingMinutes(
                Collections.singletonList(sample(70, now - HOUR)), charging, now));
        assertEquals(-1L, BatteryRuntimeEstimator.estimateRemainingMinutes(
                Collections.singletonList(sample(70, now - HOUR)), invalidLevel, now));
    }

    @Test public void rejectsZeroAndUsesOnlyTheLatestDischargingSegmentAfterRise() {
        long start = 1_000_000L;
        BatterySnapshot empty = sample(0, start + 2 * HOUR);
        assertEquals(-1L, BatteryRuntimeEstimator.estimateRemainingMinutes(Arrays.asList(
                sample(50, start), sample(40, start + HOUR)), empty, empty.timestamp));

        BatterySnapshot rising = sample(75, start + 2 * HOUR);
        assertEquals(-1L, BatteryRuntimeEstimator.estimateRemainingMinutes(Arrays.asList(
                sample(70, start), sample(80, start + HOUR)), rising, rising.timestamp));
    }

    private java.util.List<BatterySnapshot> dischargeSeries(int startLevel, int endLevel, long start) {
        java.util.ArrayList<BatterySnapshot> samples = new java.util.ArrayList<>();
        for (int step = 0; step <= 6; step++) {
            int level = startLevel - Math.round((startLevel - endLevel) * step / 6.0f);
            samples.add(sample(level, start + step * (HOUR / 6)));
        }
        return samples;
    }

    @SafeVarargs private final List<BatterySnapshot> combine(List<BatterySnapshot>... groups) {
        ArrayList<BatterySnapshot> samples = new ArrayList<>();
        for (List<BatterySnapshot> group : groups) samples.addAll(group);
        return samples;
    }

    private BatterySnapshot sample(int level, long timestamp) {
        return new BatterySnapshot(level, BatteryManager.BATTERY_STATUS_DISCHARGING, 0,
                null, null, null, null, -1, timestamp);
    }
}
