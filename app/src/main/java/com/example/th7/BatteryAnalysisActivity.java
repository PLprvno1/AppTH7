package com.example.th7;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.view.View;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.example.th7.data.AppSettings;
import com.example.th7.data.BatteryCoachEngine;
import com.example.th7.data.BatteryMonitor;
import com.example.th7.data.BatteryRepository;
import com.example.th7.data.BatteryStateMapper;
import com.example.th7.data.GeminiApiService;
import com.example.th7.model.BatteryAnalysisResult;
import com.example.th7.model.BatteryFinding;
import com.example.th7.model.BatteryRecommendation;
import com.example.th7.model.BatterySnapshot;
import com.example.th7.model.ChargingSession;
import com.google.android.material.appbar.MaterialToolbar;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class BatteryAnalysisActivity extends AppCompatActivity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private Future<?> task;
    private int generation;
    private TextView summary, quality, available, findings, recommendations, unavailable;
    private TextView geminiStatus, geminiResult, geminiResultTitle;
    private android.widget.Button geminiButton;
    private android.widget.ProgressBar geminiProgress;
    private BatteryRepository repository;

    @Override protected void onCreate(@Nullable Bundle state) {
        AppSettings.applyTheme(this);
        super.onCreate(state);
        setContentView(R.layout.activity_battery_analysis);
        repository = new BatteryRepository(getApplicationContext());
        ((MaterialToolbar) findViewById(R.id.analysisToolbar)).setNavigationOnClickListener(v -> finish());
        summary = findViewById(R.id.offlineAnalysisSummary);
        quality = findViewById(R.id.offlineAnalysisQuality);
        available = findViewById(R.id.offlineAnalysisAvailable);
        findings = findViewById(R.id.offlineAnalysisFindings);
        recommendations = findViewById(R.id.offlineAnalysisRecommendations);
        unavailable = findViewById(R.id.offlineAnalysisUnavailable);
        geminiButton = findViewById(R.id.buttonGeminiAnalysis);
        geminiProgress = findViewById(R.id.geminiProgress);
        geminiStatus = findViewById(R.id.geminiStatus);
        geminiResult = findViewById(R.id.geminiResult);
        geminiResultTitle = findViewById(R.id.geminiResultTitle);
        geminiButton.setOnClickListener(v -> startGeminiAnalysis());
    }

    @Override protected void onResume() {
        super.onResume();
        loadAnalysis(false, -1);
    }

    private void loadAnalysis(boolean askGemini, int requestGeneration) {
        if (io.isShutdown()) return;
        Context app = getApplicationContext();
        Future<?> submitted = io.submit(() -> {
            try {
                BatterySnapshot snapshot = BatteryMonitor.read(app, null);
                long now = System.currentTimeMillis();
                List<BatterySnapshot> samples = repository.getSamplesSince(now - 24L * 60 * 60 * 1000);
                List<ChargingSession> sessions = repository.getSessionsSince(now - 30L * 24 * 60 * 60 * 1000);
                Boolean saver = null;
                try {
                    PowerManager power = (PowerManager) app.getSystemService(Context.POWER_SERVICE);
                    if (power != null) saver = power.isPowerSaveMode();
                } catch (RuntimeException ignored) { }
                BatteryAnalysisResult result = BatteryCoachEngine.analyze(snapshot, samples, sessions, saver);
                String[] rendered = formatResult(app, result);
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    summary.setText(rendered[0]);
                    quality.setText(rendered[1]);
                    available.setText(rendered[2]);
                    findings.setText(rendered[3]);
                    recommendations.setText(rendered[4]);
                    unavailable.setText(rendered[5]);
                    unavailable.setVisibility(rendered[5].isEmpty() ? View.GONE : View.VISIBLE);
                });
                if (askGemini) requestGeminiExplanation(requestGeneration, result, rendered, app);
            } catch (RuntimeException error) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    summary.setText(R.string.analysis_unavailable);
                    quality.setText(""); available.setText(""); findings.setText("");
                    recommendations.setText(""); unavailable.setText(""); unavailable.setVisibility(View.GONE);
                    if (askGemini) finishGemini(requestGeneration, null, getString(R.string.gemini_error_invalid));
                });
            }
        });
        task = submitted;
    }

    private String[] formatResult(Context context, BatteryAnalysisResult result) {
        String level = result.currentLevel == null ? context.getString(R.string.analysis_value_unknown)
                : context.getString(R.string.analysis_level_format, result.currentLevel);
        String headline = context.getString(R.string.analysis_current_summary, level,
                chargeStatusLabel(context, result.chargeStatus), sourceLabel(context, result.chargeSource),
                levelBandLabel(context, result.levelBand), severityLabel(context, result.overallSeverity),
                powerSaveLabel(context, result.powerSaveMode), healthLabel(context, result.healthStatus));
        String qualityText = context.getString(R.string.analysis_data_quality,
                dataQualityLabel(context, result.dataQuality), result.observedSampleCount,
                result.observedDurationMs / 60000L);
        ArrayList<String> measurements = new ArrayList<>();
        if (result.voltageV != null) measurements.add(context.getString(R.string.analysis_voltage, result.voltageV));
        if (result.currentMa != null) measurements.add(context.getString(R.string.analysis_current, result.currentMa));
        if (!measurements.isEmpty()) qualityText += "\n" + android.text.TextUtils.join(" • ", measurements);
        String availableText = context.getString(R.string.analysis_available_data,
                android.text.TextUtils.join("\n- ", result.availableData));
        StringBuilder findingText = new StringBuilder();
        for (BatteryFinding finding : result.findings) {
            if (findingText.length() > 0) findingText.append("\n\n");
            findingText.append(context.getString(R.string.analysis_finding_format,
                    severityLabel(context, finding.severity), finding.title, finding.evidence, finding.reason));
        }
        StringBuilder recommendationText = new StringBuilder();
        for (BatteryRecommendation recommendation : result.recommendations) {
            if (recommendationText.length() > 0) recommendationText.append("\n\n");
            recommendationText.append(context.getString(R.string.analysis_recommendation_format,
                    recommendation.title, recommendation.message));
        }
        ArrayList<String> missing = new ArrayList<>(result.unavailableData);
        missing.add(context.getString(R.string.analysis_unavailable_app_drain));
        missing.add(context.getString(R.string.analysis_unavailable_health_percent));
        missing.add(context.getString(R.string.analysis_unavailable_temperature));
        String unavailableText = context.getString(R.string.analysis_unavailable_data,
                android.text.TextUtils.join("\n- ", missing));
        return new String[]{headline, qualityText, availableText, findingText.toString(),
                recommendationText.toString(), unavailableText};
    }

    private String severityLabel(Context c, BatteryFinding.Severity s) {
        switch (s) {
            case ADVISORY: return c.getString(R.string.analysis_severity_advisory);
            case WARNING: return c.getString(R.string.analysis_severity_warning);
            case CRITICAL: return c.getString(R.string.analysis_severity_critical);
            default: return c.getString(R.string.analysis_severity_info);
        }
    }
    private String dataQualityLabel(Context c, BatteryAnalysisResult.DataQuality q) {
        switch (q) {
            case SUFFICIENT: return c.getString(R.string.analysis_quality_sufficient);
            case LIMITED: return c.getString(R.string.analysis_quality_limited);
            default: return c.getString(R.string.analysis_quality_insufficient);
        }
    }
    private String levelBandLabel(Context c, BatteryAnalysisResult.LevelBand b) {
        switch (b) {
            case VERY_LOW: return c.getString(R.string.analysis_band_very_low);
            case LOW: return c.getString(R.string.analysis_band_low);
            case MEDIUM: return c.getString(R.string.analysis_band_medium);
            case HIGH: return c.getString(R.string.analysis_band_high);
            default: return c.getString(R.string.analysis_value_unknown);
        }
    }
    private String chargeStatusLabel(Context c, BatteryStateMapper.Status s) {
        switch (s) {
            case CHARGING: return c.getString(R.string.analysis_status_charging);
            case DISCHARGING: return c.getString(R.string.analysis_status_discharging);
            case NOT_CHARGING: return c.getString(R.string.analysis_status_not_charging);
            case FULL: return c.getString(R.string.analysis_status_full);
            case UNKNOWN: return c.getString(R.string.analysis_status_unknown);
            default: return c.getString(R.string.analysis_value_unavailable);
        }
    }
    private String sourceLabel(Context c, BatteryStateMapper.Source s) {
        switch (s) {
            case NONE: return c.getString(R.string.analysis_source_none);
            case AC: return c.getString(R.string.analysis_source_ac);
            case USB: return c.getString(R.string.analysis_source_usb);
            case WIRELESS: return c.getString(R.string.analysis_source_wireless);
            case OTHER: return c.getString(R.string.analysis_source_other);
            default: return c.getString(R.string.analysis_value_unknown);
        }
    }
    private String powerSaveLabel(Context c, Boolean enabled) {
        if (enabled == null) return c.getString(R.string.analysis_value_unknown);
        return c.getString(enabled ? R.string.analysis_power_save_on : R.string.analysis_power_save_off);
    }
    private String healthLabel(Context c, BatteryStateMapper.Health h) {
        switch (h) {
            case GOOD: return c.getString(R.string.analysis_health_good);
            case OVERHEAT: return c.getString(R.string.analysis_health_overheat);
            case DEAD: return c.getString(R.string.analysis_health_dead);
            case OVER_VOLTAGE: return c.getString(R.string.analysis_health_over_voltage);
            case UNSPECIFIED_FAILURE: return c.getString(R.string.analysis_health_failure);
            case COLD: return c.getString(R.string.analysis_health_cold);
            default: return c.getString(R.string.analysis_value_unknown);
        }
    }

    private void startGeminiAnalysis() {
        if (io.isShutdown() || task != null && !task.isDone()) return;
        int request = ++generation;
        geminiButton.setEnabled(false);
        geminiProgress.setVisibility(View.VISIBLE);
        geminiStatus.setVisibility(View.VISIBLE);
        geminiStatus.setText(R.string.analysis_offline_running);
        geminiResult.setVisibility(View.GONE);
        geminiResultTitle.setVisibility(View.GONE);
        loadAnalysis(true, request);
    }

    private void requestGeminiExplanation(int request, BatteryAnalysisResult analysis,
                                         String[] rendered, Context appContext) {
        try {
            String key = BuildConfig.GEMINI_API_KEY;
            if (key == null || key.trim().isEmpty()) {
                finishGemini(request, null, getString(R.string.gemini_key_missing)); return;
            }
            android.content.SharedPreferences limits = getSharedPreferences("gemini_request_limits", MODE_PRIVATE);
            long remaining = 20_000L - (System.currentTimeMillis() - limits.getLong("last_request_ms", 0L));
            if (remaining > 0L) {
                finishGemini(request, null, getString(R.string.gemini_error_cooldown,
                        Math.max(1, (int) Math.ceil(remaining / 1000.0)))); return;
            }
            new Handler(Looper.getMainLooper()).post(() -> {
                if (isCurrent(request)) geminiStatus.setText(R.string.gemini_loading);
            });
            String prompt = buildGeminiPrompt(analysis, rendered);
            limits.edit().putLong("last_request_ms", System.currentTimeMillis()).apply();
            finishGemini(request, new GeminiApiService().generate(key, prompt), null);
        } catch (GeminiApiService.ApiException error) {
            finishGemini(request, null, getString(geminiErrorString(error.error)));
        } catch (RuntimeException error) {
            finishGemini(request, null, getString(R.string.gemini_error_invalid));
        }
    }

    private String buildGeminiPrompt(BatteryAnalysisResult result, String[] rendered) {
        return "Analysis severity: " + result.overallSeverity.name() + "\n"
                + rendered[0] + "\n" + rendered[1] + "\n" + rendered[2]
                + "\n\nFindings:\n" + rendered[3]
                + "\n\nRecommendations:\n" + (rendered[4].isEmpty() ? "None." : rendered[4])
                + "\n\nUnavailable data:\n" + rendered[5];
    }
    private int geminiErrorString(GeminiApiService.Error error) {
        switch (error) {
            case NETWORK: return R.string.gemini_error_network;
            case TIMEOUT: return R.string.gemini_error_timeout;
            case BAD_REQUEST: return R.string.gemini_error_bad_request;
            case UNAUTHORIZED: return R.string.gemini_error_unauthorized;
            case FORBIDDEN: return R.string.gemini_error_forbidden;
            case MODEL_UNAVAILABLE: return R.string.gemini_error_model;
            case RATE_LIMIT: return R.string.gemini_error_rate_limit;
            case SERVER: return R.string.gemini_error_server;
            case SAFETY: return R.string.gemini_error_safety;
            case INCOMPLETE_RESPONSE: return R.string.gemini_error_incomplete;
            case INVALID_RESPONSE: return R.string.gemini_error_invalid;
            default: return R.string.gemini_error_other;
        }
    }
    private boolean isCurrent(int request) { return !isFinishing() && !isDestroyed() && request == generation; }
    private void finishGemini(int request, String text, String message) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (!isCurrent(request)) return;
            geminiProgress.setVisibility(View.GONE);
            geminiButton.setEnabled(true);
            if (text != null) {
                geminiButton.setText(R.string.gemini_analyze);
                geminiStatus.setVisibility(View.GONE);
                geminiResultTitle.setVisibility(View.VISIBLE);
                geminiResult.setText(text);
                geminiResult.setVisibility(View.VISIBLE);
            } else {
                geminiButton.setText(R.string.gemini_retry);
                geminiStatus.setText(message);
                geminiStatus.setVisibility(View.VISIBLE);
            }
        });
    }

    @Override protected void onDestroy() {
        generation++;
        if (task != null) task.cancel(true);
        io.shutdownNow();
        super.onDestroy();
    }
}
