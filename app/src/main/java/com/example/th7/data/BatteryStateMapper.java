package com.example.th7.data;

import android.os.BatteryManager;

/** Maps Android battery extras to explicit states without inventing missing values. */
public final class BatteryStateMapper {
    private BatteryStateMapper() { }

    public enum Status {
        UNKNOWN, CHARGING, DISCHARGING, NOT_CHARGING, FULL, UNAVAILABLE, UNRECOGNIZED
    }

    public enum Source {
        NONE, AC, USB, WIRELESS, OTHER, UNKNOWN, UNAVAILABLE, UNRECOGNIZED
    }

    public enum Health {
        UNKNOWN, GOOD, OVERHEAT, DEAD, OVER_VOLTAGE, UNSPECIFIED_FAILURE, COLD,
        UNAVAILABLE, UNRECOGNIZED
    }

    public enum ChargePath {
        AC, USB, WIRELESS, CHARGING_SOURCE_UNKNOWN, DISCHARGING, NOT_CHARGING,
        FULL, UNKNOWN, UNAVAILABLE, UNRECOGNIZED
    }

    public static Status status(int raw) {
        if (raw == Integer.MIN_VALUE) return Status.UNAVAILABLE;
        switch (raw) {
            case BatteryManager.BATTERY_STATUS_UNKNOWN: return Status.UNKNOWN;
            case BatteryManager.BATTERY_STATUS_CHARGING: return Status.CHARGING;
            case BatteryManager.BATTERY_STATUS_DISCHARGING: return Status.DISCHARGING;
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING: return Status.NOT_CHARGING;
            case BatteryManager.BATTERY_STATUS_FULL: return Status.FULL;
            default: return Status.UNRECOGNIZED;
        }
    }

    public static Source source(int raw) {
        if (raw == Integer.MIN_VALUE) return Source.UNAVAILABLE;
        if (raw == 0) return Source.NONE;
        switch (raw) {
            case BatteryManager.BATTERY_PLUGGED_AC: return Source.AC;
            case BatteryManager.BATTERY_PLUGGED_USB: return Source.USB;
            case BatteryManager.BATTERY_PLUGGED_WIRELESS: return Source.WIRELESS;
            case BatteryManager.BATTERY_PLUGGED_DOCK: return Source.OTHER;
            default: return Source.UNRECOGNIZED;
        }
    }

    public static Health health(int raw) {
        if (raw == Integer.MIN_VALUE) return Health.UNAVAILABLE;
        switch (raw) {
            case BatteryManager.BATTERY_HEALTH_UNKNOWN: return Health.UNKNOWN;
            case BatteryManager.BATTERY_HEALTH_GOOD: return Health.GOOD;
            case BatteryManager.BATTERY_HEALTH_OVERHEAT: return Health.OVERHEAT;
            case BatteryManager.BATTERY_HEALTH_DEAD: return Health.DEAD;
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE: return Health.OVER_VOLTAGE;
            case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE: return Health.UNSPECIFIED_FAILURE;
            case BatteryManager.BATTERY_HEALTH_COLD: return Health.COLD;
            default: return Health.UNRECOGNIZED;
        }
    }

    public static ChargePath classify(Status status, Source source) {
        if (status == null) return ChargePath.UNAVAILABLE;
        switch (status) {
            case CHARGING:
                if (source == null) return ChargePath.CHARGING_SOURCE_UNKNOWN;
                switch (source) {
                    case AC: return ChargePath.AC;
                    case USB: return ChargePath.USB;
                    case WIRELESS: return ChargePath.WIRELESS;
                    case NONE:
                    case UNKNOWN:
                    case UNAVAILABLE:
                    case OTHER:
                    case UNRECOGNIZED:
                    default: return ChargePath.CHARGING_SOURCE_UNKNOWN;
                }
            case DISCHARGING: return ChargePath.DISCHARGING;
            case NOT_CHARGING: return ChargePath.NOT_CHARGING;
            case FULL: return ChargePath.FULL;
            case UNKNOWN: return ChargePath.UNKNOWN;
            case UNAVAILABLE: return ChargePath.UNAVAILABLE;
            case UNRECOGNIZED: return ChargePath.UNRECOGNIZED;
            default: return ChargePath.UNRECOGNIZED;
        }
    }
}
