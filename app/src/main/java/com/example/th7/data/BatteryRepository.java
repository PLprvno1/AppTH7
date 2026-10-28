package com.example.th7.data;

import com.example.th7.R;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import com.example.th7.model.BatterySnapshot;
import com.example.th7.data.AppSettings;
import com.example.th7.model.ChargingSession;
import com.example.th7.model.NotificationItem;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/** Local-only store for samples, observed charging sessions, and in-app notifications. */
public final class BatteryRepository extends SQLiteOpenHelper {
    private static final String DB = "battery_smart_lab.db";
    private final Context appContext;
    private static final int VERSION = 3;
    private static final long SAMPLE_INTERVAL_MS = 5 * 60 * 1000L;
    public BatteryRepository(Context context) { this(context, DB); }
    public BatteryRepository(Context context, String databaseName) { super(context.getApplicationContext(), databaseName, null, VERSION); this.appContext=context.getApplicationContext(); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE sessions (id INTEGER PRIMARY KEY AUTOINCREMENT, start_ms INTEGER NOT NULL, end_ms INTEGER NOT NULL DEFAULT 0, start_level INTEGER NOT NULL, end_level INTEGER NOT NULL DEFAULT -1, source TEXT NOT NULL, starred INTEGER NOT NULL DEFAULT 0, end_observed INTEGER NOT NULL DEFAULT 1)");
        db.execSQL("CREATE TABLE samples (id INTEGER PRIMARY KEY AUTOINCREMENT, sampled_at INTEGER NOT NULL, level INTEGER NOT NULL, voltage_v REAL, current_ma REAL)");
        createNotifications(db);
    }
    private void createNotifications(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS notifications (id INTEGER PRIMARY KEY AUTOINCREMENT, created_at INTEGER NOT NULL, title TEXT NOT NULL, message TEXT NOT NULL, category TEXT NOT NULL, is_read INTEGER NOT NULL DEFAULT 0)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE sessions ADD COLUMN end_observed INTEGER NOT NULL DEFAULT 1");
            createNotifications(db);
        }
        if (oldVersion < 3) db.execSQL("DELETE FROM notifications WHERE category IN ('HIGH_TEMPERATURE','BATTERY_TEMPERATURE','OVERHEAT_TEMPERATURE')");
    }

    public synchronized long startSession(BatterySnapshot s) {
        SQLiteDatabase db = getWritableDatabase();
        Cursor c = db.rawQuery("SELECT id FROM sessions WHERE end_ms=0 ORDER BY start_ms DESC LIMIT 1", null);
        try { if (c.moveToFirst()) return c.getLong(0); } finally { c.close(); }
        ContentValues v = new ContentValues(); v.put("start_ms", s.timestamp); v.put("start_level", s.level); v.put("source", source(s.plugged));
        return db.insert("sessions", null, v);
    }
    public synchronized void updateOpenSession(BatterySnapshot s) {
        getWritableDatabase().execSQL("UPDATE sessions SET end_level=? WHERE end_ms=0", new Object[]{s.level});
    }
    public synchronized void finishSession(BatterySnapshot s, boolean endObserved) {
        updateOpenSession(s);
        getWritableDatabase().execSQL("UPDATE sessions SET end_ms=?, end_observed=CASE WHEN ?=1 AND start_ms<? THEN 1 ELSE 0 END WHERE end_ms=0", new Object[]{s.timestamp, endObserved ? 1 : 0, s.timestamp});
    }
    public synchronized void recoverOpenSession(BatterySnapshot current) {
        if (current != null && !current.isPlugged()
                && current.statusType() != BatteryStateMapper.Status.CHARGING
                && current.statusType() != BatteryStateMapper.Status.FULL) finishSession(current, false);
    }
    public synchronized void recordSample(BatterySnapshot s) {
        SQLiteDatabase db = getWritableDatabase();
        Cursor c = db.rawQuery("SELECT sampled_at,level,voltage_v,current_ma FROM samples ORDER BY sampled_at DESC LIMIT 1", null);
        boolean insert = true;
        try {
            if (c.moveToFirst()) {
                long age = s.timestamp - c.getLong(0);
                boolean changed = c.getInt(1) != s.level || differs(c, 2, s.voltageV, 0.1f);
                insert = age >= SAMPLE_INTERVAL_MS || changed;
            }
        } finally { c.close(); }
        if (insert) {
            ContentValues v = new ContentValues(); v.put("sampled_at", s.timestamp); v.put("level", s.level);
            if (s.voltageV != null) v.put("voltage_v", s.voltageV); if (s.currentMa != null) v.put("current_ma", s.currentMa);
            db.insert("samples", null, v);
        }
        db.execSQL("DELETE FROM samples WHERE sampled_at < ?", new Object[]{s.timestamp - 30L * 24 * 60 * 60 * 1000});
    }
    private boolean differs(Cursor c, int column, Float value, float threshold) {
        if (value == null) return !c.isNull(column);
        return c.isNull(column) || Math.abs(c.getFloat(column) - value) >= threshold;
    }
    public synchronized ArrayList<BatterySnapshot> getSamplesSince(long since) {
        ArrayList<BatterySnapshot> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT sampled_at,level,voltage_v,current_ma FROM samples WHERE sampled_at>=? ORDER BY sampled_at ASC", new String[]{String.valueOf(since)});
        try { while (c.moveToNext()) out.add(new BatterySnapshot(c.getInt(1), -1, 0, c.isNull(2) ? null : c.getFloat(2), c.isNull(3) ? null : c.getFloat(3), null, null, -1, c.getLong(0))); }
        finally { c.close(); } return out;
    }
    private Context getContext() { return appContext; }

    public synchronized ArrayList<ChargingSession> getSessions() { return getSessionsSince(0L); }
    public synchronized ArrayList<ChargingSession> getSessionsSince(long since) {
        ArrayList<ChargingSession> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT id,start_ms,end_ms,start_level,end_level,source,starred,end_observed FROM sessions WHERE start_ms>=? ORDER BY start_ms DESC", new String[]{String.valueOf(since)});
        SimpleDateFormat fmt = new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault());
        try { while (c.moveToNext()) {
            long start = c.getLong(1), end = c.getLong(2); boolean open = end == 0, invalidEnd = !open && end <= start;
            long minutes = invalidEnd ? 0 : Math.max(0, ((open ? System.currentTimeMillis() : end) - start) / 60000);
            Context context = getContext();
            String endLabel = open ? context.getString(R.string.session_end_in_progress) : invalidEnd ? context.getString(R.string.session_end_unknown) : fmt.format(new Date(end));
            String range = context.getString(R.string.session_range_format, fmt.format(new Date(start)), endLabel);
            if (!open && (c.getInt(7) == 0 || invalidEnd)) range += " • " + context.getString(R.string.session_end_not_observed);
            String duration = invalidEnd ? context.getString(R.string.history_average_unknown) : minutes < 1 ? context.getString(R.string.history_duration_under_minute) : minutes < 60 ? context.getString(R.string.session_duration_minutes, minutes) : context.getString(R.string.session_duration_hours_minutes, minutes / 60, minutes % 60);
            ChargingSession item = new ChargingSession(String.valueOf(c.getLong(0)), range, duration, c.getInt(3), c.getInt(4) < 0 ? c.getInt(3) : c.getInt(4), c.getString(5), start, invalidEnd ? 0 : end, c.getInt(7) != 0 && !invalidEnd);
            item.setStarred(c.getInt(6) != 0); out.add(item);
        }} finally { c.close(); } return out;
    }
    public synchronized int getSessionCountSince(long since) {
        Cursor c=getReadableDatabase().rawQuery("SELECT COUNT(*) FROM sessions WHERE start_ms>=?",new String[]{String.valueOf(since)});
        try { return c.moveToFirst()?c.getInt(0):0; } finally { c.close(); }
    }
    public synchronized long getAverageDurationMinutes(long since) {
        Cursor c=getReadableDatabase().rawQuery("SELECT AVG(end_ms-start_ms) FROM sessions WHERE start_ms>=? AND end_ms>start_ms",new String[]{String.valueOf(since)});
        try { return c.moveToFirst()&&!c.isNull(0)?c.getLong(0)/60000L:-1L; } finally { c.close(); }
    }    public synchronized void setStarred(String id, boolean starred) { ContentValues v = new ContentValues(); v.put("starred", starred); getWritableDatabase().update("sessions", v, "id=?", new String[]{id}); }
    public synchronized void deleteSession(String id) { getWritableDatabase().delete("sessions", "id=?", new String[]{id}); }
    public synchronized void clearSessions() { getWritableDatabase().delete("sessions", null, null); }

    public synchronized boolean addNotification(String title, String message, String category, long cooldownMs) {
        SQLiteDatabase db = getWritableDatabase(); long now = System.currentTimeMillis();
        Cursor c = db.rawQuery("SELECT created_at FROM notifications WHERE category=? ORDER BY created_at DESC LIMIT 1", new String[]{category});
        try { if (c.moveToFirst() && now - c.getLong(0) < cooldownMs) return false; } finally { c.close(); }
        ContentValues v = new ContentValues(); v.put("created_at", now); v.put("title", title); v.put("message", message); v.put("category", category); v.put("is_read", 0);
        db.insert("notifications", null, v); return true;
    }
    public synchronized ArrayList<NotificationItem> getNotifications() {
        ArrayList<NotificationItem> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("SELECT id,created_at,title,message,category,is_read FROM notifications ORDER BY created_at DESC", null);
        SimpleDateFormat fmt = new SimpleDateFormat("dd/MM HH:mm", Locale.getDefault());
        try { while (c.moveToNext()) out.add(new NotificationItem(String.valueOf(c.getLong(0)), c.getString(2), c.getString(3), fmt.format(new Date(c.getLong(1))), c.getString(4), c.getInt(5) != 0)); }
        finally { c.close(); } return out;
    }
    public synchronized void markNotificationRead(String id) { ContentValues v = new ContentValues(); v.put("is_read", 1); getWritableDatabase().update("notifications", v, "id=?", new String[]{id}); }
    public synchronized void markAllNotificationsRead() { ContentValues v = new ContentValues(); v.put("is_read", 1); getWritableDatabase().update("notifications", v, null, null); }
    public synchronized void deleteNotification(String id) { getWritableDatabase().delete("notifications", "id=?", new String[]{id}); }
    public synchronized void clearNotifications() { getWritableDatabase().delete("notifications", null, null); }
    private String source(int plugged) {
        switch (BatteryStateMapper.source(plugged)) {
            case AC: return "AC";
            case USB: return "USB";
            case WIRELESS: return "Wireless";
            default: return "Other";
        }
    }
}

