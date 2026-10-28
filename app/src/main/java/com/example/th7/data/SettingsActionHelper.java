package com.example.th7.data;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

public final class SettingsActionHelper {
    public enum Action { DISPLAY, WIFI, BLUETOOTH, LOCATION, APP_INFO, BATTERY_OPTIMIZATION }
    public enum Result { OPENED, OPENED_GENERAL_SETTINGS, OPENED_APP_INFO_FALLBACK, UNAVAILABLE }
    private SettingsActionHelper() { }

    public static boolean open(Context context, Action action) {
        if (action == Action.BLUETOOTH) return openBluetooth(context) != Result.UNAVAILABLE;
        Intent intent;
        switch (action) {
            case DISPLAY: intent = new Intent(Settings.ACTION_DISPLAY_SETTINGS); break;
            case WIFI:
                if (android.os.Build.VERSION.SDK_INT >= 29) intent = new Intent("android.settings.panel.action.INTERNET_CONNECTIVITY");
                else intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
                break;
            case BLUETOOTH: intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS); break;
            case LOCATION: intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS); break;
            case APP_INFO:
                intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.getPackageName(), null)); break;
            case BATTERY_OPTIMIZATION:
                intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS); break;
            default: return false;
        }
        if (canHandle(context, intent)) return startSafely(context, intent);
        if (action == Action.WIFI) return startSafely(context, new Intent(Settings.ACTION_WIFI_SETTINGS));
        return false;
    }

    public static Result openAppBattery(Context context) {
        return openAppInfo(context);
    }

    public static Result openBatterySaver(Context context) {
        Intent batterySaverSettings = new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS);
        if (canHandle(context, batterySaverSettings) && startSafely(context, batterySaverSettings)) {
            return Result.OPENED;
        }

        Intent generalSettings = new Intent(Settings.ACTION_SETTINGS);
        if (canHandle(context, generalSettings) && startSafely(context, generalSettings)) {
            return Result.OPENED_GENERAL_SETTINGS;
        }
        return Result.UNAVAILABLE;
    }

    private static Result openAppInfo(Context context) {
        Intent appInfo = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.getPackageName(), null));
        return canHandle(context, appInfo) && startSafely(context, appInfo)
                ? Result.OPENED_APP_INFO_FALLBACK : Result.UNAVAILABLE;
    }

    public static Result openBluetooth(Context context) {
        Intent bluetoothSettings = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
        if (canHandle(context, bluetoothSettings) && startSafely(context, bluetoothSettings)) {
            return Result.OPENED;
        }

        // Keep the public Bluetooth settings action as the primary route on every
        // Android version. Some system Settings apps may map it to an intermediate
        // screen; the Coach shows the Pixel-specific route in that case.
        Intent generalSettings = new Intent(Settings.ACTION_SETTINGS);
        if (canHandle(context, generalSettings) && startSafely(context, generalSettings)) {
            return Result.OPENED_GENERAL_SETTINGS;
        }
        return Result.UNAVAILABLE;
    }
    private static boolean canHandle(Context context, Intent intent) {
        return intent.resolveActivity(context.getPackageManager()) != null;
    }

    private static boolean startSafely(Context context, Intent intent) {
        if (!canHandle(context, intent)) return false;
        try {
            context.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }
}
