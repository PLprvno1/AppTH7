package com.example.th7.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.th7.R;
import com.example.th7.adapter.LabScenarioAdapter;
import com.example.th7.model.LabScenario;
import com.example.th7.data.AppSettings;
import com.example.th7.widget.CircularBatteryGaugeView;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class BatteryLabFragment extends Fragment {

    private CircularBatteryGaugeView labGaugeView;
    private SeekBar seekBarBattery;
    private TextView tvSliderPercentValue;
    private TextView tvSimDischargeRate, tvThermalState, tvSimRemainingTime, tvSimEfficiency;
    private MaterialButton btnStartSim, btnResetSim;

    private List<LabScenario> scenarios;
    private LabScenarioAdapter scenarioAdapter;
    private LabScenario selectedScenario;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean isSimulating = false;
    private Runnable simulationCompletion;
    private SwipeRefreshLayout refresh;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View content=inflater.inflate(R.layout.fragment_battery_lab, container, false); refresh=new SwipeRefreshLayout(requireContext());refresh.setColorSchemeResources(R.color.tech_cyan);refresh.addView(content);refresh.setOnRefreshListener(()->((com.example.th7.MainActivity)requireActivity()).refreshBatteryState(ok->{if(ok)refreshThermalState();else Toast.makeText(requireContext(),R.string.refresh_failed,Toast.LENGTH_SHORT).show();if(refresh!=null)refresh.setRefreshing(false);}));return refresh;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        labGaugeView = view.findViewById(R.id.labGaugeView);
        seekBarBattery = view.findViewById(R.id.seekBarBattery);
        tvSliderPercentValue = view.findViewById(R.id.tvSliderPercentValue);
        tvSimDischargeRate = view.findViewById(R.id.tvSimDischargeRate);
        tvThermalState = view.findViewById(R.id.tvThermalState);
        tvSimRemainingTime = view.findViewById(R.id.tvSimRemainingTime);
        tvSimEfficiency = view.findViewById(R.id.tvSimEfficiency);
        btnStartSim = view.findViewById(R.id.btnStartSim);
        btnResetSim = view.findViewById(R.id.btnResetSim);

        labGaugeView.post(() -> {
            labGaugeView.setProgress(78, false);
            labGaugeView.setStatusText(getString(R.string.lab_simulation_status));
            labGaugeView.setSubtitleText(getString(R.string.lab_simulation_subtitle));
        });

        // Initialize scenarios
        initScenarios(view);

        // SeekBar listener
        seekBarBattery.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvSliderPercentValue.setText(progress + "%");
                labGaugeView.setProgress(progress, false);
                labGaugeView.setStatusText(getString(R.string.lab_simulation_status));
                updateCalculation(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { if(refresh!=null)refresh.setEnabled(false); }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) { if(refresh!=null)refresh.setEnabled(true); }
        });

        // Start Simulation Button
        btnStartSim.setOnClickListener(v -> {
            if (isSimulating) return;
            isSimulating = true;
            btnStartSim.setText(R.string.btn_simulating);
            btnStartSim.setEnabled(false);

            int current = seekBarBattery.getProgress();
            int target = Math.max(5, current - 15);
            if (selectedScenario != null && selectedScenario.getDischargeRateMa() > 0) {
                target = Math.min(100, current + 15);
            }
            final int simulationTarget = target;
            labGaugeView.setProgress(simulationTarget, AppSettings.animations(requireContext()));
            labGaugeView.setStatusText(getString(R.string.lab_simulation_status));

            simulationCompletion = () -> {
                if (getContext() != null) {
                    seekBarBattery.setProgress(simulationTarget);
                    isSimulating = false;
                    simulationCompletion = null;
                    btnStartSim.setText(R.string.btn_start_sim);
                    btnStartSim.setEnabled(true);
                    Toast.makeText(getContext(), R.string.lab_simulation_done, Toast.LENGTH_SHORT).show();
                }
            };
            handler.postDelayed(simulationCompletion, 1200);
        });

        // Reset Button
        btnResetSim.setOnClickListener(v -> {
            cancelSimulation();
            seekBarBattery.setProgress(78);
            tvSliderPercentValue.setText("78%");
            labGaugeView.setProgress(78, true);
            labGaugeView.setStatusText(getString(R.string.lab_simulation_status));
            if (scenarioAdapter != null) {
                scenarioAdapter.setSelectedPosition(0);
                selectedScenario = scenarios.get(0);
                applyScenario(selectedScenario);
            }
            Toast.makeText(getContext(), R.string.lab_simulation_reset, Toast.LENGTH_SHORT).show();
        });
    }

    private void cancelSimulation(){if(simulationCompletion!=null){handler.removeCallbacks(simulationCompletion);simulationCompletion=null;}isSimulating=false;if(btnStartSim!=null){btnStartSim.setText(R.string.btn_start_sim);btnStartSim.setEnabled(true);}if(labGaugeView!=null&&seekBarBattery!=null){labGaugeView.setProgress(seekBarBattery.getProgress(),false);labGaugeView.setStatusText(getString(R.string.lab_simulation_status));}}

    @Override public void onResume(){super.onResume();refreshScenarioDisplay();}
    @Override public void onHiddenChanged(boolean hidden){super.onHiddenChanged(hidden);if(hidden)cancelSimulation();else refreshScenarioDisplay();}
    private void refreshScenarioDisplay(){if(selectedScenario!=null&&tvThermalState!=null)applyScenario(selectedScenario);refreshThermalState();}
    private void refreshThermalState(){if(tvThermalState==null||!isAdded())return;com.example.th7.model.BatterySnapshot s=((com.example.th7.MainActivity)requireActivity()).getLatestBattery();com.example.th7.data.BatteryThermalState.State t=s==null?com.example.th7.data.BatteryThermalState.State.UNKNOWN:com.example.th7.data.BatteryThermalState.fromHealth(s.healthType());tvThermalState.setText(t==com.example.th7.data.BatteryThermalState.State.OVERHEAT?R.string.thermal_state_overheat:t==com.example.th7.data.BatteryThermalState.State.NORMAL?R.string.thermal_state_normal:R.string.thermal_state_unknown);}

    @Override public void onPause(){cancelSimulation();super.onPause();}
    @Override public void onDestroyView(){cancelSimulation();handler.removeCallbacksAndMessages(null);super.onDestroyView();}

    private void initScenarios(View view) {
        scenarios = new ArrayList<>();
        scenarios.add(new LabScenario("1", getString(R.string.scenario_normal_title), getString(R.string.scenario_normal_desc), R.drawable.ic_dashboard, -350, getString(R.string.lab_scenario_time_normal)));
        scenarios.add(new LabScenario("2", getString(R.string.scenario_gaming_title), getString(R.string.scenario_gaming_desc), R.drawable.ic_game, -1850, getString(R.string.lab_scenario_time_gaming)));
        scenarios.add(new LabScenario("3", getString(R.string.scenario_video_title), getString(R.string.scenario_video_desc), R.drawable.ic_video, -750, getString(R.string.lab_scenario_time_video)));
        scenarios.add(new LabScenario("4", getString(R.string.scenario_charging_title), getString(R.string.scenario_charging_desc), R.drawable.ic_battery_bolt, 4500, getString(R.string.lab_scenario_time_charging)));
        scenarios.add(new LabScenario("5", getString(R.string.scenario_saver_title), getString(R.string.scenario_saver_desc), R.drawable.ic_power_save, -120, getString(R.string.lab_scenario_time_saver)));

        selectedScenario = scenarios.get(0);

        RecyclerView recyclerScenarios = view.findViewById(R.id.recyclerScenarios);
        recyclerScenarios.setLayoutManager(new LinearLayoutManager(getContext()));
        scenarioAdapter = new LabScenarioAdapter(scenarios, (scenario, position) -> {
            selectedScenario = scenario;
            applyScenario(scenario);
        });
        recyclerScenarios.setAdapter(scenarioAdapter);

        applyScenario(selectedScenario);
    }

    private void applyScenario(LabScenario scenario) {
        int rate = scenario.getDischargeRateMa();
        if (rate > 0) {
            tvSimDischargeRate.setText("+" + rate + " mA");
        } else {
            tvSimDischargeRate.setText(rate + " mA");
        }
        refreshThermalState();
        tvSimRemainingTime.setText(scenario.getRemainingTimeEstimate());

        if (rate > 0) {
            tvSimEfficiency.setText(getString(R.string.lab_model_capacity_assumption));
        } else if (rate < -1000) {
            tvSimEfficiency.setText(getString(R.string.lab_model_capacity_assumption));
        } else {
            tvSimEfficiency.setText(getString(R.string.lab_model_capacity_assumption));
        }
    }

    private void updateCalculation(int percent) {
        if (selectedScenario == null) return;
        // Dynamically estimate remaining time based on current slider percentage
        int rate = Math.abs(selectedScenario.getDischargeRateMa());
        if (selectedScenario.getDischargeRateMa() > 0) {
            int minsToFull = (int) (((100 - percent) / 100f) * 45);
            tvSimRemainingTime.setText(getString(R.string.lab_charge_full_estimate,minsToFull));
        } else {
            float hoursLeft = (percent / 100f) * (4500f / rate);
            int hrs = (int) hoursLeft;
            int mins = (int) ((hoursLeft - hrs) * 60);
            tvSimRemainingTime.setText(getString(R.string.lab_discharge_time_estimate,hrs,mins));
        }
    }
}

