package com.example.th7.model;

/** A user-controlled action suggested from a battery finding. */
public final class BatteryRecommendation {
    public final String id;
    public final String title;
    public final String message;
    public final BatteryFinding.Severity priority;

    public BatteryRecommendation(String id, String title, String message,
                                  BatteryFinding.Severity priority) {
        this.id = id;
        this.title = title;
        this.message = message;
        this.priority = priority;
    }
}
