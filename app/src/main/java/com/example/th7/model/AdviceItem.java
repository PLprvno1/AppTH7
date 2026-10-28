package com.example.th7.model;

public class AdviceItem {
    private String id;
    private String title;
    private String content;
    private String category; // Charging, power, battery state, or general advice.
    private String priority; // "Cao", "Trung bình", "Khuyến nghị"
    private int iconRes;

    public AdviceItem(String id, String title, String content, String category, String priority, int iconRes) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.category = category;
        this.priority = priority;
        this.iconRes = iconRes;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public String getCategory() { return category; }
    public String getPriority() { return priority; }
    public int getIconRes() { return iconRes; }
}

