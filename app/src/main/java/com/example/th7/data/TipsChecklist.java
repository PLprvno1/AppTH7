package com.example.th7.data;

import com.example.th7.model.BatteryTip;
import java.util.ArrayList;
import java.util.List;

/** Pure checklist operations, kept separate from Android preferences and views. */
public final class TipsChecklist {
    public enum Filter { ALL, INCOMPLETE, COMPLETED }

    private TipsChecklist() { }

    public static int completedCount(List<BatteryTip> tips) {
        int count = 0;
        if (tips != null) for (BatteryTip tip : tips) if (tip != null && tip.completed) count++;
        return count;
    }

    public static List<BatteryTip> filter(List<BatteryTip> tips, Filter filter) {
        ArrayList<BatteryTip> result = new ArrayList<>();
        if (tips == null) return result;
        Filter selected = filter == null ? Filter.ALL : filter;
        for (BatteryTip tip : tips) {
            if (tip == null || selected == Filter.COMPLETED && !tip.completed
                    || selected == Filter.INCOMPLETE && tip.completed) continue;
            result.add(tip);
        }
        return result;
    }
}
