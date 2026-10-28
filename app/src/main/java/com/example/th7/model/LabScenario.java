package com.example.th7.model;

public class LabScenario {
    private String id;
    private String title;
    private String description;
    private int iconRes;
    private int dischargeRateMa; // e.g. -350, -1850, +4500
    private String remainingTimeEstimate;

    public LabScenario(String id, String title, String description, int iconRes, int dischargeRateMa, String remainingTimeEstimate) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.iconRes = iconRes;
        this.dischargeRateMa = dischargeRateMa;
        this.remainingTimeEstimate = remainingTimeEstimate;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public int getIconRes() { return iconRes; }
    public int getDischargeRateMa() { return dischargeRateMa; }
    public String getRemainingTimeEstimate() { return remainingTimeEstimate; }
}

