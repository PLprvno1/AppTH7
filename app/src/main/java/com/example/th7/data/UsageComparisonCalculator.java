package com.example.th7.data;

import java.util.List;

/** Compares today's usage with a fixed number of prior calendar days; absent days count as zero. */
public final class UsageComparisonCalculator {
    private UsageComparisonCalculator() { }

    public static long differenceFromAverage(long todayMs, List<Long> previousDailyTotalsMs,
                                             int previousDayCount) {
        if (previousDayCount <= 0) throw new IllegalArgumentException("previousDayCount must be positive");
        long total = 0L;
        if (previousDailyTotalsMs != null) {
            for (Long duration : previousDailyTotalsMs) {
                if (duration != null && duration > 0L) total += duration;
            }
        }
        long average = Math.round(total / (double) previousDayCount);
        return Math.max(0L, todayMs) - average;
    }
}
