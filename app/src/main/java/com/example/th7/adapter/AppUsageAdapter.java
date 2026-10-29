package com.example.th7.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.th7.R;
import com.example.th7.data.AppUsageReader;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

public final class AppUsageAdapter extends RecyclerView.Adapter<AppUsageAdapter.Holder> {
    private final List<AppUsageReader.AppUsageRecord> items;
    private final DateFormat timeFormat = DateFormat.getTimeInstance(DateFormat.SHORT);
    private final DateFormat dateTimeFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT);

    public AppUsageAdapter(List<AppUsageReader.AppUsageRecord> items) { this.items = items; }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app_usage, parent, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        AppUsageReader.AppUsageRecord item = items.get(position);
        holder.icon.setImageDrawable(item.getIcon());
        holder.name.setText(item.getLabel());
        holder.duration.setText(formatDuration(holder.itemView, item.getForegroundMs()));
        long lastActive = item.getLastActiveMs();
        holder.lastActive.setText(lastActive <= 0L ? "" : holder.itemView.getContext().getString(
                R.string.usage_last_active,
                android.text.format.DateUtils.isToday(lastActive)
                        ? timeFormat.format(new Date(lastActive)) : dateTimeFormat.format(new Date(lastActive))));
    }

    private String formatDuration(View view, long durationMs) {
        long totalSeconds = Math.max(0L, (durationMs + 999L) / 1000L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        if (hours > 0L) return view.getContext().getString(R.string.usage_duration_format, hours, minutes);
        if (minutes > 0L) return view.getContext().getString(R.string.usage_duration_minutes, minutes);
        return view.getContext().getString(R.string.usage_duration_seconds, totalSeconds);
    }

    @Override public int getItemCount() { return items.size(); }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView name, lastActive, duration;
        Holder(View view) {
            super(view);
            icon = view.findViewById(R.id.appUsageIcon);
            name = view.findViewById(R.id.appUsageName);
            lastActive = view.findViewById(R.id.appUsageLastActive);
            duration = view.findViewById(R.id.appUsageDuration);
        }
    }
}
