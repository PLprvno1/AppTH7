package com.example.th7.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.ext.SdkExtensions;
import android.provider.Settings;
import android.view.Display;
import androidx.appcompat.app.AppCompatDelegate;

public final class AppSettings {
    private static final String FILE = "battery_smart_settings";
    private AppSettings() { }
    private static SharedPreferences prefs(Context c) { return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE); }
    public static boolean darkMode(Context c) {
        int mode = getThemeMode(c);
        if (mode == AppCompatDelegate.MODE_NIGHT_YES) return true;
        if (mode == AppCompatDelegate.MODE_NIGHT_NO) return false;
        return (c.getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }
    public static boolean animations(Context c) { return prefs(c).getBoolean("animations", true); }
    public static boolean lowAlert(Context c) { return prefs(c).getBoolean("low_alert", true); }
    public static boolean fullAlert(Context c) { return prefs(c).getBoolean("full_alert", true); }
    public static boolean overheatAlert(Context c) {
        SharedPreferences p=prefs(c);
        if(p.contains("overheat_alert"))return p.getBoolean("overheat_alert",true);
        boolean legacy=p.getBoolean("hot_alert",true);
        p.edit().putBoolean("overheat_alert",legacy).remove("hot_alert").apply();
        return legacy;
    }
    public static void set(Context c, String key, boolean value) { prefs(c).edit().putBoolean(key, value).apply(); }
    public static int getThemeMode(Context c) {
        int mode = prefs(c).getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        return mode == AppCompatDelegate.MODE_NIGHT_YES || mode == AppCompatDelegate.MODE_NIGHT_NO
                || mode == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                ? mode : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
    }
    public static void setThemeMode(Context c, int mode) {
        if (mode != AppCompatDelegate.MODE_NIGHT_YES && mode != AppCompatDelegate.MODE_NIGHT_NO
                && mode != AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) {
            mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        }
        prefs(c).edit().putInt("theme_mode", mode).apply();
    }
    public static void applyTheme(Context c) { AppCompatDelegate.setDefaultNightMode(getThemeMode(c)); }
    public static boolean canWriteSystemBrightness(Context context) {
        return Settings.System.canWrite(context);
    }
    /**
     * Android 16 QPR1 (36.1) added a percentage unit that uses the same user-perception
     * scale as the system brightness slider. Earlier SCREEN_BRIGHTNESS values are a
     * device backlight range and cannot be converted reliably to the slider percentage.
     */
    public static boolean supportsSystemBrightnessPercent() {
        if (Build.VERSION.SDK_INT >= 37) return true;
        return Build.VERSION.SDK_INT >= 36
                && SdkExtensions.getExtensionVersion(Build.VERSION_CODES.BAKLAVA) >= 20;
    }
    public static int systemBrightnessPercent(Context context) {
        if (!supportsSystemBrightnessPercent()) return -1;
        try {
            DisplayManager manager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
            if (manager == null) return -1;
            float value = manager.getBrightness(Display.DEFAULT_DISPLAY, DisplayManager.BRIGHTNESS_UNIT_PERCENTAGE);
            if (Float.isNaN(value) || Float.isInfinite(value) || value < 0f || value > 100f) return -1;
            return Math.round(value);
        } catch (RuntimeException e) {
            return -1;
        }
    }
    public static boolean setSystemBrightness(Context context, int percent) {
        if (!supportsSystemBrightnessPercent() || !canWriteSystemBrightness(context)) return false;
        int bounded = Math.max(0, Math.min(100, percent));
        try {
            DisplayManager manager = (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
            if (manager == null) return false;
            manager.setBrightness(Display.DEFAULT_DISPLAY, bounded, DisplayManager.BRIGHTNESS_UNIT_PERCENTAGE);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
