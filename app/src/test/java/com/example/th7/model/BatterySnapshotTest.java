package com.example.th7.model;

import android.os.BatteryManager;
import org.junit.Test;
import static org.junit.Assert.*;

public class BatterySnapshotTest {
    @Test public void reportsChargingSourceFromSystemPlugType() {
        BatterySnapshot snapshot = new BatterySnapshot(55, BatteryManager.BATTERY_STATUS_CHARGING,
                BatteryManager.BATTERY_PLUGGED_USB, null, null, null, null, -1, 1L);
        assertTrue(snapshot.isCharging());
        assertEquals("USB", snapshot.sourceLabel());
    }
    @Test public void pluggedButNotChargingStillReportsPowerSource() { BatterySnapshot s = new BatterySnapshot(100, BatteryManager.BATTERY_STATUS_NOT_CHARGING, BatteryManager.BATTERY_PLUGGED_AC, null, null, null, null, -1, 1L); assertFalse(s.isCharging()); assertTrue(s.isPlugged()); assertEquals("Nguồn điện AC", s.sourceLabel()); }
    @Test public void doesNotTreatUnknownStateAsCharging() {
        BatterySnapshot snapshot = new BatterySnapshot(55, BatteryManager.BATTERY_STATUS_UNKNOWN,
                0, null, null, null, null, -1, 1L);
        assertFalse(snapshot.isCharging());
        assertEquals("Không xác định", snapshot.statusLabel());
        assertEquals("Chưa kết nối bộ sạc", snapshot.sourceLabel());
    }
}
