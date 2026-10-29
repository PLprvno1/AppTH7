package com.example.th7.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.SeekBar;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.th7.R;
import com.example.th7.model.BatteryTip;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import java.util.List;

public final class BatteryTipAdapter extends RecyclerView.Adapter<BatteryTipAdapter.Holder> {
    public interface Listener {
        void onAction(BatteryTip tip);
        void onCompletionChanged(BatteryTip tip, boolean completed);
        void onBrightnessChanged(BatteryTip tip, int percent, boolean trackingStopped);
    }
    private final List<BatteryTip> items;
    private final Listener listener;
    public BatteryTipAdapter(List<BatteryTip> items, Listener listener) { this.items = items; this.listener = listener; }
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_battery_tip, parent, false));
    }
    @Override public void onBindViewHolder(@NonNull Holder h, int position) {
        BatteryTip tip = items.get(position);
        h.icon.setImageResource(tip.iconRes); h.title.setText(tip.title); h.description.setText(tip.description);
        h.status.setText(tip.status); h.done.setOnCheckedChangeListener(null); h.done.setChecked(tip.completed);
        h.done.setOnCheckedChangeListener((button, checked) -> listener.onCompletionChanged(tip, checked));
        h.action.setText(tip.actionLabel); h.action.setVisibility(tip.actionable ? View.VISIBLE : View.GONE);
        h.action.setOnClickListener(v -> listener.onAction(tip));
        boolean hasBrightness = "brightness".equals(tip.id) && tip.brightnessPercent >= 0;
        h.brightnessControls.setVisibility("brightness".equals(tip.id) ? View.VISIBLE : View.GONE);
        h.brightness.setEnabled(hasBrightness);
        h.brightnessLabel.setText(hasBrightness
                ? brightnessLabel(h, tip.brightnessPercent)
                : h.itemView.getContext().getString(R.string.tip_status_unknown));
        h.brightness.setOnSeekBarChangeListener(null);
        if (hasBrightness) h.brightness.setProgress(tip.brightnessPercent);
        h.brightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    int percent = progress;
                    h.brightnessLabel.setText(brightnessLabel(h, percent));
                    listener.onBrightnessChanged(tip, percent, false);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                listener.onBrightnessChanged(tip, seekBar.getProgress(), true);
            }
        });
        h.card.setAlpha(tip.completed ? 0.78f : 1f);
    }
    private CharSequence brightnessLabel(Holder holder, int percent) {
        return holder.itemView.getContext().getString(R.string.tip_brightness_percent, percent);
    }
    @Override public int getItemCount() { return items.size(); }
    static final class Holder extends RecyclerView.ViewHolder {
        final MaterialCardView card; final ImageView icon; final TextView title, description, status;
        final MaterialButton action; final com.google.android.material.materialswitch.MaterialSwitch done;
        final View brightnessControls; final SeekBar brightness; final TextView brightnessLabel;
        Holder(View v) { super(v); card=v.findViewById(R.id.tipCard); icon=v.findViewById(R.id.tipIcon); title=v.findViewById(R.id.tipTitle); description=v.findViewById(R.id.tipDescription); status=v.findViewById(R.id.tipStatus); action=v.findViewById(R.id.tipAction); done=v.findViewById(R.id.tipDone); brightnessControls=v.findViewById(R.id.tipBrightnessControls); brightness=v.findViewById(R.id.tipBrightnessSeekBar); brightnessLabel=v.findViewById(R.id.tipBrightnessValue); }
    }
}
