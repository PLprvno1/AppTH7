package com.example.th7.data;
import android.os.BatteryManager;
import org.junit.Test;
import static org.junit.Assert.*;
public class BatteryEventPolicyTest {
 @Test public void overheatOnlyNotifiesOnTransition(){assertEquals(BatteryEventPolicy.Event.OVERHEAT,BatteryEventPolicy.next(-1,-1,BatteryThermalState.State.UNKNOWN,70, BatteryStateMapper.Status.DISCHARGING,BatteryThermalState.State.OVERHEAT,true,true,true));assertEquals(BatteryEventPolicy.Event.NONE,BatteryEventPolicy.next(70,3,BatteryThermalState.State.OVERHEAT,70, BatteryStateMapper.Status.DISCHARGING,BatteryThermalState.State.OVERHEAT,true,true,true));}
 @Test public void normalToOverheatCanNotifyAgain(){assertEquals(BatteryEventPolicy.Event.OVERHEAT,BatteryEventPolicy.next(70,3,BatteryThermalState.State.NORMAL,69,BatteryStateMapper.Status.DISCHARGING,BatteryThermalState.State.OVERHEAT,true,true,true));}
 @Test public void disabledAlertDoesNotEmitEvent(){assertEquals(BatteryEventPolicy.Event.NONE,BatteryEventPolicy.next(70,3,BatteryThermalState.State.NORMAL,69,BatteryStateMapper.Status.DISCHARGING,BatteryThermalState.State.OVERHEAT,true,true,false));}
 @Test public void lowAndFullOnlyNotifyWhenEnteringState(){assertEquals(BatteryEventPolicy.Event.LOW_BATTERY,BatteryEventPolicy.next(21,3,BatteryThermalState.State.NORMAL,20,BatteryStateMapper.Status.DISCHARGING,BatteryThermalState.State.NORMAL,true,true,true));assertEquals(BatteryEventPolicy.Event.NONE,BatteryEventPolicy.next(20,3,BatteryThermalState.State.NORMAL,19,BatteryStateMapper.Status.DISCHARGING,BatteryThermalState.State.NORMAL,true,true,true));assertEquals(BatteryEventPolicy.Event.BATTERY_FULL,BatteryEventPolicy.next(99,3,BatteryThermalState.State.NORMAL,100,BatteryStateMapper.Status.FULL,BatteryThermalState.State.NORMAL,true,true,true));}
}
