package com.example.th7.data;

/** Thermal state derived solely from Android's battery health status. */
public final class BatteryThermalState {
    public enum State { NORMAL, OVERHEAT, UNKNOWN }
    private BatteryThermalState() { }
    public static State fromHealth(BatteryStateMapper.Health health) {
        if (health == null || health == BatteryStateMapper.Health.UNKNOWN
                || health == BatteryStateMapper.Health.UNAVAILABLE
                || health == BatteryStateMapper.Health.UNRECOGNIZED) return State.UNKNOWN;
        return health == BatteryStateMapper.Health.OVERHEAT ? State.OVERHEAT : State.NORMAL;
    }
}
