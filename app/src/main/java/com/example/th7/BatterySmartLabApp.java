package com.example.th7;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import com.example.th7.data.AppSettings;
import com.example.th7.data.BatteryEventPolicy;
import com.example.th7.data.BatteryMonitor;
import com.example.th7.data.BatteryNotificationHelper;
import com.example.th7.data.BatteryRepository;
import com.example.th7.data.BatteryThermalState;
import com.example.th7.data.BatteryStateMapper;
import com.example.th7.model.BatterySnapshot;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BatterySmartLabApp extends Application {
    private final ExecutorService eventIo = Executors.newSingleThreadExecutor();
    @Override public void onCreate() {
        super.onCreate(); AppSettings.applyTheme(this); BatteryNotificationHelper.createChannels(this);
        ContextCompat.registerReceiver(this, new BroadcastReceiver() {
            @Override public void onReceive(android.content.Context context, Intent intent) {
                if (!Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) return;
                BatterySnapshot snapshot = BatteryMonitor.read(getApplicationContext(), intent);
                if (snapshot == null) return;
                eventIo.execute(() -> processBatteryEvent(snapshot));
            }
        }, new IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED);
    }
    private void processBatteryEvent(BatterySnapshot snapshot) {
        android.content.SharedPreferences p = getSharedPreferences("battery_event_state", MODE_PRIVATE);
        int oldLevel = p.getInt("level", -1);
        boolean previousFull = p.getBoolean("full_state", false);
        boolean previousRapidDrain = p.getBoolean("rapid_drain_active", false);
        int lastLowAlertLevel = p.getInt("low_alert_level", -1);
        BatteryThermalState.State oldThermal;
        try { oldThermal = BatteryThermalState.State.valueOf(p.getString("thermal", "UNKNOWN")); }
        catch (RuntimeException e) { oldThermal = BatteryThermalState.State.UNKNOWN; }
        BatteryThermalState.State thermal = BatteryThermalState.fromHealth(snapshot.healthType());
        BatteryStateMapper.Status status = snapshot.statusType();
        boolean full = snapshot.isPlugged() && (status == BatteryStateMapper.Status.FULL || snapshot.level >= 100);
        List<BatterySnapshot> drainSamples = recordDrainObservation(p, snapshot);
        boolean rapidDrain = BatteryEventPolicy.isRapidDrain(drainSamples,
                snapshot.timestamp - 30L * 60L * 1000L, snapshot.timestamp);
        List<BatteryEventPolicy.Event> events = BatteryEventPolicy.events(oldLevel, previousFull,
                oldThermal, previousRapidDrain, snapshot.level, status, snapshot.isPlugged(), thermal,
                rapidDrain, AppSettings.lowAlert(this), AppSettings.fullAlert(this), AppSettings.overheatAlert(this));
        boolean lowAlertEnabled = AppSettings.lowAlert(this);
        boolean shouldUpdateLowAlert = lowAlertEnabled && snapshot.level >= 0 && snapshot.level <= 20
                && snapshot.level != lastLowAlertLevel;
        if (shouldUpdateLowAlert && !events.contains(BatteryEventPolicy.Event.LOW_BATTERY)) {
            events.add(BatteryEventPolicy.Event.LOW_BATTERY);
        }
        android.content.SharedPreferences.Editor state = p.edit().putInt("level", snapshot.level).putInt("status", snapshot.status)
                .putString("thermal", thermal.name()).putBoolean("full_state", full)
                .putBoolean("rapid_drain_active", rapidDrain);
        if (!lowAlertEnabled || snapshot.level < 0 || snapshot.level > 20) state.putInt("low_alert_level", -1);
        else if (shouldUpdateLowAlert) state.putInt("low_alert_level", snapshot.level);
        state.apply();
        if (events.isEmpty()) return;

        BatteryRepository repository = new BatteryRepository(this);
        for (BatteryEventPolicy.Event event : events) {
            String category;
            int title;
            int message;
            if (event == BatteryEventPolicy.Event.OVERHEAT) {
                category = "OVERHEAT"; title = R.string.notification_overheat_title; message = R.string.notification_overheat_message;
            } else if (event == BatteryEventPolicy.Event.RAPID_DRAIN) {
                category = "RAPID_DRAIN"; title = R.string.notification_rapid_drain_title; message = R.string.notification_rapid_drain_message;
            } else if (event == BatteryEventPolicy.Event.LOW_BATTERY) {
                category = "LOW_BATTERY"; title = R.string.notification_low_battery_title; message = R.string.notification_low_battery_message;
            } else {
                category = "BATTERY_FULL"; title = R.string.notification_battery_full_title; message = R.string.notification_battery_full_message;
            }
            String body = event == BatteryEventPolicy.Event.LOW_BATTERY
                    ? getString(message, snapshot.level) : getString(message);
            try {
                long rowId = event == BatteryEventPolicy.Event.LOW_BATTERY
                        ? repository.updateLatestNotification(getString(title), body, category)
                        : repository.addNotificationImmediately(getString(title), body, category);
                if (rowId > 0) {
                    int notificationId = 1_000_000 + (int)(rowId % 1_000_000_000L);
                    BatteryNotificationHelper.post(this, event, snapshot.level, notificationId);
                }
            } catch (RuntimeException ignored) {
                // Keep the Application receiver alive if local notification storage fails.
            }
        }
    }

    private List<BatterySnapshot> recordDrainObservation(android.content.SharedPreferences preferences,
            BatterySnapshot snapshot) {
        final long maxAge = 30L * 60L * 1000L;
        final long minInterval = 5L * 60L * 1000L;
        ArrayList<BatterySnapshot> recent = new ArrayList<>();
        String stored = preferences.getString("drain_observations", "");
        if (!stored.isEmpty()) {
            for (String row : stored.split(";")) {
                String[] fields = row.split(",");
                if (fields.length != 4) continue;
                try {
                    long time = Long.parseLong(fields[0]);
                    if (time < snapshot.timestamp - maxAge || time > snapshot.timestamp) continue;
                    recent.add(new BatterySnapshot(Integer.parseInt(fields[1]), Integer.parseInt(fields[2]),
                            Integer.parseInt(fields[3]), null, null, null, null,
                            Integer.MIN_VALUE, time));
                } catch (NumberFormatException ignored) {
                    // Ignore malformed persisted observations.
                }
            }
        }
        BatterySnapshot last = recent.isEmpty() ? null : recent.get(recent.size() - 1);
        boolean changed = last == null || last.level != snapshot.level || last.status != snapshot.status
                || last.plugged != snapshot.plugged || snapshot.timestamp - last.timestamp >= minInterval;
        if (changed) recent.add(snapshot);
        StringBuilder encoded = new StringBuilder();
        for (BatterySnapshot sample : recent) {
            if (encoded.length() > 0) encoded.append(';');
            encoded.append(sample.timestamp).append(',').append(sample.level).append(',')
                    .append(sample.status).append(',').append(sample.plugged);
        }
        preferences.edit().putString("drain_observations", encoded.toString()).apply();
        return recent;
    }
}
