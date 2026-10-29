package com.example.th7.data;

import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Process;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Reads Android's aggregated foreground-time usage locally; no history is persisted or transmitted. */
public final class AppUsageReader {
    private AppUsageReader() { }

    public static boolean hasUsageAccess(Context context) {
        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        if (appOps == null) return false;
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(), context.getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    public static List<AppUsageRecord> read(Context context, long beginMs, long endMs) {
        if (!hasUsageAccess(context)) throw new SecurityException("Usage access is not granted");
        UsageStatsManager manager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (manager == null) throw new IllegalStateException("UsageStatsManager unavailable");

        // Daily buckets align with the UI's local-calendar ranges and avoid event-history retention limits.
        List<UsageStats> stats = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, beginMs, endMs);
        if (stats == null) throw new IllegalStateException("Usage statistics unavailable");
        Map<String, MutableUsage> byPackage = new HashMap<>();
        for (UsageStats stat : stats) {
            String packageName = stat.getPackageName();
            if (packageName == null || packageName.isEmpty()) continue;
            MutableUsage item = byPackage.computeIfAbsent(packageName, key -> new MutableUsage());
            item.foregroundMs += Math.max(0L, stat.getTotalTimeInForeground());
            item.lastActiveMs = Math.max(item.lastActiveMs, stat.getLastTimeUsed());
        }

        PackageManager pm = context.getPackageManager();
        ArrayList<AppUsageRecord> result = new ArrayList<>();
        for (Map.Entry<String, MutableUsage> entry : byPackage.entrySet()) {
            MutableUsage value = entry.getValue();
            if (value.foregroundMs <= 0L) continue;
            // Keep the list to launchable apps; system-only packages would otherwise appear by raw package ID.
            if (pm.getLaunchIntentForPackage(entry.getKey()) == null) continue;
            String label = entry.getKey();
            Drawable icon = null;
            try {
                ApplicationInfo info = pm.getApplicationInfo(entry.getKey(), 0);
                label = String.valueOf(pm.getApplicationLabel(info));
                icon = pm.getApplicationIcon(info);
            } catch (PackageManager.NameNotFoundException | RuntimeException ignored) {
                // Package visibility or an uninstall can hide metadata; retain the real package usage.
            }
            if (icon == null) icon = pm.getDefaultActivityIcon();
            result.add(new AppUsageRecord(entry.getKey(), label, icon,
                    value.foregroundMs, value.lastActiveMs));
        }
        Collections.sort(result, Comparator.comparingLong(AppUsageRecord::getForegroundMs).reversed());
        return result;
    }

    public static final class AppUsageRecord {
        private final String packageName;
        private final String label;
        private final Drawable icon;
        private final long foregroundMs;
        private final long lastActiveMs;

        AppUsageRecord(String packageName, String label, Drawable icon, long foregroundMs, long lastActiveMs) {
            this.packageName = packageName;
            this.label = label;
            this.icon = icon;
            this.foregroundMs = foregroundMs;
            this.lastActiveMs = lastActiveMs;
        }
        public String getPackageName() { return packageName; }
        public String getLabel() { return label; }
        public Drawable getIcon() { return icon; }
        public long getForegroundMs() { return foregroundMs; }
        public long getLastActiveMs() { return lastActiveMs; }
    }

    private static final class MutableUsage {
        long foregroundMs;
        long lastActiveMs;
    }
}
