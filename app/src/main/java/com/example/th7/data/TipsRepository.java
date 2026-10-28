package com.example.th7.data;

import android.content.Context;
import android.content.SharedPreferences;

public final class TipsRepository {
    private static final String FILE = "battery_tips_checklist";
    private final SharedPreferences preferences;
    public TipsRepository(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }
    public boolean isCompleted(String id) { return preferences.getBoolean(id, false); }
    public void setCompleted(String id, boolean completed) { preferences.edit().putBoolean(id, completed).apply(); }
}
