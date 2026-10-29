package com.example.th7.data;

import com.example.th7.model.BatterySnapshot;
import java.util.ArrayList;
import java.util.List;

/** Emits battery alerts when the device enters a qualifying state. */
public final class BatteryEventPolicy {
    public enum Event { NONE, LOW_BATTERY, BATTERY_FULL, OVERHEAT, RAPID_DRAIN }
    private static final long RAPID_DRAIN_WINDOW_MS = 30L * 60L * 1000L;
    private static final long RAPID_DRAIN_MIN_DURATION_MS = 15L * 60L * 1000L;
    private static final int RAPID_DRAIN_MIN_DROP = 10;
    private static final int RAPID_DRAIN_MIN_SAMPLES = 3;

    private BatteryEventPolicy() { }

    public static List<Event> events(int previousLevel, boolean previousFull,
            BatteryThermalState.State previousThermal, boolean previousRapidDrain,
            int level, BatteryStateMapper.Status status, boolean plugged,
            BatteryThermalState.State thermal, boolean rapidDrain,
            boolean lowEnabled, boolean fullEnabled, boolean overheatEnabled) {
        ArrayList<Event> result = new ArrayList<>();
        if (overheatEnabled && thermal == BatteryThermalState.State.OVERHEAT
                && previousThermal != BatteryThermalState.State.OVERHEAT) {
            result.add(Event.OVERHEAT);
        }
        if (rapidDrain && !previousRapidDrain) result.add(Event.RAPID_DRAIN);

        boolean full = plugged && (status == BatteryStateMapper.Status.FULL || level >= 100);
        if (fullEnabled && full && !previousFull) result.add(Event.BATTERY_FULL);
        if (lowEnabled && level >= 0 && level <= 20 && (previousLevel < 0 || previousLevel > 20)) {
            result.add(Event.LOW_BATTERY);
        }
        return result;
    }

    /** Uses the same evidence threshold as the Coach rapid-drain advice. */
    public static boolean isRapidDrain(List<BatterySnapshot> samples, long from, long to) {
        if (samples == null) return false;
        BatterySnapshot first = null;
        BatterySnapshot last = null;
        int observedDischargeSamples = 0;
        for (BatterySnapshot sample : samples) {
            if (sample == null || sample.timestamp < from || sample.timestamp > to
                    || sample.level < 0 || sample.level > 100 || sample.isPlugged()
                    || sample.statusType() != BatteryStateMapper.Status.DISCHARGING) continue;
            if (first == null) first = sample;
            last = sample;
            observedDischargeSamples++;
        }
        if (observedDischargeSamples < RAPID_DRAIN_MIN_SAMPLES || first == null || last == null) return false;
        long elapsed = last.timestamp - first.timestamp;
        return elapsed >= RAPID_DRAIN_MIN_DURATION_MS
                && elapsed <= RAPID_DRAIN_WINDOW_MS
                && first.level - last.level >= RAPID_DRAIN_MIN_DROP;
    }
}
