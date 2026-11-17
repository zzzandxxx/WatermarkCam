package com.huaming.watermark.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

/** 取景网格（三分线） */
public class GridOverlayView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean visible = false;

    public GridOverlayView(Context context) {
        this(context, null);
    }

    public GridOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setColor(0x59FFFFFF);
        paint.setStrokeWidth(Math.max(1f, getResources().getDisplayMetrics().density * 0.8f));
    }

    public void setGridVisible(boolean v) {
        if (visible != v) {
            visible = v;
            invalidate();
        }
    }

    public boolean isGridVisible() {
        return visible;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!visible) return;
        int w = getWidth(), h = getHeight();
        float x1 = w / 3f, x2 = w * 2f / 3f;
        float y1 = h / 3f, y2 = h * 2f / 3f;
        canvas.drawLine(x1, 0, x1, h, paint);
        canvas.drawLine(x2, 0, x2, h, paint);
        canvas.drawLine(0, y1, w, y1, paint);
        canvas.drawLine(0, y2, w, y2, paint);
    }
}
