package com.example.th7.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.th7.R;
import com.example.th7.model.LabScenario;
import com.google.android.material.card.MaterialCardView;

import java.util.List;

public class LabScenarioAdapter extends RecyclerView.Adapter<LabScenarioAdapter.ViewHolder> {

    public interface OnScenarioSelectedListener {
        void onScenarioSelected(LabScenario scenario, int position);
    }

    private final List<LabScenario> items;
    private final OnScenarioSelectedListener listener;
    private int selectedPosition = 0;

    public LabScenarioAdapter(List<LabScenario> items, OnScenarioSelectedListener listener) {
        this.items = items;
        this.listener = listener;
    }

    public int getSelectedPosition() {
        return selectedPosition;
    }

    public void setSelectedPosition(int position) {
        int oldPos = selectedPosition;
        selectedPosition = position;
        notifyItemChanged(oldPos);
        notifyItemChanged(selectedPosition);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_lab_scenario, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        LabScenario item = items.get(position);
        holder.tvScenarioTitle.setText(item.getTitle());
        holder.tvScenarioDesc.setText(item.getDescription());
        holder.ivScenarioIcon.setImageResource(item.getIconRes());

        int rate = item.getDischargeRateMa();
        if (rate > 0) {
            holder.tvDischargeRate.setText("+" + rate + " mA");
            holder.tvDischargeRate.setBackgroundResource(R.drawable.bg_badge_green);
            holder.tvDischargeRate.setTextColor(color(holder, R.color.tech_green));
        } else if (rate < -1000) {
            holder.tvDischargeRate.setText(rate + " mA");
            holder.tvDischargeRate.setBackgroundResource(R.drawable.bg_badge_red);
            holder.tvDischargeRate.setTextColor(color(holder, R.color.tech_red));
        } else {
            holder.tvDischargeRate.setText(rate + " mA");
            holder.tvDischargeRate.setBackgroundResource(R.drawable.bg_badge_demo);
            holder.tvDischargeRate.setTextColor(color(holder, R.color.tech_cyan));
        }

        // Selected highlight
        if (position == selectedPosition) {
            holder.card.setStrokeColor(color(holder, R.color.tech_cyan));
            holder.card.setStrokeWidth((int) (holder.itemView.getResources().getDisplayMetrics().density * 2));
            holder.card.setCardBackgroundColor(color(holder, R.color.tech_bg_card_elevated));
        } else {
            holder.card.setStrokeColor(color(holder, R.color.tech_card_stroke));
            holder.card.setStrokeWidth((int) (holder.itemView.getResources().getDisplayMetrics().density * 1));
            holder.card.setCardBackgroundColor(color(holder, R.color.tech_bg_card));
        }

        holder.card.setOnClickListener(v -> {
            int prev = selectedPosition;
            selectedPosition = holder.getAdapterPosition();
            notifyItemChanged(prev);
            notifyItemChanged(selectedPosition);
            if (listener != null) {
                listener.onScenarioSelected(item, selectedPosition);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView card;
        ImageView ivScenarioIcon;
        TextView tvScenarioTitle, tvDischargeRate, tvScenarioDesc;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.cardScenario);
            ivScenarioIcon = itemView.findViewById(R.id.ivScenarioIcon);
            tvScenarioTitle = itemView.findViewById(R.id.tvScenarioTitle);
            tvDischargeRate = itemView.findViewById(R.id.tvDischargeRate);
            tvScenarioDesc = itemView.findViewById(R.id.tvScenarioDesc);
        }
    }

    private static int color(ViewHolder holder, int resource) {
        return ContextCompat.getColor(holder.itemView.getContext(), resource);
    }
}

