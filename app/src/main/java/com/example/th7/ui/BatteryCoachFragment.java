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
import com.example.th7.R;
import com.example.th7.adapter.BatteryTipAdapter;
import com.example.th7.data.AppSettings;
import com.example.th7.data.BatterySaverMode;
import com.example.th7.data.SettingsActionHelper;
import com.example.th7.data.TipsRepository;
import com.example.th7.data.TipsChecklist;
import com.example.th7.data.ScreenTimeoutFormatter;
import com.example.th7.model.BatteryTip;
import com.google.android.material.chip.ChipGroup;
import java.util.ArrayList;
import java.util.List;

public class BatteryCoachFragment extends Fragment {
    private final List<BatteryTip> allTips = new ArrayList<>(), shownTips = new ArrayList<>();
    private BatteryTipAdapter tipsAdapter;
    private TipsRepository tipsRepository;
    private RecyclerView tipsList;
    private TextView completedSummary, attentionSummary, tipsEmpty;
    private int tipsFilter = R.id.tipsFilterAll;
    private int brightnessPercent = -1;
    private SwipeRefreshLayout refresh;
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
        View content=inflater.inflate(R.layout.fragment_battery_coach, container, false); refresh=new SwipeRefreshLayout(requireContext()); refresh.setColorSchemeResources(R.color.tech_cyan); refresh.addView(content); refresh.setOnRefreshListener(()->((MainActivity)requireActivity()).refreshBatteryState(ok->{if(isAdded()){refreshTips();if(!ok)android.widget.Toast.makeText(requireContext(),R.string.refresh_failed,android.widget.Toast.LENGTH_SHORT).show();}if(refresh!=null)refresh.setRefreshing(false);}));return refresh;
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        tipsRepository = new TipsRepository(requireContext());
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
    }

    @Override public void onResume() { super.onResume(); refreshTips(); }

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

}

