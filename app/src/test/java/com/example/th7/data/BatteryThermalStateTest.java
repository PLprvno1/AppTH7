package com.example.th7.data;
import android.os.BatteryManager;
import org.junit.Test;
import static org.junit.Assert.*;
public class BatteryThermalStateTest {
 @Test public void overheatHealthMapsToOverheat(){assertEquals(BatteryThermalState.State.OVERHEAT,BatteryThermalState.fromHealth(BatteryStateMapper.health(BatteryManager.BATTERY_HEALTH_OVERHEAT)));}
 @Test public void goodAndKnownNonOverheatHealthMapToNormal(){assertEquals(BatteryThermalState.State.NORMAL,BatteryThermalState.fromHealth(BatteryStateMapper.Health.GOOD));assertEquals(BatteryThermalState.State.NORMAL,BatteryThermalState.fromHealth(BatteryStateMapper.Health.DEAD));assertEquals(BatteryThermalState.State.NORMAL,BatteryThermalState.fromHealth(BatteryStateMapper.Health.OVER_VOLTAGE));}
 @Test public void unknownOrUnavailableHealthStaysUnknown(){assertEquals(BatteryThermalState.State.UNKNOWN,BatteryThermalState.fromHealth(BatteryStateMapper.Health.UNKNOWN));assertEquals(BatteryThermalState.State.UNKNOWN,BatteryThermalState.fromHealth(BatteryStateMapper.Health.UNAVAILABLE));assertEquals(BatteryThermalState.State.UNKNOWN,BatteryThermalState.fromHealth(BatteryStateMapper.Health.UNRECOGNIZED));}
}
