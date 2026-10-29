package com.example.th7.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.example.th7.MainActivity;
import com.example.th7.R;
import com.example.th7.data.BatteryRepository;
import com.example.th7.data.BatteryRuntimeEstimator;
import com.example.th7.data.BatteryStateMapper;
import com.example.th7.data.BatteryThermalState;
import com.example.th7.model.BatterySnapshot;
import com.example.th7.widget.CircularBatteryGaugeView;
import com.example.th7.widget.TechGraphView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DashboardFragment extends Fragment {
    private CircularBatteryGaugeView gauge;
    private TextView statusDetail, thermalValue, thermalStatus, thermalBadge, chargeBadge, chargingValue, chargingStatus, usageValue, healthStatus, healthInfo;
    private TechGraphView miniGraph;
    private BatteryRepository repository;
    private SwipeRefreshLayout refresh;
    private boolean graphLoading;
    private int usageEstimateRequest;
    private long lastUsageEstimateTimestamp = Long.MIN_VALUE;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    @Nullable @Override public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle state) {
        View content=inflater.inflate(R.layout.fragment_dashboard, container, false); refresh=new SwipeRefreshLayout(requireContext()); refresh.setColorSchemeResources(R.color.tech_cyan); refresh.addView(content); refresh.setOnRefreshListener(()->((MainActivity)requireActivity()).refreshBatteryState(ok->{if(ok){render(((MainActivity)requireActivity()).getLatestBattery());loadGraph();}else android.widget.Toast.makeText(requireContext(),R.string.refresh_failed,android.widget.Toast.LENGTH_SHORT).show();if(refresh!=null)refresh.setRefreshing(false);})); return refresh;
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        gauge=view.findViewById(R.id.circularGaugeView); miniGraph=view.findViewById(R.id.miniGraphView);
        statusDetail=view.findViewById(R.id.tvBatteryStatusDetail); thermalValue=view.findViewById(R.id.tvQuickThermalValue);
        thermalStatus=view.findViewById(R.id.tvQuickThermalStatus); thermalBadge=view.findViewById(R.id.tvQuickThermalBadge);
        chargeBadge=view.findViewById(R.id.tvQuickChargeBadge); healthStatus=view.findViewById(R.id.tvHealthStatus);
        healthInfo=view.findViewById(R.id.tvHealthInfo); chargingValue=view.findViewById(R.id.tvQuickChargingValue);
        chargingStatus=view.findViewById(R.id.tvQuickChargingStatus); usageValue=view.findViewById(R.id.tvQuickUsageValue);
        repository=new BatteryRepository(requireContext());
        miniGraph.setData(new float[0],new String[0],"%");
        view.findViewById(R.id.btnNavStatistics).setOnClickListener(v->{if(getActivity() instanceof MainActivity)((MainActivity)getActivity()).navigateToTab(R.id.nav_statistics);});
        render(((MainActivity)requireActivity()).getLatestBattery());
    }

    public void render(BatterySnapshot battery) {
        if(gauge==null)return;
        if(battery==null){
            gauge.setProgress(-1,false); gauge.setStatusText(getString(R.string.value_unavailable)); gauge.setSubtitleText(getString(R.string.battery_status_unavailable));
            statusDetail.setText(R.string.label_battery_unreadable); thermalValue.setText(R.string.thermal_state_unknown); thermalStatus.setText(R.string.thermal_state_source); thermalBadge.setText(R.string.thermal_state_unknown);
            chargingValue.setText(R.string.battery_status_unavailable); chargingStatus.setText(R.string.battery_source_unavailable); chargeBadge.setText(R.string.badge_data);
            healthStatus.setText(R.string.battery_health_unavailable); healthInfo.setText(R.string.battery_health_android_disclaimer);
            TextView updated=getView().findViewById(R.id.tvBatteryLastUpdated); if(updated!=null)updated.setText(R.string.value_unavailable);
            usageValue.setText(R.string.dashboard_usage_collecting); return;
        }
        gauge.setProgress(battery.level, true);
        BatteryStateMapper.Status batteryStatus=battery.statusType();
        BatteryStateMapper.Source batterySource=battery.sourceType();
        BatteryStateMapper.Health batteryHealth=battery.healthType();
        gauge.setStatusText(battery.level < 0 ? getString(R.string.value_unavailable) : getString(battery.level > 60 ? R.string.gauge_status_high : battery.level > 20 ? R.string.gauge_status_normal : R.string.gauge_status_low));
        gauge.setSubtitleText(getString(statusResource(batteryStatus)));
        String current=battery.currentMa==null?getString(R.string.label_current_unavailable):getString(R.string.label_current_format,battery.currentMa);
        String voltage=battery.voltageV==null?getString(R.string.label_voltage_unavailable):getString(R.string.label_voltage_format,battery.voltageV);
        statusDetail.setText(getString(R.string.battery_status_details, getString(statusResource(batteryStatus)), getString(sourceResource(batterySource)), current, voltage));
        TextView updated=getView().findViewById(R.id.tvBatteryLastUpdated);
        if(updated!=null){
            updated.setText(battery.timestamp>0?getString(R.string.battery_last_updated,new SimpleDateFormat("HH:mm:ss",Locale.getDefault()).format(new Date(battery.timestamp))):getString(R.string.value_unavailable));
        }
        BatteryThermalState.State thermal = BatteryThermalState.fromHealth(batteryHealth);
        int thermalLabel = thermal == BatteryThermalState.State.OVERHEAT ? R.string.thermal_state_overheat
                : thermal == BatteryThermalState.State.NORMAL ? R.string.thermal_state_normal : R.string.thermal_state_unknown;
        thermalValue.setText(thermalLabel);
        chargingValue.setText(chargePathResource(battery.chargePath()));
        chargeBadge.setText(batteryStatus==BatteryStateMapper.Status.CHARGING?R.string.badge_charging:batteryStatus==BatteryStateMapper.Status.FULL?R.string.battery_status_full:batteryStatus==BatteryStateMapper.Status.DISCHARGING?R.string.badge_using_battery:batteryStatus==BatteryStateMapper.Status.NOT_CHARGING?R.string.label_not_charging:R.string.value_unknown);
        chargingStatus.setText(sourceResource(batterySource)); updateUsageEstimate(battery, false);
        healthStatus.setText(healthResource(batteryHealth)); healthInfo.setText(R.string.battery_health_android_disclaimer);
        thermalStatus.setText(R.string.thermal_state_source);
        thermalBadge.setText(thermalLabel);
        thermalBadge.setBackgroundResource(thermal == BatteryThermalState.State.OVERHEAT ? R.drawable.bg_badge_red
                : thermal == BatteryThermalState.State.NORMAL ? R.drawable.bg_badge_green : R.drawable.bg_badge_demo);
    }

    private int statusResource(BatteryStateMapper.Status status){
        switch(status){case CHARGING:return R.string.battery_status_charging;case DISCHARGING:return R.string.battery_status_discharging;case NOT_CHARGING:return R.string.battery_status_not_charging;case FULL:return R.string.battery_status_full;case UNKNOWN:return R.string.battery_status_unknown;case UNAVAILABLE:return R.string.battery_status_unavailable;default:return R.string.battery_status_unrecognized;}
    }
    private int sourceResource(BatteryStateMapper.Source source){
        switch(source){case NONE:return R.string.battery_source_none;case AC:return R.string.battery_source_ac;case USB:return R.string.battery_source_usb;case WIRELESS:return R.string.battery_source_wireless;case OTHER:return R.string.battery_source_other;case UNKNOWN:return R.string.battery_source_unknown;case UNAVAILABLE:return R.string.battery_source_unavailable;default:return R.string.battery_source_unrecognized;}
    }
    private int healthResource(BatteryStateMapper.Health health){
        switch(health){case GOOD:return R.string.battery_health_good;case OVERHEAT:return R.string.battery_health_overheat;case DEAD:return R.string.battery_health_dead;case OVER_VOLTAGE:return R.string.battery_health_over_voltage;case UNSPECIFIED_FAILURE:return R.string.battery_health_unspecified_failure;case COLD:return R.string.battery_health_cold;case UNKNOWN:return R.string.battery_health_unknown;case UNAVAILABLE:return R.string.battery_health_unavailable;default:return R.string.battery_health_unrecognized;}
    }
    private int chargePathResource(BatteryStateMapper.ChargePath path){
        switch(path){case AC:return R.string.battery_status_charging_ac;case USB:return R.string.battery_status_charging_usb;case WIRELESS:return R.string.battery_status_charging_wireless;case CHARGING_SOURCE_UNKNOWN:return R.string.battery_status_charging_source_unknown;case DISCHARGING:return R.string.battery_status_discharging;case NOT_CHARGING:return R.string.battery_status_not_charging;case FULL:return R.string.battery_status_full;case UNKNOWN:return R.string.battery_status_unknown;case UNAVAILABLE:return R.string.battery_status_unavailable;default:return R.string.battery_status_unrecognized;}
    }

    private void updateUsageEstimate(BatterySnapshot battery, boolean force) {
        if (usageValue == null || battery == null) return;
        if (battery.isPlugged() || battery.statusType() == BatteryStateMapper.Status.CHARGING) {
            usageEstimateRequest++;
            usageValue.setText(R.string.dashboard_usage_charging);
            return;
        }
        if (battery.level == 0) {
            usageEstimateRequest++;
            usageValue.setText(R.string.dashboard_usage_depleted);
            return;
        }
        if (battery.statusType() != BatteryStateMapper.Status.DISCHARGING) {
            usageEstimateRequest++;
            usageValue.setText(R.string.dashboard_usage_collecting);
            return;
        }
        if (!force && lastUsageEstimateTimestamp == battery.timestamp) return;
        lastUsageEstimateTimestamp = battery.timestamp;
        final int request = ++usageEstimateRequest;
        final long now = System.currentTimeMillis();
        if (repository == null || io.isShutdown()) {
            usageValue.setText(R.string.dashboard_usage_collecting);
            return;
        }
        io.execute(() -> {
            long estimate = -1L;
            try {
                List<BatterySnapshot> samples = repository.getSamplesSince(now - 24L * 60L * 60L * 1000L);
                estimate = BatteryRuntimeEstimator.estimateRemainingMinutes(samples, battery, now);
            } catch (RuntimeException ignored) { }
            final long result = estimate;
            if (getActivity() != null) getActivity().runOnUiThread(() -> {
                if (usageValue == null || request != usageEstimateRequest) return;
                if (result <= 0L) usageValue.setText(R.string.dashboard_usage_collecting);
                else if (result < 60L) usageValue.setText(getString(R.string.dashboard_usage_minutes, result));
                else {
                    long hours = result / 60L;
                    long minutes = result % 60L;
                    usageValue.setText(minutes == 0L
                            ? getString(R.string.dashboard_usage_hours, hours)
                            : getString(R.string.dashboard_usage_hours_minutes, hours, minutes));
                }
            });
        });
    }

    /** Called after the activity has persisted the latest battery broadcast sample. */
    public void onBatterySampleRecorded(BatterySnapshot battery) {
        if (usageValue != null) updateUsageEstimate(battery, true);
    }

    @Override public void onResume(){super.onResume();if(gauge!=null&&getActivity() instanceof MainActivity)render(((MainActivity)getActivity()).getLatestBattery());loadGraph();}
    private void loadGraph(){if(repository==null||miniGraph==null||io.isShutdown()||graphLoading)return;graphLoading=true;io.execute(()->{try{List<BatterySnapshot> samples=repository.getSamplesSince(System.currentTimeMillis()-86400000L);ArrayList<BatterySnapshot> valid=new ArrayList<>();for(BatterySnapshot sample:samples)if(sample.level>=0&&sample.level<=100)valid.add(sample);if(valid.size()>12)valid=new ArrayList<>(valid.subList(valid.size()-12,valid.size()));float[] data=new float[valid.size()];String[] labels=new String[data.length];SimpleDateFormat format=new SimpleDateFormat("HH:mm",Locale.getDefault());for(int i=0;i<data.length;i++){data[i]=valid.get(i).level;labels[i]=format.format(new Date(valid.get(i).timestamp));}if(getActivity()!=null)getActivity().runOnUiThread(()->{graphLoading=false;if(miniGraph!=null)miniGraph.setData(data,labels,"%");});}catch(RuntimeException e){if(getActivity()!=null)getActivity().runOnUiThread(()->graphLoading=false);}});}
    @Override public void onDestroyView(){usageEstimateRequest++;gauge=null;miniGraph=null;statusDetail=null;thermalValue=null;thermalStatus=null;thermalBadge=null;chargeBadge=null;healthStatus=null;healthInfo=null;chargingValue=null;chargingStatus=null;usageValue=null;super.onDestroyView();}
    @Override public void onDestroy(){io.shutdown();super.onDestroy();}
}

