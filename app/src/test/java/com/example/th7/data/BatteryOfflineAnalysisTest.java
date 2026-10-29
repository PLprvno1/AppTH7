package com.example.th7.data;

import android.os.BatteryManager;
import com.example.th7.model.BatteryAnalysisResult;
import com.example.th7.model.BatteryFinding;
import com.example.th7.model.BatterySnapshot;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class BatteryOfflineAnalysisTest {
    @Test public void lowLevelIsAdvisoryAndNeverCriticalByItself() {
        BatterySnapshot current = snapshot(8, BatteryManager.BATTERY_STATUS_DISCHARGING, 20 * 60_000L);
        BatteryAnalysisResult result = BatteryCoachEngine.analyze(current,
                Collections.singletonList(snapshot(9, BatteryManager.BATTERY_STATUS_DISCHARGING, 10 * 60_000L)),
                Collections.emptyList(), false);
        assertEquals(BatteryFinding.Severity.ADVISORY, result.overallSeverity);
        assertFalse(hasFinding(result, "fast_drain"));
        assertTrue(hasFinding(result, "very_low"));
    }

    @Test public void chargingSamplesBreakDischargeObservation() {
        BatterySnapshot a = snapshot(90, BatteryManager.BATTERY_STATUS_DISCHARGING, 0);
        BatterySnapshot b = snapshot(80, BatteryManager.BATTERY_STATUS_CHARGING, 10 * 60_000L);
        BatterySnapshot c = snapshot(70, BatteryManager.BATTERY_STATUS_DISCHARGING, 20 * 60_000L);
        BatteryAnalysisResult result = BatteryCoachEngine.analyze(c, Arrays.asList(a, b),
                Collections.emptyList(), false);
        assertFalse(hasFinding(result, "fast_drain"));
    }

    @Test public void criticalSeverityRequiresAndroidDeadHealthCode() {
        BatterySnapshot dead = new BatterySnapshot(-1, BatteryManager.BATTERY_STATUS_DISCHARGING,
                0, null, null, null, null, BatteryManager.BATTERY_HEALTH_DEAD, 20 * 60_000L);
        BatteryAnalysisResult result = BatteryCoachEngine.analyze(dead,
                Collections.emptyList(), Collections.emptyList(), false);
        assertEquals(BatteryFinding.Severity.CRITICAL, result.overallSeverity);
        assertTrue(hasFinding(result, "health_dead"));
    }

    @Test public void missingHistoricalChargeStateIsNotTreatedAsDischarge() {
        BatterySnapshot oldA = snapshot(90, Integer.MIN_VALUE, 0);
        BatterySnapshot oldB = snapshot(80, Integer.MIN_VALUE, 10 * 60_000L);
        BatterySnapshot current = snapshot(70, BatteryManager.BATTERY_STATUS_DISCHARGING, 20 * 60_000L);
        BatteryAnalysisResult result = BatteryCoachEngine.analyze(current, Arrays.asList(oldA, oldB),
                Collections.emptyList(), false);
        assertFalse(hasFinding(result, "fast_drain"));
        assertTrue(result.unavailableData.contains("Trạng thái sạc chưa xác định ở một số mẫu"));
    }

    @Test public void sufficientOrdinaryDischargeWindowIsNotReportedAsFastDrain() {
        BatterySnapshot a = snapshot(80, BatteryManager.BATTERY_STATUS_DISCHARGING, 0);
        BatterySnapshot b = snapshot(79, BatteryManager.BATTERY_STATUS_DISCHARGING, 10 * 60_000L);
        BatterySnapshot c = snapshot(78, BatteryManager.BATTERY_STATUS_DISCHARGING, 20 * 60_000L);
        BatteryAnalysisResult result = BatteryCoachEngine.analyze(c, Arrays.asList(a, b),
                Collections.emptyList(), false);
        assertFalse(hasFinding(result, "fast_drain"));
        assertEquals(BatteryAnalysisResult.DataQuality.SUFFICIENT, result.dataQuality);
        assertEquals(3, result.observedSampleCount);
    }

    @Test public void outOfOrderSampleDoesNotReorderHistoryIntoAnAlert() {
        BatterySnapshot first = snapshot(90, BatteryManager.BATTERY_STATUS_DISCHARGING, 1_000L);
        BatterySnapshot outOfOrder = snapshot(40, BatteryManager.BATTERY_STATUS_DISCHARGING, 500L);
        BatterySnapshot current = snapshot(86, BatteryManager.BATTERY_STATUS_DISCHARGING, 20 * 60_000L);
        BatteryAnalysisResult result = BatteryCoachEngine.analyze(current,
                Arrays.asList(first, outOfOrder), Collections.emptyList(), false);
        assertFalse(hasFinding(result, "fast_drain"));
    }

    private static BatterySnapshot snapshot(int level, int status, long timestamp) {
        return new BatterySnapshot(level, status, 0, null, null, null, null,
                BatteryManager.BATTERY_HEALTH_GOOD, timestamp);
    }

    private static boolean hasFinding(BatteryAnalysisResult result, String id) {
        for (BatteryFinding finding : result.findings) if (id.equals(finding.id)) return true;
        return false;
    }
}
