package com.example.th7.ui;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.net.wifi.WifiManager;
import android.bluetooth.BluetoothAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.th7.MainActivity;
import com.example.th7.R;
import com.example.th7.adapter.AdviceAdapter;
import com.example.th7.adapter.BatteryTipAdapter;
import com.example.th7.data.AppSettings;
import com.example.th7.data.BatteryCoachEngine;
import com.example.th7.data.BatteryRepository;
import com.example.th7.data.BatterySaverMode;
import com.example.th7.data.SettingsActionHelper;
import com.example.th7.data.TipsRepository;
import com.example.th7.data.TipsChecklist;
import com.example.th7.data.ScreenTimeoutFormatter;
import com.example.th7.model.AdviceItem;
import com.example.th7.model.BatterySnapshot;
import com.example.th7.model.BatteryTip;
import com.google.android.material.chip.ChipGroup;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BatteryCoachFragment extends Fragment {
    private final List<AdviceItem> all = new ArrayList<>(), shown = new ArrayList<>();
    private final List<BatteryTip> allTips = new ArrayList<>(), shownTips = new ArrayList<>();
    private AdviceAdapter adviceAdapter;
    private BatteryTipAdapter tipsAdapter;
    private BatteryRepository repository;
    private TipsRepository tipsRepository;
    private ExecutorService io = Executors.newSingleThreadExecutor();
    private RecyclerView adviceList, tipsList;
    private TextView completedSummary, attentionSummary, tipsEmpty, adviceEmpty;
    private int adviceFilter = R.id.chipFilterAll;
    private int tipsFilter = R.id.tipsFilterAll;
    private int brightnessPercent = -1;
    private SwipeRefreshLayout refresh;

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        View content=inflater.inflate(R.layout.fragment_battery_coach, container, false); refresh=new SwipeRefreshLayout(requireContext()); refresh.setColorSchemeResources(R.color.tech_cyan); refresh.addView(content); refresh.setOnRefreshListener(()->((MainActivity)requireActivity()).refreshBatteryState(ok->{refreshTips();refreshAdvice(()->{if(!ok&&isAdded())android.widget.Toast.makeText(requireContext(),R.string.refresh_failed,android.widget.Toast.LENGTH_SHORT).show();if(refresh!=null)refresh.setRefreshing(false);});}));return refresh;
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        repository = new BatteryRepository(requireContext());
        tipsRepository = new TipsRepository(requireContext());
        adviceList = view.findViewById(R.id.recyclerAdvice); adviceList.setLayoutManager(new LinearLayoutManager(getContext()));
        adviceAdapter = new AdviceAdapter(shown); adviceList.setAdapter(adviceAdapter);
        tipsList = view.findViewById(R.id.recyclerTips); tipsList.setLayoutManager(new LinearLayoutManager(getContext()));
        tipsAdapter = new BatteryTipAdapter(shownTips, new BatteryTipAdapter.Listener() {
            @Override public void onAction(BatteryTip tip) { performTipAction(tip); }
            @Override public void onSecondaryAction(BatteryTip tip) { openSecondaryTipAction(tip); }
            @Override public void onBrightnessChanged(BatteryTip tip, int percent, boolean trackingStopped) {
                brightnessPercent = percent;
                AppSettings.setWindowBrightness(requireActivity(), percent);
                if (trackingStopped) AppSettings.saveWindowBrightness(requireContext(), percent);
            }
            @Override public void onCompletionChanged(BatteryTip tip, boolean completed) {
                tipsRepository.setCompleted(tip.id, completed); refreshTips();
            }
        });
        tipsList.setAdapter(tipsAdapter);
        completedSummary = view.findViewById(R.id.tipsCompletedSummary); attentionSummary = view.findViewById(R.id.tipsAttentionSummary);
        tipsEmpty = view.findViewById(R.id.tipsEmpty); adviceEmpty = view.findViewById(R.id.adviceEmpty);
        ChipGroup adviceGroup = view.findViewById(R.id.chipGroupCategories);
        adviceGroup.setOnCheckedStateChangeListener((g, ids) -> { if (!ids.isEmpty()) { adviceFilter = ids.get(0); applyAdviceFilter(); } });
        ChipGroup tipsGroup = view.findViewById(R.id.tipsFilterGroup);
        tipsGroup.setOnCheckedStateChangeListener((g, ids) -> { if (!ids.isEmpty()) { tipsFilter = ids.get(0); applyTipsFilter(); } });
        refreshTips();
    }

    @Override public void onResume() { super.onResume(); refreshTips(); refreshAdvice(); }

    private void refreshAdvice() {
        refreshAdvice(null);
    }
    private void refreshAdvice(Runnable finished) {
        if (repository == null || io.isShutdown()) return;
        BatterySnapshot current = getActivity() instanceof MainActivity ? ((MainActivity) getActivity()).getLatestBattery() : null;
        io.execute(() -> {
            try { List<BatterySnapshot> samples = repository.getSamplesSince(System.currentTimeMillis() - 24L * 60 * 60 * 1000);
                List<com.example.th7.model.ChargingSession> sessions = repository.getSessionsSince(System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000);
                List<AdviceItem> result = BatteryCoachEngine.evaluate(current, samples, sessions);
                if (getActivity() != null) getActivity().runOnUiThread(() -> {if (isAdded()) {all.clear(); all.addAll(result); applyAdviceFilter();} if(finished!=null)finished.run();});
            } catch (RuntimeException e) {if(getActivity()!=null)getActivity().runOnUiThread(()->{if(finished!=null)finished.run();});}
        });
    }

    private void refreshTips() {
        if (getContext() == null || tipsRepository == null) return;
        Context context = requireContext();
        brightnessPercent = AppSettings.initialWindowBrightnessPercent(requireActivity());
        allTips.clear();
        addTip("brightness", R.string.tip_brightness_title, R.string.tip_brightness_desc,
                brightnessPercent >= 0 ? R.string.tip_status_window_brightness : R.string.tip_status_unknown,
                R.string.tip_action_display, R.drawable.ic_lightbulb, SettingsActionHelper.Action.DISPLAY, true);
        addTip("dark_mode", R.string.tip_dark_title, R.string.tip_dark_desc,
                isDark(context) ? R.string.tip_status_dark_on : R.string.tip_status_dark_off,
                isDark(context) ? R.string.tip_action_light : R.string.tip_action_dark, R.drawable.ic_coach, null, true);
        addTip("wifi", R.string.tip_wifi_title, R.string.tip_wifi_desc, wifiStatus(context),
                R.string.tip_action_wifi, R.drawable.ic_game, SettingsActionHelper.Action.WIFI, true);
        addTip("bluetooth", R.string.tip_bluetooth_title, R.string.tip_bluetooth_desc, bluetoothStatus(),
                R.string.tip_action_bluetooth, R.drawable.ic_info, SettingsActionHelper.Action.BLUETOOTH, true);
        addTip("location", R.string.tip_location_title, R.string.tip_location_desc, locationStatus(context),
                R.string.tip_action_location, R.drawable.ic_info, SettingsActionHelper.Action.LOCATION, true);
        addTip("screen_timeout", R.string.tip_timeout_title, R.string.tip_timeout_desc, timeoutStatus(context),
                R.string.tip_action_display, R.drawable.ic_usage_time, SettingsActionHelper.Action.DISPLAY, true);
        addTip("background", R.string.tip_background_title, R.string.tip_background_desc, backgroundStatus(context),
                R.string.tip_action_app_info, R.drawable.ic_settings, SettingsActionHelper.Action.APP_INFO, true);
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

    private void applyAdviceFilter() {
        shown.clear();
        for (AdviceItem item : all) {
            String category = item.getCategory();
            if (adviceFilter == R.id.chipFilterAll || adviceFilter == R.id.chipFilterCharging && "Sạc pin".equals(category) ||

                    adviceFilter == R.id.chipFilterOpt && "Tối ưu pin".equals(category) ||
                    adviceFilter == R.id.chipFilterLow && "Pin yếu".equals(category)) shown.add(item);
        }
        if (adviceAdapter != null) adviceAdapter.notifyDataSetChanged();
        if (adviceEmpty != null) adviceEmpty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
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
        if ("bluetooth".equals(tip.id)) {
            SettingsActionHelper.Result result = SettingsActionHelper.openBluetooth(requireContext());
            if (result == SettingsActionHelper.Result.OPENED_GENERAL_SETTINGS
                    || (result == SettingsActionHelper.Result.OPENED && Build.VERSION.SDK_INT >= 31)) {
                android.widget.Toast.makeText(requireContext(), R.string.tips_bluetooth_fallback, android.widget.Toast.LENGTH_LONG).show();
            } else if (result == SettingsActionHelper.Result.UNAVAILABLE) {
                android.widget.Toast.makeText(requireContext(), R.string.tips_bluetooth_unavailable, android.widget.Toast.LENGTH_LONG).show();
            }
            return;
        }
        if ("background".equals(tip.id)) {
            SettingsActionHelper.Result result = SettingsActionHelper.openAppBattery(requireContext());
            if (result == SettingsActionHelper.Result.OPENED_APP_INFO_FALLBACK) {
                android.widget.Toast.makeText(requireContext(), R.string.tips_app_info_fallback, android.widget.Toast.LENGTH_LONG).show();
            } else if (result == SettingsActionHelper.Result.UNAVAILABLE) {
                android.widget.Toast.makeText(requireContext(), R.string.tips_settings_unavailable, android.widget.Toast.LENGTH_LONG).show();
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

    private void openSecondaryTipAction(BatteryTip tip) {
        if (!isAdded() || !"background".equals(tip.id)) return;
        if (!SettingsActionHelper.open(requireContext(), SettingsActionHelper.Action.BATTERY_OPTIMIZATION)) {
            android.widget.Toast.makeText(requireContext(), R.string.tips_settings_unavailable, android.widget.Toast.LENGTH_SHORT).show();
        }
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
        if (Build.VERSION.SDK_INT >= 31) return getString(R.string.tip_status_unknown);
        try {
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null) return getString(R.string.tip_status_unknown);
            return getString(adapter.isEnabled() ? R.string.tip_status_enabled : R.string.tip_status_disabled);
        } catch (RuntimeException e) { return getString(R.string.tip_status_unknown); }
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
    private String backgroundStatus(Context context) {
        try { PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            return getString(pm != null && pm.isIgnoringBatteryOptimizations(context.getPackageName()) ? R.string.tip_status_exempt : R.string.tip_status_optimized);
        } catch (RuntimeException e) { return getString(R.string.tip_status_unknown); }
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
    @Override public void onDestroy() { io.shutdown(); super.onDestroy(); }
}

