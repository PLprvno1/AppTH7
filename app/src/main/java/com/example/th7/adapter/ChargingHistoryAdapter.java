package com.example.th7.adapter;

import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.th7.R;
import com.example.th7.model.ChargingSession;
import com.google.android.material.card.MaterialCardView;

import java.util.List;

public class ChargingHistoryAdapter extends RecyclerView.Adapter<ChargingHistoryAdapter.ViewHolder> {

    public interface OnItemActionCallback {
        void onItemClick(ChargingSession item, int position);
        void onContextMenuAction(ChargingSession item, int position, int menuItemId);
    }

    private final List<ChargingSession> items;
    private final OnItemActionCallback callback;
    private int selectedPosition = -1;

    public ChargingHistoryAdapter(List<ChargingSession> items, OnItemActionCallback callback) {
        this.items = items;
        this.callback = callback;
    }

    public int getSelectedPosition() {
        return selectedPosition;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_charging_session, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ChargingSession item = items.get(position);
        holder.tvChargeType.setText(chargeTypeLabel(item.getChargeType()));
        holder.tvTimeRange.setText(item.getTimeRange());
        holder.tvLevelDelta.setText(item.getStartPercent() + "% → " + item.getEndPercent() + "% (+" + (item.getEndPercent() - item.getStartPercent()) + "%)");
        holder.tvDuration.setText(item.getDuration());
        
        holder.ivStar.setVisibility(item.isStarred() ? View.VISIBLE : View.GONE);

        holder.card.setOnClickListener(v -> {
            if (callback != null) {
                callback.onItemClick(item, holder.getAdapterPosition());
            }
        });

        holder.card.setOnLongClickListener(v -> {
            selectedPosition = holder.getAdapterPosition();
            return false; // let Android context menu system handle it
        });

        holder.card.setOnCreateContextMenuListener((menu, v, menuInfo) -> {
            selectedPosition = holder.getAdapterPosition();
            menu.setHeaderTitle(R.string.history_context_menu_title);
            menu.add(0, R.id.ctx_view_detail, 0, R.string.menu_view_detail).setOnMenuItemClickListener(menuItem -> {
                if (callback != null) callback.onContextMenuAction(item, holder.getAdapterPosition(), R.id.ctx_view_detail);
                return true;
            });
            menu.add(0, R.id.ctx_star_item, 1, item.isStarred() ? R.string.menu_unstar_item : R.string.menu_star_item).setOnMenuItemClickListener(menuItem -> {
                if (callback != null) callback.onContextMenuAction(item, holder.getAdapterPosition(), R.id.ctx_star_item);
                return true;
            });
            menu.add(0, R.id.ctx_delete_item, 2, R.string.menu_delete_item).setOnMenuItemClickListener(menuItem -> {
                if (callback != null) callback.onContextMenuAction(item, holder.getAdapterPosition(), R.id.ctx_delete_item);
                return true;
            });
        });
    }

    private String chargeTypeLabel(String type) {
        if ("AC".equalsIgnoreCase(type)) return "Nguồn AC";
        if ("USB".equalsIgnoreCase(type)) return "USB";
        if ("Wireless".equalsIgnoreCase(type)) return "Sạc không dây";
        return "Nguồn điện khác";
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView card;
        ImageView ivChargeIcon, ivStar;
        TextView tvChargeType, tvTimeRange, tvLevelDelta, tvDuration;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.cardChargingSession);
            ivChargeIcon = itemView.findViewById(R.id.ivChargeIcon);
            ivStar = itemView.findViewById(R.id.ivStar);
            tvChargeType = itemView.findViewById(R.id.tvChargeType);
            tvTimeRange = itemView.findViewById(R.id.tvTimeRange);
            tvLevelDelta = itemView.findViewById(R.id.tvLevelDelta);
            tvDuration = itemView.findViewById(R.id.tvDuration);
            
        }
    }
}


