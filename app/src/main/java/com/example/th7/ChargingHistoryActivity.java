package com.example.th7;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.th7.adapter.ChargingHistoryAdapter;
import com.example.th7.data.AppSettings;
import com.example.th7.data.BatteryRepository;
import com.example.th7.model.ChargingSession;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChargingHistoryActivity extends AppCompatActivity {
    private RecyclerView recycler;
    private ChargingHistoryAdapter adapter;
    private final List<ChargingSession> all = new ArrayList<>();
    private final List<ChargingSession> shown = new ArrayList<>();
    private View empty;
    private TextView total;
    private TextView average;
    private BatteryRepository repo;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private int typeFilter = R.id.chipHistAll;
    private int days;
    private SwipeRefreshLayout refresh; private boolean loading;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_charging_history);
        installRefreshWrapper(R.id.recyclerChargingHistory);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(android.R.id.content), (v, insets) -> {
                    androidx.core.graphics.Insets systemBars = insets.getInsets(
                            androidx.core.view.WindowInsetsCompat.Type.systemBars());
                    v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
                    return insets;
                });

        MaterialToolbar toolbar = findViewById(R.id.historyToolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setOnMenuItemClickListener(this::handleMenu);

        recycler = findViewById(R.id.recyclerChargingHistory);
        empty = findViewById(R.id.layoutEmptyHistory);
        total = findViewById(R.id.tvTotalSessions);
        average = findViewById(R.id.tvAverageDuration);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        repo = new BatteryRepository(this);
        refresh.setOnRefreshListener(()->load(true));

        adapter = new ChargingHistoryAdapter(shown, new ChargingHistoryAdapter.OnItemActionCallback() {
            @Override
            public void onItemClick(ChargingSession item, int position) {
                showDetails(item);
            }

            @Override
            public void onContextMenuAction(ChargingSession item, int position, int menuId) {
                if (menuId == R.id.ctx_view_detail) {
                    showDetails(item);
                } else if (menuId == R.id.ctx_star_item) {
                    item.setStarred(!item.isStarred());
                    io.execute(() -> repo.setStarred(item.getId(), item.isStarred()));
                    adapter.notifyItemChanged(position);
                } else if (menuId == R.id.ctx_delete_item && all.contains(item)) {
                    new AlertDialog.Builder(ChargingHistoryActivity.this)
                            .setTitle(R.string.history_confirm_delete_title)
                            .setMessage(R.string.history_confirm_delete_message)
                            .setNegativeButton(R.string.common_cancel, null)
                            .setPositiveButton(R.string.notification_delete_button, (dialog, which) -> {
                                repo.deleteSession(item.getId());
                                all.removeIf(session -> item.getId().equals(session.getId()));
                                applyFilters();
                            })
                            .show();
                }
            }
        });
        recycler.setAdapter(adapter);
        registerForContextMenu(recycler);

        ChipGroup chips = findViewById(R.id.chipGroupHistoryFilters);
        chips.findViewById(R.id.chipHistStandard).setVisibility(View.GONE);
        ((Chip) chips.findViewById(R.id.chipHistAll)).setText(R.string.history_filter_all);
        ((Chip) chips.findViewById(R.id.chipHistFast)).setText(R.string.history_filter_wired);
        ((Chip) chips.findViewById(R.id.chipHistWireless)).setText(R.string.history_filter_wireless);
        chips.setOnCheckedStateChangeListener((group, ids) -> {
            if (!ids.isEmpty()) {
                typeFilter = ids.get(0);
                applyFilters();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        load(false);
    }

    private void installRefreshWrapper(int viewId){View child=findViewById(viewId);ViewGroup parent=(ViewGroup)child.getParent();int index=parent.indexOfChild(child);ViewGroup.LayoutParams old=child.getLayoutParams();parent.removeView(child);refresh=new SwipeRefreshLayout(this);refresh.setId(View.generateViewId());refresh.setColorSchemeResources(R.color.tech_cyan);refresh.setLayoutParams(old);child.setLayoutParams(new SwipeRefreshLayout.LayoutParams(-1,-1));refresh.addView(child);parent.addView(refresh,index,old);}
    private void load(){load(false);}
    private void load(boolean fromRefresh) {
        if(loading){if(refresh!=null)refresh.setRefreshing(false);return;}loading=true;
        io.execute(() -> {
            try {ArrayList<ChargingSession> result = repo.getSessions();
            runOnUiThread(() -> {loading=false;if(refresh!=null)refresh.setRefreshing(false);
                all.clear();
                all.addAll(result);
                int wired = 0;
                int wireless = 0;
                for (ChargingSession session : all) {
                    String source = session.getChargeType();
                    if ("AC".equalsIgnoreCase(source) || "USB".equalsIgnoreCase(source)) wired++;
                    if ("Wireless".equalsIgnoreCase(source)) wireless++;
                }
                ((Chip) findViewById(R.id.chipHistAll)).setText(
                        getString(R.string.history_filter_all_count, all.size()));
                ((Chip) findViewById(R.id.chipHistFast)).setText(
                        getString(R.string.history_filter_wired_count, wired));
                ((Chip) findViewById(R.id.chipHistWireless)).setText(
                        getString(R.string.history_filter_wireless));
                applyFilters();
            });} catch(RuntimeException error){runOnUiThread(()->{loading=false;if(refresh!=null)refresh.setRefreshing(false);if(fromRefresh)android.widget.Toast.makeText(this,R.string.refresh_failed,android.widget.Toast.LENGTH_SHORT).show();});}
        });
    }

    private void applyFilters() {
        shown.clear();
        long since = days == 0 ? 0 : System.currentTimeMillis() - days * 24L * 60 * 60 * 1000;
        for (ChargingSession session : all) {
            if (days > 0 && session.getStartedAt() < since) continue;
            String source = session.getChargeType().toLowerCase(Locale.ROOT);
            if (typeFilter == R.id.chipHistFast
                    && !(source.equals("ac") || source.equals("usb"))) continue;
            if (typeFilter == R.id.chipHistWireless && !source.equals("wireless")) continue;
            shown.add(session);
        }
        adapter.notifyDataSetChanged();
        updateSummary();
    }

    private boolean handleMenu(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_filter_time) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.history_filter_dialog_title)
                    .setItems(R.array.history_time_filter_items, (dialog, which) -> {
                        days = which == 0 ? 0 : which == 1 ? 1 : which == 2 ? 7 : 30;
                        applyFilters();
                    })
                    .show();
            return true;
        }
        if (id == R.id.action_clear_history) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.history_clear_confirm_title)
                    .setMessage(R.string.history_clear_confirm_message)
                    .setNegativeButton(R.string.common_cancel, null)
                    .setPositiveButton(R.string.notification_delete_button, (dialog, which) ->
                            io.execute(() -> {
                                repo.clearSessions();
                                runOnUiThread(() -> {
                                    all.clear();
                                    applyFilters();
                                });
                            }))
                    .show();
            return true;
        }
        return false;
    }

    private void showDetails(ChargingSession session) {
        String source;
        if ("AC".equalsIgnoreCase(session.getChargeType())) {
            source = getString(R.string.history_source_ac);
        } else if ("USB".equalsIgnoreCase(session.getChargeType())) {
            source = getString(R.string.history_source_usb);
        } else if ("Wireless".equalsIgnoreCase(session.getChargeType())) {
            source = getString(R.string.history_source_wireless);
        } else if (session.getChargeType() == null || session.getChargeType().isEmpty()) {
            source = getString(R.string.history_source_unknown);
        } else {
            source = getString(R.string.history_source_other);
        }
        String details = getString(R.string.history_details_format, source, session.getTimeRange(),
                session.getStartPercent(), session.getEndPercent(), session.getDuration());
        new AlertDialog.Builder(this)
                .setTitle(R.string.history_details_title)
                .setMessage(details)
                .setPositiveButton(R.string.common_close, null)
                .show();
    }

    private void updateSummary() {
        boolean none = shown.isEmpty();
        recycler.setVisibility(none ? View.GONE : View.VISIBLE);
        empty.setVisibility(none ? View.VISIBLE : View.GONE);
        total.setText(getString(R.string.history_total_sessions_count, shown.size()));

        long sum = 0;
        int completedCount = 0;
        for (ChargingSession session : shown) {
            if (session.getEndedAt() > session.getStartedAt() && session.isEndTimeObserved()) {
                sum += session.getEndedAt() - session.getStartedAt();
                completedCount++;
            }
        }
        long averageDurationMs = completedCount == 0 ? 0 : sum / completedCount;
        average.setText(completedCount == 0
                ? getString(R.string.history_average_unknown)
                : averageDurationMs < 60000
                    ? getString(R.string.history_duration_under_minute)
                    : getString(R.string.history_duration_minutes, averageDurationMs / 60000));
    }

    @Override
    protected void onDestroy() {
        io.shutdown();
        super.onDestroy();
    }
}
