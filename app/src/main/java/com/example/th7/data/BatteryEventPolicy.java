package com.example.th7.data;

/** Emits one event on entry into each configured battery state. */
public final class BatteryEventPolicy {
    public enum Event { NONE, LOW_BATTERY, BATTERY_FULL, OVERHEAT }
    private BatteryEventPolicy() { }
    public static Event next(int previousLevel, int previousStatus, BatteryThermalState.State previousThermal,
            int level, BatteryStateMapper.Status status, BatteryThermalState.State thermal,
            boolean lowEnabled, boolean fullEnabled, boolean overheatEnabled) {
        if (overheatEnabled && thermal == BatteryThermalState.State.OVERHEAT
                && previousThermal != BatteryThermalState.State.OVERHEAT) return Event.OVERHEAT;
        if (fullEnabled && status == BatteryStateMapper.Status.FULL
                && previousStatus != android.os.BatteryManager.BATTERY_STATUS_FULL) return Event.BATTERY_FULL;
        if (lowEnabled && level >= 0 && level <= 20 && (previousLevel < 0 || previousLevel > 20)) return Event.LOW_BATTERY;
        return Event.NONE;
    }
}
