package com.example.th7.data;

import com.example.th7.model.BatterySnapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Estimates remaining discharge time only from recent, observed discharge samples. */
public final class BatteryRuntimeEstimator {
    private static final long MAX_HISTORY_MS = 24L * 60L * 60L * 1000L;
    // Short demo window: a real forecast should use a much longer observation period.
    private static final long MIN_INTERVAL_MS = 5_000L;
    // Larger gaps may hide charging while the app was not observing broadcasts.
    private static final long MAX_SAMPLE_GAP_MS = 30L * 60L * 1000L;
    private static final long MAX_FORECAST_MS = 7L * 24L * 60L * 60L * 1000L;
    // Permit short emulator demo drops (e.g. changing the simulated level by 1%).
    private static final double MAX_RATE_PERCENT_PER_HOUR = 1_000.0;

    private BatteryRuntimeEstimator() { }

    /** Returns an estimate in minutes, or -1 when recent samples are insufficient or unreliable. */
    public static long estimateRemainingMinutes(List<BatterySnapshot> samples,
                                                BatterySnapshot current, long nowMs) {
        if (!isValidDischarging(current, nowMs) || current.level == 0) return -1L;

        ArrayList<BatterySnapshot> points = new ArrayList<>();
        if (samples != null) {
            for (BatterySnapshot sample : samples) {
                if (sample != null && sample.timestamp >= nowMs - MAX_HISTORY_MS
                        && isValidPoint(sample, nowMs)) points.add(sample);
            }
        }
        points.add(current);
        Collections.sort(points, Comparator.comparingLong(snapshot -> snapshot.timestamp));

        ArrayList<Double> rates = new ArrayList<>();
        ArrayList<BatterySnapshot> segment = new ArrayList<>();
        BatterySnapshot previous = null;
        for (BatterySnapshot point : points) {
            if (previous != null && point.timestamp == previous.timestamp) {
                if (point == current) {
                    if (!segment.isEmpty()) segment.set(segment.size() - 1, point);
                    previous = point;
                }
                continue;
            }
            if (!isValidDischarging(point, nowMs)) {
                collectRates(segment, rates);
                segment.clear();
                previous = point;
                continue;
            }
            if (previous != null && isValidDischarging(previous, nowMs)
                    && point.timestamp - previous.timestamp > MAX_SAMPLE_GAP_MS) {
                collectRates(segment, rates);
                segment.clear();
            }
            if (!segment.isEmpty() && point.level > segment.get(segment.size() - 1).level) {
                collectRates(segment, rates);
                segment.clear();
            }
            segment.add(point);
            previous = point;
        }
        collectRates(segment, rates);
        if (rates.isEmpty()) return -1L;

        Collections.sort(rates);
        double rate = rates.get(rates.size() / 2);
        double forecastHours = current.level / rate;
        if (!Double.isFinite(forecastHours) || forecastHours <= 0.0
                || forecastHours * 60.0 > MAX_FORECAST_MS / 60000.0) return -1L;
        return Math.max(1L, Math.round(forecastHours * 60.0));
    }

    private static void collectRates(List<BatterySnapshot> segment, List<Double> rates) {
        for (int i = 0; i < segment.size(); i++) {
            BatterySnapshot start = segment.get(i);
            for (int j = i + 1; j < segment.size(); j++) {
                BatterySnapshot end = segment.get(j);
                long duration = end.timestamp - start.timestamp;
                if (duration < MIN_INTERVAL_MS) continue;
                int drop = start.level - end.level;
                // Battery level is reported in whole percentages; for the demo, accept
                // a one-point change over the short observation window.
                if (drop < 1) continue;
                double hours = duration / 3600000.0;
                double rate = drop / hours;
                if (Double.isFinite(rate) && rate > 0.0
                        && rate <= MAX_RATE_PERCENT_PER_HOUR) rates.add(rate);
            }
        }
    }

    private static boolean isValidPoint(BatterySnapshot point, long nowMs) {
        return point.level >= 0 && point.level <= 100 && point.timestamp > 0
                && point.timestamp <= nowMs;
    }

    private static boolean isValidDischarging(BatterySnapshot point, long nowMs) {
        return isValidPoint(point, nowMs)
                && point.statusType() == BatteryStateMapper.Status.DISCHARGING
                && !point.isPlugged();
    }
}
