package com.example.th7.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import androidx.core.content.ContextCompat;
import com.example.th7.R;

import androidx.annotation.Nullable;

/**
 * Custom Futuristic Circular Battery Gauge
 * Renders tech tick marks, background ring, glowing progress arc, and center metrics.
 */
public class CircularBatteryGaugeView extends View {

    private int progress = -1; // -1 means no level was provided
    private String statusText = "";
    private String subtitleText = "";

    private Paint backgroundArcPaint;
    private Paint progressArcPaint;
    private Paint tickPaint;
    private Paint percentTextPaint;
    private Paint percentSymbolPaint;
    private Paint statusTextPaint;
    private Paint subtitleTextPaint;

    private RectF arcBounds;
    private float strokeWidth = 24f;
    private ValueAnimator animator;

    public CircularBatteryGaugeView(Context context) {
        super(context);
        init();
    }

    public CircularBatteryGaugeView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CircularBatteryGaugeView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        strokeWidth = getResources().getDisplayMetrics().density * 10f;

        backgroundArcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backgroundArcPaint.setStyle(Paint.Style.STROKE);
        backgroundArcPaint.setStrokeWidth(strokeWidth);
        backgroundArcPaint.setColor(color(R.color.tech_bg_card_secondary));
        backgroundArcPaint.setStrokeCap(Paint.Cap.ROUND);

        progressArcPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        progressArcPaint.setStyle(Paint.Style.STROKE);
        progressArcPaint.setStrokeWidth(strokeWidth);
        progressArcPaint.setColor(color(R.color.tech_cyan));
        progressArcPaint.setStrokeCap(Paint.Cap.ROUND);

        tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tickPaint.setColor(color(R.color.tech_card_stroke));
        tickPaint.setStrokeWidth(getResources().getDisplayMetrics().density * 1.5f);

        percentTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        percentTextPaint.setColor(color(R.color.tech_text_primary));
        percentTextPaint.setTextAlign(Paint.Align.CENTER);
        percentTextPaint.setFakeBoldText(true);

        percentSymbolPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        percentSymbolPaint.setColor(color(R.color.tech_cyan));
        percentSymbolPaint.setTextAlign(Paint.Align.LEFT);

        statusTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        statusTextPaint.setColor(color(R.color.tech_green));
        statusTextPaint.setTextAlign(Paint.Align.CENTER);
        statusTextPaint.setFakeBoldText(true);

        subtitleTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        subtitleTextPaint.setColor(color(R.color.tech_text_muted));
        subtitleTextPaint.setTextAlign(Paint.Align.CENTER);

        arcBounds = new RectF();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float padding = strokeWidth + (getResources().getDisplayMetrics().density * 14f);
        arcBounds.set(padding, padding, w - padding, h - padding);

        // Gradient shader for progress arc
        Shader shader = new LinearGradient(
                arcBounds.left, arcBounds.top, arcBounds.right, arcBounds.bottom,
                new int[]{color(R.color.tech_blue), color(R.color.tech_cyan), color(R.color.tech_green)},
                null, Shader.TileMode.CLAMP
        );
        progressArcPaint.setShader(shader);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        float cx = width / 2f;
        float cy = height / 2f;
        float radius = (Math.min(width, height) / 2f) - (strokeWidth / 2f) - (getResources().getDisplayMetrics().density * 6f);

        // 1. Draw sci-fi tick marks ring
        canvas.save();
        int tickCount = 48;
        float tickLength = getResources().getDisplayMetrics().density * 4f;
        for (int i = 0; i < tickCount; i++) {
            float angle = (i * 360f / tickCount);
            if (i % 4 == 0) {
                tickPaint.setColor(color(R.color.tech_cyan));
                tickPaint.setAlpha(120);
            } else {
                tickPaint.setColor(color(R.color.tech_card_stroke));
                tickPaint.setAlpha(80);
            }
            float startX = (float) (cx + (radius + tickLength) * Math.cos(Math.toRadians(angle)));
            float startY = (float) (cy + (radius + tickLength) * Math.sin(Math.toRadians(angle)));
            float stopX = (float) (cx + (radius + tickLength + 8f) * Math.cos(Math.toRadians(angle)));
            float stopY = (float) (cy + (radius + tickLength + 8f) * Math.sin(Math.toRadians(angle)));
            canvas.drawLine(startX, startY, stopX, stopY, tickPaint);
        }
        canvas.restore();

        // 2. Draw 270-degree background arc (from 135 deg to 405 deg)
        float startAngle = 135f;
        float sweepMax = 270f;
        canvas.drawArc(arcBounds, startAngle, sweepMax, false, backgroundArcPaint);

        // 3. Draw active progress arc
        float sweepAngle = progress < 0 ? 0f : (progress / 100f) * sweepMax;
        if (sweepAngle > 0) {
            canvas.drawArc(arcBounds, startAngle, sweepAngle, false, progressArcPaint);
        }

        // 4. Center Metrics
        float density = getResources().getDisplayMetrics().density;
        percentTextPaint.setTextSize(density * (progress < 0 ? 20f : 42f));
        percentSymbolPaint.setTextSize(density * 22f);
        statusTextPaint.setTextSize(density * 12f);
        subtitleTextPaint.setTextSize(density * 10f);

        String percentVal = progress < 0
                ? getContext().getString(R.string.battery_percent_unavailable_center)
                : String.valueOf(progress);
        float percentWidth = percentTextPaint.measureText(percentVal);
        float textY = cy + (density * 8f);

        // Draw percentage number and % symbol
        canvas.drawText(percentVal, progress < 0 ? cx : cx - (density * 8f), textY, percentTextPaint);
        if (progress >= 0) {
            canvas.drawText("%", cx - (density * 8f) + (percentWidth / 2f) + (density * 2f), textY - (density * 14f), percentSymbolPaint);
        }

        // Draw status label above or below
        canvas.drawText(statusText, cx, textY + (density * 24f), statusTextPaint);
        canvas.drawText(subtitleText, cx, textY + (density * 38f), subtitleTextPaint);
    }

    public void setProgress(int targetProgress, boolean animate) {
        if (targetProgress < 0) {
            if (animator != null && animator.isRunning()) animator.cancel();
            progress = -1;
            invalidate();
            return;
        }
        if (targetProgress > 100) targetProgress = 100;

        if (!animate) {
            this.progress = targetProgress;
            updateStatusText(targetProgress);
            invalidate();
            return;
        }

        if (animator != null && animator.isRunning()) {
            animator.cancel();
        }

        animator = ValueAnimator.ofInt(this.progress, targetProgress);
        animator.setDuration(900);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            progress = (int) animation.getAnimatedValue();
            updateStatusText(progress);
            invalidate();
        });
        animator.start();
    }

    private void updateStatusText(int percent) {
        if (percent > 60) {
            statusText = getContext().getString(R.string.gauge_status_high);
            statusTextPaint.setColor(color(R.color.tech_green));
        } else if (percent > 20) {
            statusText = getContext().getString(R.string.gauge_status_normal);
            statusTextPaint.setColor(color(R.color.tech_cyan));
        } else {
            statusText = getContext().getString(R.string.gauge_status_low);
            statusTextPaint.setColor(color(R.color.tech_red));
        }
    }

    private int color(int resource) { return ContextCompat.getColor(getContext(), resource); }

    public int getProgress() {
        return progress;
    }

    public void setStatusText(String text) {
        this.statusText = text;
        invalidate();
    }

    public String getStatusText() { return statusText; }

    public void setSubtitleText(String text) {
        this.subtitleText = text;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int defaultSize = (int) (getResources().getDisplayMetrics().density * 240);
        int width = resolveSize(defaultSize, widthMeasureSpec);
        int height = resolveSize(defaultSize, heightMeasureSpec);
        int size = Math.min(width, height);
        setMeasuredDimension(size, size);
    }
}

