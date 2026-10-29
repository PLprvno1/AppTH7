package com.example.th7.model;

/** A finding supported by observed Android data or stored battery samples. */
public final class BatteryFinding {
    public enum Severity { INFO, ADVISORY, WARNING, CRITICAL }

    public final String id;
    public final String title;
    public final String category;
    public final String evidence;
    public final String reason;
    public final Severity severity;

    public BatteryFinding(String id, String title, String category, String evidence,
                          String reason, Severity severity) {
        this.id = id;
        this.title = title;
        this.category = category;
        this.evidence = evidence;
        this.reason = reason;
        this.severity = severity;
    }
}
