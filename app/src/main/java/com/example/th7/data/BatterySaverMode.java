package com.example.th7.data;

/** Represents the system-reported Battery Saver state without inferring it from UI actions. */
public final class BatterySaverMode {
    public enum State { ENABLED, DISABLED, UNKNOWN }

    private BatterySaverMode() { }

    public static State from(Boolean enabled) {
        if (enabled == null) return State.UNKNOWN;
        return enabled ? State.ENABLED : State.DISABLED;
    }
}
