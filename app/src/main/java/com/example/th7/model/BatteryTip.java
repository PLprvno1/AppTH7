package com.example.th7.model;

public final class BatteryTip {
    public final String id;
    public final String title;
    public final String description;
    public final String status;
    public final String actionLabel;
    public final int iconRes;
    public final boolean completed;
    public final boolean actionable;
    public final int brightnessPercent;

    public BatteryTip(String id, String title, String description, String status,
                      String actionLabel, int iconRes, boolean completed, boolean actionable,
                      int brightnessPercent) {
        this.id = id; this.title = title; this.description = description; this.status = status;
        this.actionLabel = actionLabel; this.iconRes = iconRes; this.completed = completed;
        this.actionable = actionable; this.brightnessPercent = brightnessPercent;
    }
}
