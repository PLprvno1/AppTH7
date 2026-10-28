package com.example.th7.widget;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.CornerPathEffect;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import com.example.th7.R;

/**
 * Custom Futuristic Tech Graph View.
 * Renders cubic spline data curve with cyber gradient fill and tech grid.
 */
public class TechGraphView extends View {

    private float[] dataPoints = new float[0];
    private String[] timeLabels = new String[0];
    private String unit = "%";

    private Paint gridPaint;
    private Paint linePaint;
    private Paint fillPaint;
    private Paint pointPaint;
    private Paint pointCenterPaint;
    private Paint textPaint;

    private Path curvePath = new Path();
    private Path fillPath = new Path();

    private float animProgress = 1f;
    private ValueAnimator animator;

    public TechGraphView(Context context) {
        super(context);
        init();
    }

    public TechGraphView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TechGraphView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        float density = getResources().getDisplayMetrics().density;

        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(color(R.color.tech_card_stroke));
        gridPaint.setStrokeWidth(density * 1f);
        gridPaint.setPathEffect(new DashPathEffect(new float[]{density * 4f, density * 4f}, 0));

        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setColor(color(R.color.tech_cyan));
        linePaint.setStrokeWidth(density * 2.5f);
        linePaint.setPathEffect(new CornerPathEffect(density * 12f));

        fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setStyle(Paint.Style.FILL);

        pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pointPaint.setColor(color(R.color.tech_cyan));
        pointPaint.setStyle(Paint.Style.FILL);

        pointCenterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pointCenterPaint.setColor(color(R.color.tech_bg_card));
        pointCenterPaint.setStyle(Paint.Style.FILL);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(color(R.color.tech_text_muted));
        textPaint.setTextSize(density * 11f);
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setData(float[] values, String[] labels, String unit) {
        this.dataPoints = values;
        this.timeLabels = labels;
        this.unit = unit;
        if (!com.example.th7.data.AppSettings.animations(getContext())) { animProgress = 1f; invalidate(); return; }

        if (animator != null && animator.isRunning()) {
            animator.cancel();
        }

        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(750);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            animProgress = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (dataPoints == null || dataPoints.length == 0) return;

        float density = getResources().getDisplayMetrics().density;
        float paddingLeft = density * 24f;
        float paddingRight = density * 24f;
        float paddingTop = density * 20f;
        float paddingBottom = density * 30f;

        float width = getWidth() - paddingLeft - paddingRight;
        float height = getHeight() - paddingTop - paddingBottom;

        if (width <= 0 || height <= 0) return;

        // 1. Draw horizontal grid lines
        int gridLines = 4;
        for (int i = 0; i <= gridLines; i++) {
            float y = paddingTop + (height / gridLines) * i;
            canvas.drawLine(paddingLeft, y, paddingLeft + width, y, gridPaint);
        }

        // 2. Find min and max for scaling
        float minVal = dataPoints[0];
        float maxVal = dataPoints[0];
        for (float v : dataPoints) { minVal = Math.min(minVal, v); maxVal = Math.max(maxVal, v); }
        if (maxVal - minVal < 1f) { minVal -= 1f; maxVal += 1f; }
        float margin = (maxVal - minVal) * 0.1f; minVal -= margin; maxVal += margin;

        // Calculate point coordinates
        int count = dataPoints.length;
        float stepX = count < 2 ? 0f : width / (count - 1);
        float[] ptsX = new float[count];
        float[] ptsY = new float[count];

        for (int i = 0; i < count; i++) {
            ptsX[i] = count == 1 ? paddingLeft + width / 2f : paddingLeft + (i * stepX);
            float normalized = (dataPoints[i] - minVal) / (maxVal - minVal);
            // Apply animation progress
            float currentVal = minVal + ((dataPoints[i] - minVal) * animProgress);
            float currentNormalized = (currentVal - minVal) / (maxVal - minVal);
            ptsY[i] = paddingTop + height - (currentNormalized * height);
        }

        // 3. Construct Path
        curvePath.reset();
        fillPath.reset();

        curvePath.moveTo(ptsX[0], ptsY[0]);
        fillPath.moveTo(ptsX[0], paddingTop + height);
        fillPath.lineTo(ptsX[0], ptsY[0]);

        for (int i = 1; i < count; i++) {
            float prevX = ptsX[i - 1];
            float prevY = ptsY[i - 1];
            float currX = ptsX[i];
            float currY = ptsY[i];

            float controlX1 = prevX + (currX - prevX) / 2f;
            float controlY1 = prevY;
            float controlX2 = prevX + (currX - prevX) / 2f;
            float controlY2 = currY;

            curvePath.cubicTo(controlX1, controlY1, controlX2, controlY2, currX, currY);
            fillPath.cubicTo(controlX1, controlY1, controlX2, controlY2, currX, currY);
        }

        fillPath.lineTo(ptsX[count - 1], paddingTop + height);
        fillPath.close();

        // 4. Fill gradient under the curve
        Shader gradient = new LinearGradient(
                0, paddingTop, 0, paddingTop + height,
                new int[]{color(R.color.tech_cyan_glow), color(R.color.tech_cyan_subtle), Color.TRANSPARENT},
                null, Shader.TileMode.CLAMP
        );
        fillPaint.setShader(gradient);
        canvas.drawPath(fillPath, fillPaint);

        // 5. Draw glowing polyline
        canvas.drawPath(curvePath, linePaint);

        // 6. Draw data points and bottom labels
        int middleIndex = count / 2;
        for (int i = 0; i < count; i++) {
            // Draw outer point
            canvas.drawCircle(ptsX[i], ptsY[i], density * 4.5f, pointPaint);
            // Draw inner point dot
            canvas.drawCircle(ptsX[i], ptsY[i], density * 2f, pointCenterPaint);

            // Limit axis labels so long histories stay legible.
            if ((i == 0 || i == middleIndex || i == count - 1) && timeLabels != null && i < timeLabels.length) {
                canvas.drawText(timeLabels[i], ptsX[i], paddingTop + height + (density * 18f), textPaint);
            }
        }
    }

    private int color(int resource) { return ContextCompat.getColor(getContext(), resource); }
}

