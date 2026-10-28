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
import com.example.th7.model.AdviceItem;
import com.google.android.material.card.MaterialCardView;

import java.util.List;

public class AdviceAdapter extends RecyclerView.Adapter<AdviceAdapter.ViewHolder> {

    private final List<AdviceItem> items;

    public AdviceAdapter(List<AdviceItem> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_advice_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AdviceItem item = items.get(position);
        holder.tvAdviceTitle.setText(item.getTitle());
        holder.tvAdviceContent.setText(item.getContent());
        holder.tvAdviceCategory.setText(item.getCategory());
        holder.tvAdvicePriority.setText(holder.itemView.getContext().getString(R.string.advice_priority, item.getPriority()));
        holder.ivAdviceIcon.setImageResource(item.getIconRes());

        if ("Cao".equalsIgnoreCase(item.getPriority())) {
            holder.tvAdvicePriority.setBackgroundResource(R.drawable.bg_badge_red);
            holder.tvAdvicePriority.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.tech_red));
        } else if ("Trung bình".equalsIgnoreCase(item.getPriority())) {
            holder.tvAdvicePriority.setBackgroundResource(R.drawable.bg_badge_yellow);
            holder.tvAdvicePriority.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.tech_yellow));
        } else {
            holder.tvAdvicePriority.setBackgroundResource(R.drawable.bg_badge_green);
            holder.tvAdvicePriority.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.tech_green));
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView card;
        ImageView ivAdviceIcon;
        TextView tvAdviceCategory, tvAdvicePriority, tvAdviceTitle, tvAdviceContent;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.cardAdvice);
            ivAdviceIcon = itemView.findViewById(R.id.ivAdviceIcon);
            tvAdviceCategory = itemView.findViewById(R.id.tvAdviceCategory);
            tvAdvicePriority = itemView.findViewById(R.id.tvAdvicePriority);
            tvAdviceTitle = itemView.findViewById(R.id.tvAdviceTitle);
            tvAdviceContent = itemView.findViewById(R.id.tvAdviceContent);
        }
    }
}

