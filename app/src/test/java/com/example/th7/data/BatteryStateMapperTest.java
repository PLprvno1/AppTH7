package com.example.th7.data;

import android.os.BatteryManager;
import org.junit.Test;

import static com.example.th7.data.BatteryStateMapper.*;
import static org.junit.Assert.assertEquals;

public class BatteryStateMapperTest {
    @Test public void mapsAllDocumentedBatteryStatusesAndInvalidValues() {
        assertEquals(Status.UNKNOWN, BatteryStateMapper.status(BatteryManager.BATTERY_STATUS_UNKNOWN));
        assertEquals(Status.CHARGING, BatteryStateMapper.status(BatteryManager.BATTERY_STATUS_CHARGING));
        assertEquals(Status.DISCHARGING, BatteryStateMapper.status(BatteryManager.BATTERY_STATUS_DISCHARGING));
        assertEquals(Status.NOT_CHARGING, BatteryStateMapper.status(BatteryManager.BATTERY_STATUS_NOT_CHARGING));
        assertEquals(Status.FULL, BatteryStateMapper.status(BatteryManager.BATTERY_STATUS_FULL));
        assertEquals(Status.UNAVAILABLE, BatteryStateMapper.status(Integer.MIN_VALUE));
        assertEquals(Status.UNRECOGNIZED, BatteryStateMapper.status(99));
    }

    @Test public void mapsEveryKnownPlugSourceWithoutTreatingNoneAsWireless() {
        assertEquals(Source.NONE, BatteryStateMapper.source(0));
        assertEquals(Source.AC, BatteryStateMapper.source(BatteryManager.BATTERY_PLUGGED_AC));
        assertEquals(Source.USB, BatteryStateMapper.source(BatteryManager.BATTERY_PLUGGED_USB));
        assertEquals(Source.WIRELESS, BatteryStateMapper.source(BatteryManager.BATTERY_PLUGGED_WIRELESS));
        assertEquals(Source.OTHER, BatteryStateMapper.source(BatteryManager.BATTERY_PLUGGED_DOCK));
        assertEquals(Source.UNAVAILABLE, BatteryStateMapper.source(Integer.MIN_VALUE));
        assertEquals(Source.UNRECOGNIZED, BatteryStateMapper.source(99));
    }

    @Test public void classifiesChargingPathAndPreservesStatusPrecedence() {
        assertEquals(ChargePath.AC, BatteryStateMapper.classify(Status.CHARGING, Source.AC));
        assertEquals(ChargePath.USB, BatteryStateMapper.classify(Status.CHARGING, Source.USB));
        assertEquals(ChargePath.WIRELESS, BatteryStateMapper.classify(Status.CHARGING, Source.WIRELESS));
        assertEquals(ChargePath.CHARGING_SOURCE_UNKNOWN, BatteryStateMapper.classify(Status.CHARGING, Source.NONE));
        assertEquals(ChargePath.CHARGING_SOURCE_UNKNOWN, BatteryStateMapper.classify(Status.CHARGING, Source.UNAVAILABLE));
        assertEquals(ChargePath.DISCHARGING, BatteryStateMapper.classify(Status.DISCHARGING, Source.AC));
        assertEquals(ChargePath.NOT_CHARGING, BatteryStateMapper.classify(Status.NOT_CHARGING, Source.USB));
        assertEquals(ChargePath.FULL, BatteryStateMapper.classify(Status.FULL, Source.WIRELESS));
        assertEquals(ChargePath.UNKNOWN, BatteryStateMapper.classify(Status.UNKNOWN, Source.AC));
        assertEquals(ChargePath.UNAVAILABLE, BatteryStateMapper.classify(Status.UNAVAILABLE, Source.USB));
        assertEquals(ChargePath.UNRECOGNIZED, BatteryStateMapper.classify(Status.UNRECOGNIZED, Source.AC));
    }

    @Test public void mapsAllAndroidHealthCodesIncludingColdAndMissingData() {
        assertEquals(Health.UNKNOWN, BatteryStateMapper.health(BatteryManager.BATTERY_HEALTH_UNKNOWN));
        assertEquals(Health.GOOD, BatteryStateMapper.health(BatteryManager.BATTERY_HEALTH_GOOD));
        assertEquals(Health.OVERHEAT, BatteryStateMapper.health(BatteryManager.BATTERY_HEALTH_OVERHEAT));
        assertEquals(Health.DEAD, BatteryStateMapper.health(BatteryManager.BATTERY_HEALTH_DEAD));
        assertEquals(Health.OVER_VOLTAGE, BatteryStateMapper.health(BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE));
        assertEquals(Health.UNSPECIFIED_FAILURE, BatteryStateMapper.health(BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE));
        assertEquals(Health.COLD, BatteryStateMapper.health(BatteryManager.BATTERY_HEALTH_COLD));
        assertEquals(Health.UNAVAILABLE, BatteryStateMapper.health(Integer.MIN_VALUE));
        assertEquals(Health.UNRECOGNIZED, BatteryStateMapper.health(99));
    }
}
