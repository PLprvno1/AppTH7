package com.example.th7.data;
import org.junit.Test;import static org.junit.Assert.*;
public class BatteryUnitsTest {
 @Test public void levelPercentRejectsMissingAndOutOfRangeReadings(){assertEquals(50,BatteryUnits.levelPercent(1,2));assertEquals(0,BatteryUnits.levelPercent(0,100));assertEquals(100,BatteryUnits.levelPercent(100,100));assertEquals(-1,BatteryUnits.levelPercent(-1,100));assertEquals(-1,BatteryUnits.levelPercent(120,100));assertEquals(-1,BatteryUnits.levelPercent(1,0));}
 @Test public void voltageAndCurrentConvertMilliAndMicroUnits(){assertEquals(4.2f,BatteryUnits.voltageVolts(4200),0.001f);assertEquals(-850f,BatteryUnits.currentMilliamps(-850000),0.001f);assertNull(BatteryUnits.currentMilliamps(Integer.MIN_VALUE));assertNull(BatteryUnits.voltageVolts(0));}
}
