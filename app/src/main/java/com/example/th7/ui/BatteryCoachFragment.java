package com.example.th7.ui;

import android.Manifest;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.database.ContentObserver;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.net.wifi.WifiManager;
import android.bluetooth.BluetoothAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.th7.MainActivity;
import com.example.th7.BuildConfig;
import com.example.th7.R;
import com.example.th7.adapter.BatteryTipAdapter;
import com.example.th7.data.AppSettings;
import com.example.th7.data.BatteryCoachEngine;
import com.example.th7.data.BatteryRepository;
import com.example.th7.data.BatterySaverMode;
import com.example.th7.data.BatteryStateMapper;
import com.example.th7.data.GeminiApiService;
import com.example.th7.data.SettingsActionHelper;
import com.example.th7.data.TipsRepository;
import com.example.th7.data.TipsChecklist;
import com.example.th7.data.ScreenTimeoutFormatter;
import com.example.th7.model.BatterySnapshot;
import com.example.th7.model.BatteryAnalysisResult;
import com.example.th7.model.BatteryFinding;
import com.example.th7.model.BatteryRecommendation;
import com.example.th7.model.BatteryTip;
import com.example.th7.model.ChargingSession;
import com.google.android.material.chip.ChipGroup;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BatteryCoachFragment extends Fragment {
    private final List<BatteryTip> allTips = new ArrayList<>(), shownTips = new ArrayList<>();
    private BatteryTipAdapter tipsAdapter;
    private BatteryRepository repository;
    private TipsRepository tipsRepository;
    private RecyclerView tipsList;
    private TextView completedSummary, attentionSummary, tipsEmpty;
    private int tipsFilter = R.id.tipsFilterAll;
    private int brightnessPercent = -1;
    private SwipeRefreshLayout refresh;
    private ExecutorService geminiIo = Executors.newSingleThreadExecutor();
    private Future<?> geminiTask, offlineAnalysisTask;
    private android.widget.Button geminiButton;
    private android.widget.ProgressBar geminiProgress;
    private TextView geminiStatus, geminiResult, geminiResultTitle;
    private TextView offlineSummary, offlineQuality, offlineAvailable, offlineFindings,
            offlineRecommendations, offlineUnavailable;
    private int geminiGeneration;
    private ActivityResultLauncher<String> bluetoothPermissionLauncher;
    private boolean brightnessWriteFailed;
    private boolean brightnessTracking;
    private boolean brightnessObserverRegistered;
    private boolean brightnessDisplayListenerRegistered;
    private boolean brightnessCanWrite;
    private final ContentObserver brightnessObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
        @Override public void onChange(boolean selfChange, android.net.Uri uri) {
            super.onChange(selfChange, uri);
            if (!isAdded() || getView() == null || brightnessTracking) return;
            boolean canWrite = AppSettings.canWriteSystemBrightness(requireContext());
            int current = canWrite && AppSettings.supportsSystemBrightnessPercent()
                    ? AppSettings.systemBrightnessPercent(requireContext()) : -1;
            if (canWrite != brightnessCanWrite || current != brightnessPercent) refreshTips();
        }
    };
    private final DisplayManager.DisplayListener brightnessDisplayListener = new DisplayManager.DisplayListener() {
        @Override public void onDisplayAdded(int displayId) { }
        @Override public void onDisplayRemoved(int displayId) { }
        @Override public void onDisplayChanged(int displayId) {
            if (displayId == android.view.Display.DEFAULT_DISPLAY && isAdded()
                    && getView() != null && !brightnessTracking) refreshTips();
        }
    };

    @Override public void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        bluetoothPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                    if (!isAdded()) return;
                    if (!granted) android.widget.Toast.makeText(requireContext(),
                            R.string.tip_bluetooth_permission_denied, android.widget.Toast.LENGTH_LONG).show();
                    refreshTips();
                });
    }

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        View content=inflater.inflate(R.layout.fragment_battery_coach, container, false); refresh=new SwipeRefreshLayout(requireContext()); refresh.setColorSchemeResources(R.color.tech_cyan); refresh.addView(content); refresh.setOnRefreshListener(()->((MainActivity)requireActivity()).refreshBatteryState(ok->{if(isAdded()){refreshTips();calculateOfflineAnalysis(((MainActivity)requireActivity()).getLatestBattery(),-1,false,null);if(!ok)android.widget.Toast.makeText(requireContext(),R.string.refresh_failed,android.widget.Toast.LENGTH_SHORT).show();}if(refresh!=null)refresh.setRefreshing(false);}));return refresh;
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        repository = new BatteryRepository(requireContext());
        tipsRepository = new TipsRepository(requireContext());
        offlineSummary = view.findViewById(R.id.offlineAnalysisSummary);
        offlineQuality = view.findViewById(R.id.offlineAnalysisQuality);
        offlineAvailable = view.findViewById(R.id.offlineAnalysisAvailable);
        offlineFindings = view.findViewById(R.id.offlineAnalysisFindings);
        offlineRecommendations = view.findViewById(R.id.offlineAnalysisRecommendations);
        offlineUnavailable = view.findViewById(R.id.offlineAnalysisUnavailable);
        geminiButton = view.findViewById(R.id.buttonGeminiAnalysis);
        geminiProgress = view.findViewById(R.id.geminiProgress);
        geminiStatus = view.findViewById(R.id.geminiStatus);
        geminiResult = view.findViewById(R.id.geminiResult);
        geminiResultTitle = view.findViewById(R.id.geminiResultTitle);
        geminiButton.setOnClickListener(v -> startGeminiAnalysis());
        tipsList = view.findViewById(R.id.recyclerTips); tipsList.setLayoutManager(new LinearLayoutManager(getContext()));
        tipsAdapter = new BatteryTipAdapter(shownTips, new BatteryTipAdapter.Listener() {
            @Override public void onAction(BatteryTip tip) { performTipAction(tip); }
            @Override public void onBrightnessChanged(BatteryTip tip, int percent, boolean trackingStopped) {
                if (trackingStopped) {
                    brightnessTracking = false;
                    if (brightnessWriteFailed) android.widget.Toast.makeText(requireContext(),
                            R.string.tip_brightness_write_failed, android.widget.Toast.LENGTH_SHORT).show();
                    refreshTips();
                } else {
                    brightnessTracking = true;
                    brightnessWriteFailed = !AppSettings.setSystemBrightness(requireContext(), percent);
                    if (!brightnessWriteFailed) brightnessPercent = percent;
                }
            }
            @Override public void onCompletionChanged(BatteryTip tip, boolean completed) {
                tipsRepository.setCompleted(tip.id, completed); refreshTips();
            }
        });
        tipsList.setAdapter(tipsAdapter);
        completedSummary = view.findViewById(R.id.tipsCompletedSummary); attentionSummary = view.findViewById(R.id.tipsAttentionSummary);
        tipsEmpty = view.findViewById(R.id.tipsEmpty);
        ChipGroup tipsGroup = view.findViewById(R.id.tipsFilterGroup);
        tipsGroup.setOnCheckedStateChangeListener((g, ids) -> { if (!ids.isEmpty()) { tipsFilter = ids.get(0); applyTipsFilter(); } });
        refreshTips();
        refreshOfflineAnalysis(false);
    }

    @Override public void onResume() { super.onResume(); refreshTips(); refreshOfflineAnalysis(true); }

    @Override public void onStart() {
        super.onStart();
        if (!brightnessObserverRegistered && getContext() != null) {
            android.content.ContentResolver resolver = requireContext().getContentResolver();
            resolver.registerContentObserver(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS), false, brightnessObserver);
            resolver.registerContentObserver(Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE), false, brightnessObserver);
            brightnessObserverRegistered = true;
        }
        if (!brightnessDisplayListenerRegistered && getContext() != null
                && AppSettings.supportsSystemBrightnessPercent()) {
            DisplayManager manager = (DisplayManager) requireContext().getSystemService(Context.DISPLAY_SERVICE);
            if (manager != null) {
                manager.registerDisplayListener(requireContext().getMainExecutor(),
                        DisplayManager.EVENT_TYPE_DISPLAY_BRIGHTNESS, brightnessDisplayListener);
                brightnessDisplayListenerRegistered = true;
            }
        }
    }

    @Override public void onStop() {
        if (brightnessObserverRegistered && getContext() != null) {
            requireContext().getContentResolver().unregisterContentObserver(brightnessObserver);
            brightnessObserverRegistered = false;
        }
        if (brightnessDisplayListenerRegistered && getContext() != null) {
            DisplayManager manager = (DisplayManager) requireContext().getSystemService(Context.DISPLAY_SERVICE);
            if (manager != null) manager.unregisterDisplayListener(brightnessDisplayListener);
            brightnessDisplayListenerRegistered = false;
        }
        super.onStop();
    }

    private void refreshOfflineAnalysis(boolean refreshBattery) {
        if (!isAdded() || repository == null || offlineSummary == null) return;
        MainActivity activity = (MainActivity) requireActivity();
        if (refreshBattery) {
            activity.refreshBatteryState(ok -> {
                if (isAdded()) calculateOfflineAnalysis(activity.getLatestBattery(), -1, false, null);
            });
        } else {
            calculateOfflineAnalysis(activity.getLatestBattery(), -1, false, null);
        }
    }

    private void calculateOfflineAnalysis(BatterySnapshot snapshot, int generation,
                                          boolean continueToGemini,
                                          android.content.SharedPreferences limits) {
        if (!isAdded() || geminiIo.isShutdown()) return;
        Context appContext = requireContext().getApplicationContext();
        Future<?> submitted = geminiIo.submit(() -> {
            try {
                long now = System.currentTimeMillis();
                List<BatterySnapshot> samples = repository.getSamplesSince(now - 24L * 60 * 60 * 1000);
                List<ChargingSession> sessions = repository.getSessionsSince(now - 30L * 24 * 60 * 60 * 1000);
                Boolean saver = null;
                try {
                    PowerManager power = (PowerManager) appContext.getSystemService(Context.POWER_SERVICE);
                    if (power != null) saver = power.isPowerSaveMode();
                } catch (RuntimeException ignored) { }
                BatteryAnalysisResult result = BatteryCoachEngine.analyze(snapshot, samples, sessions, saver);
                String[] rendered = formatOfflineResult(appContext, result);
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (!isAdded() || getView() == null) return;
                    offlineSummary.setText(rendered[0]);
                    offlineQuality.setText(rendered[1]);
                    offlineAvailable.setText(rendered[2]);
                    offlineFindings.setText(rendered[3]);
                    offlineRecommendations.setText(rendered[4]);
                    offlineUnavailable.setText(rendered[5]);
                    offlineUnavailable.setVisibility(rendered[5].isEmpty() ? View.GONE : View.VISIBLE);
                });
                if (continueToGemini) requestGeminiExplanation(generation, result, rendered, limits, appContext);
            } catch (RuntimeException error) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (isAdded() && getView() != null) {
                        offlineSummary.setText(R.string.analysis_unavailable);
                        offlineQuality.setText("");
                        offlineAvailable.setText("");
                        offlineFindings.setText("");
                        offlineRecommendations.setText("");
                        offlineUnavailable.setText("");
                        offlineUnavailable.setVisibility(View.GONE);
                    }
                    if (continueToGemini) finishGemini(generation, null,
                            appContext.getString(R.string.gemini_error_invalid));
                });
            }
        });
        if (continueToGemini) geminiTask = submitted;
        else offlineAnalysisTask = submitted;
    }

    private String[] formatOfflineResult(Context context, BatteryAnalysisResult result) {
        String level = result.currentLevel == null ? context.getString(R.string.analysis_value_unknown)
                : context.getString(R.string.analysis_level_format, result.currentLevel);
        String summary = context.getString(R.string.analysis_current_summary, level,
                chargeStatusLabel(context, result.chargeStatus), sourceLabel(context, result.chargeSource),
                levelBandLabel(context, result.levelBand), severityLabel(context, result.overallSeverity),
                powerSaveLabel(context, result.powerSaveMode), healthLabel(context, result.healthStatus));
        String quality = context.getString(R.string.analysis_data_quality,
                dataQualityLabel(context, result.dataQuality), result.observedSampleCount,
                result.observedDurationMs / 60000L);
        ArrayList<String> measurements = new ArrayList<>();
        if (result.voltageV != null) measurements.add(context.getString(R.string.analysis_voltage,
                result.voltageV));
        if (result.currentMa != null) measurements.add(context.getString(R.string.analysis_current,
                result.currentMa));
        if (!measurements.isEmpty()) quality += "\n" + android.text.TextUtils.join(" • ", measurements);
        String availableText = context.getString(R.string.analysis_available_data,
                android.text.TextUtils.join("\n- ", result.availableData));
        StringBuilder findings = new StringBuilder();
        for (BatteryFinding finding : result.findings) {
            if (findings.length() > 0) findings.append("\n\n");
            findings.append(context.getString(R.string.analysis_finding_format,
                    severityLabel(context, finding.severity), finding.title, finding.evidence, finding.reason));
        }
        StringBuilder recommendations = new StringBuilder();
        for (BatteryRecommendation recommendation : result.recommendations) {
            if (recommendations.length() > 0) recommendations.append("\n\n");
            recommendations.append(context.getString(R.string.analysis_recommendation_format,
                    recommendation.title, recommendation.message));
        }
        ArrayList<String> unavailable = new ArrayList<>(result.unavailableData);
        unavailable.add(context.getString(R.string.analysis_unavailable_app_drain));
        unavailable.add(context.getString(R.string.analysis_unavailable_health_percent));
        unavailable.add(context.getString(R.string.analysis_unavailable_temperature));
        String unavailableText = context.getString(R.string.analysis_unavailable_data,
                android.text.TextUtils.join("\n- ", unavailable));
        return new String[]{summary, quality, availableText, findings.toString(),
                recommendations.toString(), unavailableText};
    }

    private String severityLabel(Context context, BatteryFinding.Severity severity) {
        switch (severity) {
            case ADVISORY: return context.getString(R.string.analysis_severity_advisory);
            case WARNING: return context.getString(R.string.analysis_severity_warning);
            case CRITICAL: return context.getString(R.string.analysis_severity_critical);
            default: return context.getString(R.string.analysis_severity_info);
        }
    }

    private String dataQualityLabel(Context context, BatteryAnalysisResult.DataQuality quality) {
        switch (quality) {
            case SUFFICIENT: return context.getString(R.string.analysis_quality_sufficient);
            case LIMITED: return context.getString(R.string.analysis_quality_limited);
            default: return context.getString(R.string.analysis_quality_insufficient);
        }
    }

    private String levelBandLabel(Context context, BatteryAnalysisResult.LevelBand band) {
        switch (band) {
            case VERY_LOW: return context.getString(R.string.analysis_band_very_low);
            case LOW: return context.getString(R.string.analysis_band_low);
            case MEDIUM: return context.getString(R.string.analysis_band_medium);
            case HIGH: return context.getString(R.string.analysis_band_high);
            default: return context.getString(R.string.analysis_value_unknown);
        }
    }

    private String chargeStatusLabel(Context context, BatteryStateMapper.Status status) {
        switch (status) {
            case CHARGING: return context.getString(R.string.analysis_status_charging);
            case DISCHARGING: return context.getString(R.string.analysis_status_discharging);
            case NOT_CHARGING: return context.getString(R.string.analysis_status_not_charging);
            case FULL: return context.getString(R.string.analysis_status_full);
            case UNKNOWN: return context.getString(R.string.analysis_status_unknown);
            default: return context.getString(R.string.analysis_value_unavailable);
        }
    }

    private String sourceLabel(Context context, BatteryStateMapper.Source source) {
        switch (source) {
            case NONE: return context.getString(R.string.analysis_source_none);
            case AC: return context.getString(R.string.analysis_source_ac);
            case USB: return context.getString(R.string.analysis_source_usb);
            case WIRELESS: return context.getString(R.string.analysis_source_wireless);
            case OTHER: return context.getString(R.string.analysis_source_other);
            default: return context.getString(R.string.analysis_value_unknown);
        }
    }

    private String powerSaveLabel(Context context, Boolean enabled) {
        if (enabled == null) return context.getString(R.string.analysis_value_unknown);
        return context.getString(enabled ? R.string.analysis_power_save_on : R.string.analysis_power_save_off);
    }

    private String healthLabel(Context context, BatteryStateMapper.Health health) {
        switch (health) {
            case GOOD: return context.getString(R.string.analysis_health_good);
            case OVERHEAT: return context.getString(R.string.analysis_health_overheat);
            case DEAD: return context.getString(R.string.analysis_health_dead);
            case OVER_VOLTAGE: return context.getString(R.string.analysis_health_over_voltage);
            case UNSPECIFIED_FAILURE: return context.getString(R.string.analysis_health_failure);
            case COLD: return context.getString(R.string.analysis_health_cold);
            default: return context.getString(R.string.analysis_value_unknown);
        }
    }

    private void startGeminiAnalysis() {
        if (!isAdded() || geminiIo.isShutdown() || (geminiTask != null && !geminiTask.isDone())) return;
        int generation = ++geminiGeneration;
        geminiButton.setEnabled(false);
        geminiProgress.setVisibility(View.VISIBLE);
        geminiStatus.setVisibility(View.VISIBLE);
        geminiStatus.setText(R.string.analysis_offline_running);
        geminiResult.setVisibility(View.GONE);
        geminiResultTitle.setVisibility(View.GONE);
        MainActivity activity = (MainActivity) requireActivity();
        android.content.SharedPreferences limits = requireContext().getSharedPreferences(
                "gemini_request_limits", Context.MODE_PRIVATE);
        activity.refreshBatteryState(ok -> {
            if (!isGeminiViewCurrent(generation)) return;
            calculateOfflineAnalysis(activity.getLatestBattery(), generation, true, limits);
        });
    }

    private void requestGeminiExplanation(int generation, BatteryAnalysisResult analysis,
                                          String[] rendered,
                                          android.content.SharedPreferences limits,
                                          Context appContext) {
        try {
            String key = BuildConfig.GEMINI_API_KEY;
            if (key == null || key.trim().isEmpty()) {
                finishGemini(generation, null, appContext.getString(R.string.gemini_key_missing));
                return;
            }
            long remaining = 20_000L - (System.currentTimeMillis()
                    - limits.getLong("last_request_ms", 0L));
            if (remaining > 0L) {
                finishGemini(generation, null, appContext.getString(R.string.gemini_error_cooldown,
                        Math.max(1, (int) Math.ceil(remaining / 1000.0))));
                return;
            }
            new Handler(Looper.getMainLooper()).post(() -> {
                if (isGeminiViewCurrent(generation)) geminiStatus.setText(R.string.gemini_loading);
            });
            String prompt = buildGeminiPrompt(analysis, rendered);
            limits.edit().putLong("last_request_ms", System.currentTimeMillis()).apply();
            String explanation = new GeminiApiService().generate(key, prompt);
            finishGemini(generation, explanation, null);
        } catch (GeminiApiService.ApiException error) {
            String message = error.error == GeminiApiService.Error.OTHER_HTTP
                    ? appContext.getString(R.string.gemini_error_other)
                    : appContext.getString(geminiErrorString(error.error));
            finishGemini(generation, null, message);
        } catch (RuntimeException error) {
            finishGemini(generation, null, appContext.getString(R.string.gemini_error_invalid));
        }
    }

    private String buildGeminiPrompt(BatteryAnalysisResult result, String[] rendered) {
        return "Trả lời bằng tiếng Việt, ngắn gọn và an toàn. Java là nguồn kết luận; chỉ giải thích "
                + "bằng chứng và khuyến nghị bên dưới. Không thay đổi mức cảnh báo, không suy đoán "
                + "sức khỏe pin, nhiệt độ, dung lượng, mức tiêu hao theo ứng dụng hoặc thời lượng còn lại. "
                + "Nếu thiếu dữ liệu, hãy nói rõ.\n\n"
                + "Kết quả phân tích Java (" + result.overallSeverity.name() + "):\n"
                + rendered[0] + "\n" + rendered[1] + "\n" + rendered[2]
                + "\n\nPhát hiện:\n" + rendered[3]
                + "\n\nKhuyến nghị:\n" + (rendered[4].isEmpty() ? "Không có." : rendered[4])
                + "\n\nDữ liệu chưa có:\n" + rendered[5];
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

    private boolean isGeminiViewCurrent(int generation) {
        return isAdded() && getView() != null && generation == geminiGeneration
                && geminiStatus != null && geminiButton != null;
    }

    private void showGeminiStatus(String message) {
        if (geminiStatus != null) {
            geminiStatus.setVisibility(View.VISIBLE);
            geminiStatus.setText(message);
        }
    }

    private void finishGemini(int generation, String result, String message) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (!isGeminiViewCurrent(generation)) return;
            geminiProgress.setVisibility(View.GONE);
            geminiButton.setEnabled(true);
            if (result != null) {
                geminiButton.setText(R.string.gemini_analyze);
                geminiStatus.setVisibility(View.GONE);
                geminiResultTitle.setVisibility(View.VISIBLE);
                geminiResult.setText(result);
                geminiResult.setVisibility(View.VISIBLE);
            } else {
                geminiButton.setText(R.string.gemini_retry);
                showGeminiStatus(message);
            }
        });
    }

    private void refreshTips() {
        if (getContext() == null || tipsRepository == null) return;
        Context context = requireContext();
        boolean canWriteBrightness = AppSettings.canWriteSystemBrightness(context);
        brightnessCanWrite = canWriteBrightness;
        boolean exactBrightnessScaleAvailable = AppSettings.supportsSystemBrightnessPercent();
        brightnessPercent = canWriteBrightness && exactBrightnessScaleAvailable
                ? AppSettings.systemBrightnessPercent(context) : -1;
        allTips.clear();
        addTip("brightness", R.string.tip_brightness_title, R.string.tip_brightness_desc,
                !exactBrightnessScaleAvailable ? R.string.tip_brightness_unavailable_status
                        : !canWriteBrightness ? R.string.tip_brightness_permission_status
                        : brightnessPercent >= 0 ? R.string.tip_brightness_system_status : R.string.tip_status_unknown,
                exactBrightnessScaleAvailable && !canWriteBrightness
                        ? R.string.tip_action_brightness_permission : R.string.tip_action_display,
                R.drawable.ic_lightbulb, SettingsActionHelper.Action.DISPLAY, true);
        addTip("dark_mode", R.string.tip_dark_title, R.string.tip_dark_desc,
                isDark(context) ? R.string.tip_status_dark_on : R.string.tip_status_dark_off,
                isDark(context) ? R.string.tip_action_light : R.string.tip_action_dark, R.drawable.ic_coach, null, true);
        addTip("wifi", R.string.tip_wifi_title, R.string.tip_wifi_desc, wifiStatus(context),
                R.string.tip_action_wifi, R.drawable.ic_game, SettingsActionHelper.Action.WIFI, true);
        addTip("bluetooth", R.string.tip_bluetooth_title, R.string.tip_bluetooth_desc, bluetoothStatus(),
                Build.VERSION.SDK_INT >= 31 && !hasBluetoothPermission()
                        ? R.string.tip_action_bluetooth_permission : R.string.tip_action_bluetooth,
                R.drawable.ic_info, SettingsActionHelper.Action.BLUETOOTH, true);
        addTip("location", R.string.tip_location_title, R.string.tip_location_desc, locationStatus(context),
                R.string.tip_action_location, R.drawable.ic_info, SettingsActionHelper.Action.LOCATION, true);
        addTip("screen_timeout", R.string.tip_timeout_title, R.string.tip_timeout_desc, timeoutStatus(context),
                R.string.tip_action_display, R.drawable.ic_usage_time, SettingsActionHelper.Action.DISPLAY, true);
        addTip("app_battery_usage", R.string.tip_app_battery_usage_title,
                R.string.tip_app_battery_usage_desc, R.string.tip_app_battery_usage_status,
                R.string.tip_action_app_battery_usage, R.drawable.ic_battery_bolt, null, true);
        addTip("battery_saver", R.string.tip_battery_saver_title, R.string.tip_battery_saver_desc,
                batterySaverStatus(context), R.string.tip_action_battery_saver, R.drawable.ic_power_save,
                null, true);
        applyTipsFilter();
    }

    private void addTip(String id, int title, int description, int status, int action, int icon,
                        SettingsActionHelper.Action actionType, boolean actionable) {
        boolean done = tipsRepository.isCompleted(id);
        allTips.add(new BatteryTip(id, getString(title), getString(description), getString(status),
                getString(action), icon, done, actionable, "brightness".equals(id) ? brightnessPercent : -1));
    }
    private void addTip(String id, int title, int description, String status, int action, int icon,
                        SettingsActionHelper.Action actionType, boolean actionable) {
        boolean done = tipsRepository.isCompleted(id);
        allTips.add(new BatteryTip(id, getString(title), getString(description), status,
                getString(action), icon, done, actionable, "brightness".equals(id) ? brightnessPercent : -1));
    }

    private void applyTipsFilter() {
        TipsChecklist.Filter filter = tipsFilter == R.id.tipsFilterDone ? TipsChecklist.Filter.COMPLETED
                : tipsFilter == R.id.tipsFilterPending ? TipsChecklist.Filter.INCOMPLETE : TipsChecklist.Filter.ALL;
        shownTips.clear(); shownTips.addAll(TipsChecklist.filter(allTips, filter));
        if (tipsAdapter != null) tipsAdapter.notifyDataSetChanged();
        if (tipsEmpty != null) tipsEmpty.setVisibility(shownTips.isEmpty() ? View.VISIBLE : View.GONE);
        int complete = TipsChecklist.completedCount(allTips);
        if (completedSummary != null) completedSummary.setText(getString(R.string.tips_completed_summary, complete, allTips.size()));
        if (attentionSummary != null) attentionSummary.setText(getString(R.string.tips_attention_summary, allTips.size() - complete));
    }

    private void performTipAction(BatteryTip tip) {
        if (!isAdded()) return;
        if ("battery_saver".equals(tip.id)) {
            SettingsActionHelper.Result result = SettingsActionHelper.openBatterySaver(requireContext());
            if (result == SettingsActionHelper.Result.OPENED_GENERAL_SETTINGS) {
                android.widget.Toast.makeText(requireContext(), R.string.tips_battery_saver_fallback,
                        android.widget.Toast.LENGTH_LONG).show();
            } else if (result == SettingsActionHelper.Result.OPENED) {
                android.widget.Toast.makeText(requireContext(), R.string.tips_battery_saver_opened,
                        android.widget.Toast.LENGTH_LONG).show();
            } else {
                android.widget.Toast.makeText(requireContext(), R.string.tips_settings_unavailable,
                        android.widget.Toast.LENGTH_LONG).show();
            }
            return;
        }
        if ("app_battery_usage".equals(tip.id)) {
            if (!SettingsActionHelper.openAppBatteryUsageSettings(requireContext())) {
                android.widget.Toast.makeText(requireContext(), R.string.tips_app_battery_usage_unavailable,
                        android.widget.Toast.LENGTH_LONG).show();
            }
            return;
        }
        if ("bluetooth".equals(tip.id)) {
            if (Build.VERSION.SDK_INT >= 31 && !hasBluetoothPermission()) {
                bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT);
                return;
            }
            SettingsActionHelper.Result result = SettingsActionHelper.openBluetooth(requireContext());
            if (result == SettingsActionHelper.Result.OPENED_GENERAL_SETTINGS
                    || (result == SettingsActionHelper.Result.OPENED && Build.VERSION.SDK_INT >= 31)) {
                android.widget.Toast.makeText(requireContext(), R.string.tips_bluetooth_fallback, android.widget.Toast.LENGTH_LONG).show();
            } else if (result == SettingsActionHelper.Result.UNAVAILABLE) {
                android.widget.Toast.makeText(requireContext(), R.string.tips_bluetooth_unavailable, android.widget.Toast.LENGTH_LONG).show();
            }
            return;
        }
        if ("brightness".equals(tip.id) && AppSettings.supportsSystemBrightnessPercent()
                && !AppSettings.canWriteSystemBrightness(requireContext())) {
            if (!SettingsActionHelper.openWriteSettingsAccess(requireContext())) {
                android.widget.Toast.makeText(requireContext(), R.string.tips_settings_unavailable, android.widget.Toast.LENGTH_SHORT).show();
            }
            return;
        }
        boolean opened;
        if ("dark_mode".equals(tip.id)) {
            int mode = AppSettings.darkMode(requireContext()) ? AppCompatDelegate.MODE_NIGHT_NO : AppCompatDelegate.MODE_NIGHT_YES;
            AppSettings.setThemeMode(requireContext(), mode); AppCompatDelegate.setDefaultNightMode(mode); return;
        }
        SettingsActionHelper.Action action;
        switch (tip.id) {
            case "brightness": case "screen_timeout": action = SettingsActionHelper.Action.DISPLAY; break;
            case "wifi": action = SettingsActionHelper.Action.WIFI; break;
            case "bluetooth": action = SettingsActionHelper.Action.BLUETOOTH; break;
            case "location": action = SettingsActionHelper.Action.LOCATION; break;
            default: return;
        }
        opened = SettingsActionHelper.open(requireContext(), action);
        if (!opened) android.widget.Toast.makeText(requireContext(), R.string.tips_settings_unavailable, android.widget.Toast.LENGTH_SHORT).show();
    }

    private boolean isDark(Context context) { return AppSettings.darkMode(context); }
    private String wifiStatus(Context context) {
        try {
            WifiManager manager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (manager == null) return getString(R.string.tip_status_unknown);
            return getString(manager.isWifiEnabled() ? R.string.tip_status_enabled : R.string.tip_status_disabled);
        } catch (RuntimeException e) { return getString(R.string.tip_status_unknown); }
    }
    private String bluetoothStatus() {
        if (Build.VERSION.SDK_INT >= 31 && !hasBluetoothPermission()) return getString(R.string.tip_bluetooth_permission_status);
        try {
            BluetoothManager manager = (BluetoothManager) requireContext().getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
            if (adapter == null) return getString(R.string.tip_status_unknown);
            return getString(adapter.isEnabled() ? R.string.tip_status_enabled : R.string.tip_status_disabled);
        } catch (RuntimeException e) { return getString(R.string.tip_status_unknown); }
    }
    private boolean hasBluetoothPermission() {
        return Build.VERSION.SDK_INT < 31 || ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }
    private String locationStatus(Context context) {
        if (Build.VERSION.SDK_INT < 28) return getString(R.string.tip_status_unknown);
        try { android.location.LocationManager manager = (android.location.LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            return getString(manager != null && manager.isLocationEnabled() ? R.string.tip_status_enabled : R.string.tip_status_disabled);
        } catch (RuntimeException e) { return getString(R.string.tip_status_unknown); }
    }
    private String timeoutStatus(Context context) {
        try { int ms = Settings.System.getInt(context.getContentResolver(), Settings.System.SCREEN_OFF_TIMEOUT);
            ScreenTimeoutFormatter.Parts parts = ScreenTimeoutFormatter.format(ms);
            if (parts == null) return getString(R.string.tip_status_unknown);
            if (parts.minutes == 0) return getString(R.string.tip_timeout_seconds, parts.seconds);
            if (parts.seconds == 0) return getString(R.string.tip_timeout_minutes, parts.minutes);
            return getString(R.string.tip_timeout_minutes_seconds, parts.minutes, parts.seconds);
        } catch (Settings.SettingNotFoundException | SecurityException e) { return getString(R.string.tip_status_unknown); }
    }
    private String batterySaverStatus(Context context) {
        try {
            PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            Boolean enabled = powerManager == null ? null : powerManager.isPowerSaveMode();
            BatterySaverMode.State state = BatterySaverMode.from(enabled);
            if (state == BatterySaverMode.State.ENABLED) return getString(R.string.tip_status_enabled);
            if (state == BatterySaverMode.State.DISABLED) return getString(R.string.tip_status_disabled);
            return getString(R.string.tip_status_unknown);
        } catch (RuntimeException e) {
            return getString(R.string.tip_status_unknown);
        }
    }
    @Override public void onDestroy() {
        geminiGeneration++;
        if (geminiTask != null) geminiTask.cancel(true);
        if (offlineAnalysisTask != null) offlineAnalysisTask.cancel(true);
        if (geminiIo != null) geminiIo.shutdownNow();
        super.onDestroy();
    }
}

