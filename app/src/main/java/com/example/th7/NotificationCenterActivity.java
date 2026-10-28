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
import com.example.th7.adapter.NotificationAdapter;
import com.example.th7.data.BatteryRepository;
import com.example.th7.data.AppSettings;
import com.example.th7.model.NotificationItem;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.chip.ChipGroup;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NotificationCenterActivity extends AppCompatActivity {
    private final List<NotificationItem> allItems = new ArrayList<>();
    private final List<NotificationItem> visibleItems = new ArrayList<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private RecyclerView recycler;
    private NotificationAdapter adapter;
    private View empty;
    private TextView summary, emptyTitle, emptyDescription;
    private BatteryRepository repository;
    private int categoryFilter = R.id.chipNotifAll;
    private SwipeRefreshLayout refresh; private boolean loading;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_notification_center);
        installRefreshWrapper(R.id.recyclerNotifications);
        AppSettings.applyWindowBrightness(this);
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content), (v, insets) -> {
            androidx.core.graphics.Insets i = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.systemBars());
            v.setPadding(i.left, i.top, i.right, 0);
            return insets;
        });
        MaterialToolbar toolbar = findViewById(R.id.notificationsToolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setOnMenuItemClickListener(this::handleMenu);
        recycler = findViewById(R.id.recyclerNotifications);
        empty = findViewById(R.id.layoutEmptyNotifications);
        summary = findViewById(R.id.tvUnreadSummary);
        emptyTitle = findViewById(R.id.tvNotificationEmptyTitle);
        emptyDescription = findViewById(R.id.tvNotificationEmptyDesc);
        repository = new BatteryRepository(this);
        refresh.setOnRefreshListener(()->load(true));
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NotificationAdapter(visibleItems, new NotificationAdapter.OnNotificationClickListener() {
            @Override public void onNotificationClick(NotificationItem item, int position) {
                io.execute(() -> repository.markNotificationRead(item.getId()));
                item.setRead(true);
                adapter.notifyItemChanged(position);
                updateSummary();
            }
            @Override public void onNotificationDelete(NotificationItem item, int position) {
                new AlertDialog.Builder(NotificationCenterActivity.this)
                        .setTitle(R.string.notification_delete_title)
                        .setMessage(item.getTitle())
                        .setNegativeButton(R.string.common_cancel, null)
                        .setPositiveButton(R.string.notification_delete_button, (dialog, which) -> io.execute(() -> {
                            repository.deleteNotification(item.getId());
                            runOnUiThread(() -> {
                                allItems.remove(item);
                                visibleItems.remove(item);
                                adapter.notifyDataSetChanged();
                                updateSummary();
                            });
                        })).show();
            }
        });
        recycler.setAdapter(adapter);
        ((ChipGroup) findViewById(R.id.chipGroupNotificationFilters)).setOnCheckedStateChangeListener((group, ids) -> {
            if (!ids.isEmpty()) {
                categoryFilter = ids.get(0);
                applyCategoryFilter();
            }
        });
    }

    @Override protected void onResume() { super.onResume(); load(); }

    private void installRefreshWrapper(int viewId){View child=findViewById(viewId);ViewGroup parent=(ViewGroup)child.getParent();int index=parent.indexOfChild(child);ViewGroup.LayoutParams old=child.getLayoutParams();parent.removeView(child);refresh=new SwipeRefreshLayout(this);refresh.setId(View.generateViewId());refresh.setColorSchemeResources(R.color.tech_cyan);refresh.setLayoutParams(old);child.setLayoutParams(new SwipeRefreshLayout.LayoutParams(-1,-1));refresh.addView(child);parent.addView(refresh,index,old);}
    private void load(){load(false);}
    private void load(boolean fromRefresh) {
        if(loading){if(refresh!=null)refresh.setRefreshing(false);return;}loading=true;
        io.execute(() -> {
            try {ArrayList<NotificationItem> result = repository.getNotifications();
            runOnUiThread(() -> {loading=false;if(refresh!=null)refresh.setRefreshing(false);
                allItems.clear();
                allItems.addAll(result);
                applyCategoryFilter();
            });} catch(RuntimeException error){runOnUiThread(()->{loading=false;if(refresh!=null)refresh.setRefreshing(false);if(fromRefresh)android.widget.Toast.makeText(this,R.string.refresh_failed,android.widget.Toast.LENGTH_SHORT).show();});}
        });
    }

    private void applyCategoryFilter() {
        visibleItems.clear();
        for (NotificationItem item : allItems) {
            if (matchesCategory(item.getCategory())) visibleItems.add(item);
        }
        if (adapter != null) adapter.notifyDataSetChanged();
        updateSummary();
    }

    private boolean matchesCategory(String category) {
        if (categoryFilter == R.id.chipNotifAll) return true;
        if (categoryFilter == R.id.chipNotifLow) return "LOW_BATTERY".equalsIgnoreCase(category);
        if (categoryFilter == R.id.chipNotifOverheat) return "OVERHEAT".equalsIgnoreCase(category);
        if (categoryFilter == R.id.chipNotifCharge) return "BATTERY_FULL".equalsIgnoreCase(category);
        return true;
    }

    private boolean handleMenu(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_mark_all_read) {
            io.execute(repository::markAllNotificationsRead);
            for (NotificationItem notification : allItems) notification.setRead(true);
            adapter.notifyDataSetChanged();
            updateSummary();
            return true;
        }
        if (id == R.id.action_clear_notifications) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.notification_clear_all_title)
                    .setMessage(R.string.notification_clear_all_message)
                    .setNegativeButton(R.string.common_cancel, null)
                    .setPositiveButton(R.string.notification_delete_button, (dialog, which) -> io.execute(() -> {
                        repository.clearNotifications();
                        runOnUiThread(() -> {
                            allItems.clear();
                            visibleItems.clear();
                            adapter.notifyDataSetChanged();
                            updateSummary();
                        });
                    })).show();
            return true;
        }
        return false;
    }

    private void updateSummary() {
        int unread = 0;
        for (NotificationItem notification : visibleItems) if (!notification.isRead()) unread++;
        boolean noVisibleItems = visibleItems.isEmpty();
        recycler.setVisibility(noVisibleItems ? View.GONE : View.VISIBLE);
        empty.setVisibility(noVisibleItems ? View.VISIBLE : View.GONE);
        if (noVisibleItems && !allItems.isEmpty()) {
            emptyTitle.setText(R.string.notifications_filtered_empty_title);
            emptyDescription.setText(R.string.notifications_filtered_empty_desc);
        } else {
            emptyTitle.setText(R.string.notifications_empty_title);
            emptyDescription.setText(R.string.notifications_empty_desc);
        }
        summary.setText(allItems.isEmpty() ? getString(R.string.notifications_empty_summary) : getString(R.string.notifications_unread_summary,visibleItems.size(),unread));
    }

    @Override protected void onDestroy() { io.shutdown(); super.onDestroy(); }
}
