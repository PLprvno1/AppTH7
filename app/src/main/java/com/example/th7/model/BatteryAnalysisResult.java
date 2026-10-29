package com.example.th7.model;

import com.example.th7.data.BatteryStateMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable offline analysis. It contains observations, not a battery-health score. */
public final class BatteryAnalysisResult {
    public enum LevelBand { VERY_LOW, LOW, MEDIUM, HIGH, UNKNOWN }
    public enum DataQuality { SUFFICIENT, LIMITED, INSUFFICIENT }

    public final long analyzedAt;
    public final Integer currentLevel;
    public final LevelBand levelBand;
    public final BatteryStateMapper.Status chargeStatus;
    public final BatteryStateMapper.Source chargeSource;
    public final BatteryStateMapper.Health healthStatus;
    public final Boolean powerSaveMode;
    public final Float voltageV;
    public final Float currentMa;
    public final BatteryFinding.Severity overallSeverity;
    public final DataQuality dataQuality;
    public final List<BatteryFinding> findings;
    public final List<BatteryRecommendation> recommendations;
    public final List<String> availableData;
    public final List<String> unavailableData;
    public final int observedSampleCount;
    public final long observedDurationMs;

    public BatteryAnalysisResult(long analyzedAt, Integer currentLevel, LevelBand levelBand,
                                 BatteryStateMapper.Status chargeStatus,
                                 BatteryStateMapper.Source chargeSource,
                                 BatteryStateMapper.Health healthStatus, Boolean powerSaveMode,
                                 Float voltageV, Float currentMa,
                                 BatteryFinding.Severity overallSeverity, DataQuality dataQuality,
                                 List<BatteryFinding> findings,
                                 List<BatteryRecommendation> recommendations,
                                 List<String> availableData, List<String> unavailableData,
                                 int observedSampleCount, long observedDurationMs) {
        this.analyzedAt = analyzedAt;
        this.currentLevel = currentLevel;
        this.levelBand = levelBand;
        this.chargeStatus = chargeStatus;
        this.chargeSource = chargeSource;
        this.healthStatus = healthStatus;
        this.powerSaveMode = powerSaveMode;
        this.voltageV = voltageV;
        this.currentMa = currentMa;
        this.overallSeverity = overallSeverity;
        this.dataQuality = dataQuality;
        this.findings = immutableCopy(findings);
        this.recommendations = immutableCopy(recommendations);
        this.availableData = immutableCopy(availableData);
        this.unavailableData = immutableCopy(unavailableData);
        this.observedSampleCount = observedSampleCount;
        this.observedDurationMs = observedDurationMs;
    }

    private static <T> List<T> immutableCopy(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<>(values));
    }
}
