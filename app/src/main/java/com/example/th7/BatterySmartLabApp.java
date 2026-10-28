package com.example.th7;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import com.example.th7.data.AppSettings;
import com.example.th7.data.BatteryEventPolicy;
import com.example.th7.data.BatteryMonitor;
import com.example.th7.data.BatteryNotificationHelper;
import com.example.th7.data.BatteryRepository;
import com.example.th7.data.BatteryThermalState;
import com.example.th7.data.BatteryStateMapper;
import com.example.th7.model.BatterySnapshot;
import androidx.core.content.ContextCompat;
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
        int oldLevel = p.getInt("level", -1), oldStatus = p.getInt("status", Integer.MIN_VALUE);
        BatteryThermalState.State oldThermal;
        try { oldThermal = BatteryThermalState.State.valueOf(p.getString("thermal", "UNKNOWN")); }
        catch (RuntimeException e) { oldThermal = BatteryThermalState.State.UNKNOWN; }
        BatteryThermalState.State thermal = BatteryThermalState.fromHealth(snapshot.healthType());
        BatteryStateMapper.Status status = snapshot.statusType();
        BatteryEventPolicy.Event event = BatteryEventPolicy.next(oldLevel, oldStatus, oldThermal,
                snapshot.level, status, thermal, AppSettings.lowAlert(this), AppSettings.fullAlert(this), AppSettings.overheatAlert(this));
        p.edit().putInt("level", snapshot.level).putInt("status", snapshot.status).putString("thermal", thermal.name()).apply();
        if (thermal == BatteryThermalState.State.NORMAL) BatteryNotificationHelper.cancelOverheat(this);
        if (event == BatteryEventPolicy.Event.NONE) return;
        String category, title, body; long cooldown;
        if (event == BatteryEventPolicy.Event.OVERHEAT) {
            category="OVERHEAT"; title=getString(R.string.notification_overheat_title); body=getString(R.string.notification_overheat_message); cooldown=6L*60*60*1000;
        } else if (event == BatteryEventPolicy.Event.LOW_BATTERY) {
            category="LOW_BATTERY"; title=getString(R.string.notification_low_battery_title); body=getString(R.string.notification_low_battery_message,snapshot.level); cooldown=6L*60*60*1000;
        } else {
            category="BATTERY_FULL"; title=getString(R.string.notification_battery_full_title); body=getString(R.string.notification_battery_full_message); cooldown=12L*60*60*1000;
        }
        if (new BatteryRepository(this).addNotification(title, body, category, cooldown)) {
            BatteryNotificationHelper.post(this, event, snapshot.level);
        }
    }
}
