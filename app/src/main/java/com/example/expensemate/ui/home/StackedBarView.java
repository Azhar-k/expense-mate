package com.example.expensemate.ui.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/** One rounded bar split into shares, with small gaps between the segments. */
public class StackedBarView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float gap = 2 * getResources().getDisplayMetrics().density;
    private double[] values = new double[0];
    private int[] colors = new int[0];

    public StackedBarView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public void setSegments(double[] values, int[] colors) {
        this.values = values;
        this.colors = colors;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        double total = 0;
        for (double value : values) {
            total += value;
        }
        if (total <= 0) {
            return;
        }
        float radius = getHeight() / 2f;
        float available = getWidth() - gap * (values.length - 1);
        float left = 0;
        for (int i = 0; i < values.length; i++) {
            float width = (float) (available * values[i] / total);
            paint.setColor(colors[i]);
            rect.set(left, 0, left + width, getHeight());
            canvas.drawRoundRect(rect, radius, radius, paint);
            left += width + gap;
        }
    }
}
