package com.example.th7;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.th7.data.BatteryMonitor;
import com.example.th7.data.BatteryRepository;
import com.example.th7.model.BatterySnapshot;
import com.example.th7.model.NotificationItem;
import com.example.th7.model.ChargingSession;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.ArrayList;
import android.os.Process;
import android.content.Intent;
import android.os.BatteryManager;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class BatteryRepositoryInstrumentedTest {
    @Test public void repositoryPersistsRecordsFiltersSamplesAndHonorsCleanup() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String testDatabase = "battery_smart_lab_test_" + Process.myPid() + ".db";
        context.deleteDatabase(testDatabase);
        long now = System.currentTimeMillis();
        BatteryRepository repository = new BatteryRepository(context, testDatabase);
        try {
            BatterySnapshot old = new BatterySnapshot(50, 2, 0, 4f, null, null, null, -1, now - 40L * 24 * 60 * 60 * 1000);
            BatterySnapshot current = new BatterySnapshot(49, 2, 0, 4f, null, null, null, -1, now);
            repository.recordSample(old);
            repository.recordSample(current);

            BatterySnapshot charging = new BatterySnapshot(49, 2, 2, 4f, null, null, null, -1, now);
            long sessionId = repository.startSession(charging);
            assertTrue(sessionId > 0);
            assertEquals("An open session must be reused", sessionId, repository.startSession(charging));
            repository.finishSession(new BatterySnapshot(75, 3, 0, 4.1f, null, null, null, -1, now + 60_000), true);
            repository.setStarred(String.valueOf(sessionId), true);
            long invalidSessionId = repository.startSession(new BatterySnapshot(40, 2, 1, 4f, null, null, null, -1, now + 120_000));
            repository.finishSession(new BatterySnapshot(45, 3, 0, 4f, null, null, null, -1, now + 60_000), true);
            assertTrue(repository.addNotification("Test", "Persisted locally", "TEST", 0));
            assertFalse("Category cooldown should suppress duplicate notifications", repository.addNotification("Test", "Duplicate", "TEST", 60_000));
            assertTrue(repository.addNotification("Other", "Second notification", "OTHER", 0));
            repository.close();

            repository = new BatteryRepository(context, testDatabase);
            ArrayList<BatterySnapshot> samples = repository.getSamplesSince(now - 60_000);
            assertEquals(1, samples.size());
            assertEquals(49, samples.get(0).level);
            ArrayList<ChargingSession> sessions = repository.getSessions();
            assertEquals(2, sessions.size());
            assertEquals("45", String.valueOf(sessions.get(0).getEndPercent()));
            assertFalse(sessions.get(0).isEndTimeObserved());
            assertEquals(0, sessions.get(0).getEndedAt());
            assertEquals("Chưa xác định", sessions.get(0).getDuration());
            assertEquals("75", String.valueOf(sessions.get(1).getEndPercent()));
            assertTrue(sessions.get(1).isStarred());
            ArrayList<NotificationItem> notifications = repository.getNotifications();
            assertEquals(2, notifications.size());
            assertFalse(notifications.get(0).isRead());
            repository.markNotificationRead(notifications.get(0).getId());
            assertTrue(repository.getNotifications().get(0).isRead());
            repository.deleteNotification(notifications.get(0).getId());
            assertEquals(1, repository.getNotifications().size());
            repository.clearNotifications();
            assertTrue(repository.getNotifications().isEmpty());
            repository.deleteSession(String.valueOf(sessionId));
            repository.deleteSession(String.valueOf(invalidSessionId));
            assertTrue(repository.getSessions().isEmpty());
        } finally {
            repository.close();
            context.deleteDatabase(testDatabase);
        }
    }

    @Test public void batteryMonitorReadsHealthAndVoltageExtras() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent reading = new Intent(Intent.ACTION_BATTERY_CHANGED)
                .putExtra(BatteryManager.EXTRA_LEVEL, 50)
                .putExtra(BatteryManager.EXTRA_SCALE, 100)
                .putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_CHARGING)
                .putExtra(BatteryManager.EXTRA_PLUGGED, BatteryManager.BATTERY_PLUGGED_USB)
                .putExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_OVERHEAT)
                .putExtra(BatteryManager.EXTRA_VOLTAGE, 4200);
        com.example.th7.model.BatterySnapshot snapshot = BatteryMonitor.read(context, reading);
        assertEquals(50, snapshot.level);
        assertTrue(snapshot.isCharging());
        assertEquals(BatteryManager.BATTERY_HEALTH_OVERHEAT, snapshot.health);
        assertEquals(4.2f, snapshot.voltageV, 0.001f);

        com.example.th7.model.BatterySnapshot missing = BatteryMonitor.read(context,
                new Intent(Intent.ACTION_BATTERY_CHANGED)
                        .putExtra(BatteryManager.EXTRA_LEVEL, 10)
                        .putExtra(BatteryManager.EXTRA_SCALE, 100));
        assertNull(missing.voltageV);
        assertEquals("Không xác định", missing.statusLabel());
    }
}
