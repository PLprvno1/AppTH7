package com.example.th7.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.app.Activity;
import android.view.WindowManager;
import androidx.appcompat.app.AppCompatDelegate;
import com.example.th7.R;

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
    public static int savedWindowBrightness(Context c) {
        return prefs(c).getInt("window_brightness_percent", -1);
    }
    public static void setWindowBrightness(Activity activity, int percent) {
        int bounded = Math.max(1, Math.min(100, percent));
        WindowManager.LayoutParams attributes = activity.getWindow().getAttributes();
        attributes.screenBrightness = bounded / 100f;
        activity.getWindow().setAttributes(attributes);
    }
    public static void saveWindowBrightness(Context context, int percent) {
        prefs(context).edit().putInt("window_brightness_percent", Math.max(1, Math.min(100, percent))).apply();
    }
    public static int initialWindowBrightnessPercent(Activity activity) {
        int saved = savedWindowBrightness(activity);
        if (saved >= 1 && saved <= 100) return saved;
        float windowValue = activity.getWindow().getAttributes().screenBrightness;
        if (windowValue >= 0f && windowValue <= 1f) return Math.max(1, Math.round(windowValue * 100f));
        try {
            int systemValue = android.provider.Settings.System.getInt(activity.getContentResolver(),
                    android.provider.Settings.System.SCREEN_BRIGHTNESS);
            if (systemValue < 1 || systemValue > 255) return -1;
            return Math.max(1, Math.min(100, Math.round(systemValue * 100f / 255f)));
        } catch (android.provider.Settings.SettingNotFoundException | SecurityException e) {
            return -1;
        }
    }
    public static void applyWindowBrightness(Activity activity) {
        int percent = savedWindowBrightness(activity);
        if (percent < 1 || percent > 100) return;
        WindowManager.LayoutParams attributes = activity.getWindow().getAttributes();
        attributes.screenBrightness = percent / 100f;
        activity.getWindow().setAttributes(attributes);
    }
}
