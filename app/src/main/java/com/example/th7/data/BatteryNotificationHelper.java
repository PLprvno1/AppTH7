package com.example.th7.data;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import com.example.th7.MainActivity;
import com.example.th7.R;

/** Shared notification channel setup and safe posting for battery events. */
public final class BatteryNotificationHelper {
    private static final String CHANNEL_ALERTS = "battery_important_alerts";
    private static final String CHANNEL_INFO = "battery_status_alerts";
    private BatteryNotificationHelper() { }
    public static void createChannels(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        manager.createNotificationChannel(new NotificationChannel(CHANNEL_ALERTS,
                context.getString(R.string.notification_channel_alerts), NotificationManager.IMPORTANCE_HIGH));
        manager.createNotificationChannel(new NotificationChannel(CHANNEL_INFO,
                context.getString(R.string.notification_channel_status), NotificationManager.IMPORTANCE_DEFAULT));
    }
    public static boolean post(Context context, BatteryEventPolicy.Event event, int level) {
        return post(context, event, level, defaultId(event));
    }
    public static boolean post(Context context, BatteryEventPolicy.Event event, int level, int notificationId) {
        createChannels(context);
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false;
        int title, message;
        switch (event) {
            case OVERHEAT: title=R.string.notification_overheat_title; message=R.string.notification_overheat_message; break;
            case LOW_BATTERY: title=R.string.notification_low_battery_title; message=R.string.notification_low_battery_message; break;
            case BATTERY_FULL: title=R.string.notification_battery_full_title; message=R.string.notification_battery_full_message; break;
            case RAPID_DRAIN: title=R.string.notification_rapid_drain_title; message=R.string.notification_rapid_drain_message; break;
            default: return false;
        }
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager manager=context.getSystemService(NotificationManager.class);
            NotificationChannel active=manager==null?null:manager.getNotificationChannel(CHANNEL_ALERTS);
            if(active==null||active.getImportance()==NotificationManager.IMPORTANCE_NONE)return false;
        }
        Intent launch = new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent content = PendingIntent.getActivity(context, notificationId, launch, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String body = event == BatteryEventPolicy.Event.LOW_BATTERY ? context.getString(message, level) : context.getString(message);
        try {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ALERTS)
                    .setSmallIcon(R.drawable.ic_battery_std).setContentTitle(context.getString(title)).setContentText(body)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(body)).setWhen(System.currentTimeMillis())
                    .setAutoCancel(true).setContentIntent(content).setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setOnlyAlertOnce(true);
            NotificationManagerCompat.from(context).notify(notificationId, builder.build());
            return true;
        } catch (SecurityException denied) { return false; }
    }

    private static int defaultId(BatteryEventPolicy.Event event) {
        switch (event) {
            case OVERHEAT: return 7101;
            case LOW_BATTERY: return 7102;
            case BATTERY_FULL: return 7103;
            case RAPID_DRAIN: return 7104;
            default: return 7100;
        }
    }
}
