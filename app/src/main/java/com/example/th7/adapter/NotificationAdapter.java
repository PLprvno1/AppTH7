package com.example.th7.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.th7.R;
import com.example.th7.model.NotificationItem;
import com.google.android.material.card.MaterialCardView;

import java.util.List;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {

    public interface OnNotificationClickListener {
        void onNotificationClick(NotificationItem item, int position);
        void onNotificationDelete(NotificationItem item, int position);
    }

    private final List<NotificationItem> items;
    private final OnNotificationClickListener listener;

    public NotificationAdapter(List<NotificationItem> items, OnNotificationClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notification, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        NotificationItem item = items.get(position);
        holder.tvTitle.setText(item.getTitle());
        holder.tvMessage.setText(item.getMessage());
        holder.tvTimestamp.setText(item.getTimestamp());
        holder.tvCategoryBadge.setText(categoryLabel(holder.itemView, item.getCategory()));

        // Color badge according to category
        if ("ALERT".equalsIgnoreCase(item.getCategory())) {
            holder.tvCategoryBadge.setBackgroundResource(R.drawable.bg_badge_red);
            holder.tvCategoryBadge.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.tech_red));
        } else if ("COACH".equalsIgnoreCase(item.getCategory())) {
            holder.tvCategoryBadge.setBackgroundResource(R.drawable.bg_badge_yellow);
            holder.tvCategoryBadge.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.tech_yellow));
        } else if ("LAB".equalsIgnoreCase(item.getCategory())) {
            holder.tvCategoryBadge.setBackgroundResource(R.drawable.bg_badge_sim);
            holder.tvCategoryBadge.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.tech_purple));
        } else {
            holder.tvCategoryBadge.setBackgroundResource(R.drawable.bg_badge_demo);
            holder.tvCategoryBadge.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.tech_cyan));
        }

        // Show/hide unread indicator and dim read card
        if (item.isRead()) {
            holder.viewUnreadDot.setVisibility(View.GONE);
            holder.card.setAlpha(0.65f);
        } else {
            holder.viewUnreadDot.setVisibility(View.VISIBLE);
            holder.card.setAlpha(1.0f);
        }

        holder.card.setOnClickListener(v -> {
            item.setRead(true);
            notifyItemChanged(holder.getAdapterPosition());
            if (listener != null) {
                listener.onNotificationClick(item, holder.getAdapterPosition());
            }
        });
        holder.delete.setOnClickListener(v -> {
            if (listener != null) listener.onNotificationDelete(item, holder.getBindingAdapterPosition());
        });
    }

    private String categoryLabel(View view, String category) {
        if (category == null) return view.getContext().getString(R.string.notification_category_default);
        switch (category.toUpperCase(java.util.Locale.ROOT)) {
            case "LOW_BATTERY": return view.getContext().getString(R.string.notification_category_low);
            case "OVERHEAT": return view.getContext().getString(R.string.notification_category_overheat);
            case "BATTERY_FULL": return view.getContext().getString(R.string.notification_category_full);
            case "ALERT": return view.getContext().getString(R.string.notification_category_alert);
            case "COACH": return view.getContext().getString(R.string.notification_category_coach);
            case "LAB": return view.getContext().getString(R.string.notification_category_lab);
            default: return view.getContext().getString(R.string.notification_category_default);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView card;
        View viewUnreadDot;
        TextView tvCategoryBadge, tvTimestamp, tvTitle, tvMessage;
        android.widget.ImageButton delete;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.cardNotification);
            viewUnreadDot = itemView.findViewById(R.id.viewUnreadDot);
            tvCategoryBadge = itemView.findViewById(R.id.tvCategoryBadge);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvMessage = itemView.findViewById(R.id.tvMessage);
            delete = itemView.findViewById(R.id.btnDeleteNotification);
        }
    }
}

