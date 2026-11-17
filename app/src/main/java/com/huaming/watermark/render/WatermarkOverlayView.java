package com.huaming.watermark.render;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import com.huaming.watermark.model.WatermarkData;
import com.huaming.watermark.model.WatermarkStyle;
import com.huaming.watermark.util.TimeUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * 相机预览上的水印覆盖层。与成片使用同一个 WatermarkPainter，故所见即所得。
 *
 * 支持行级点击：点在标题 / 时间 / 备注 / 打卡人等某一行的矩形内，
 * 通过 {@link OnFieldTap} 回调告诉宿主要编辑哪一项；
 * 点在卡片内的行间空隙则回落为整卡点击（编辑时间）。
 */
public class WatermarkOverlayView extends View {

    /** 字段点击回调；field 取值为 WatermarkPainter.F_* 常量 */
    public interface OnFieldTap {
        void onFieldTap(String field);
    }

    private final WatermarkData data = new WatermarkData();
    private final WatermarkStyle style = new WatermarkStyle();

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean running = false;
    private boolean withSeconds = true;
    private boolean showWatermark = true;
    /** 0 = 自动（当前时刻）；1 = 固定指定时刻 */
    private int timeMode = 0;
    private long fixedTime = 0L;

    /** 上一次绘制时水印卡片占用的矩形，用于「点水印」命中判断 */
    private final RectF hitBox = new RectF();
    /** 各字段（行）占用的矩形，用于「点哪行改哪行」 */
    private final Map<String, RectF> rowBoxes = new HashMap<>();
    /** 手指按住的字段；null 表示按在卡片空隙（整卡）上 */
    private String pressedField = null;
    /** 手指按在水印上时给一点描边反馈 */
    private boolean pressing = false;
    /** 命中判定的额外容差（dp），水印偏小时更好点 */
    private static final float TOUCH_SLOP_DP = 16f;

    private OnFieldTap fieldTapListener;

    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            refreshClock();
            invalidate();
            if (running) handler.postDelayed(this, 1000L);
        }
    };

    public WatermarkOverlayView(Context context) {
        this(context, null);
    }

    public WatermarkOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        setClickable(true);
        refreshClock();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startClock();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopClock();
        super.onDetachedFromWindow();
    }

    public void startClock() {
        if (running) return;
        running = true;
        handler.post(ticker);
    }

    public void stopClock() {
        running = false;
        handler.removeCallbacks(ticker);
    }

    public WatermarkData data() {
        return data;
    }

    public WatermarkStyle style() {
        return style;
    }

    public void setWithSeconds(boolean value) {
        withSeconds = value;
        refreshClock();
        invalidate();
    }

    public boolean isWithSeconds() {
        return withSeconds;
    }

    public void setShowWatermark(boolean show) {
        showWatermark = show;
        invalidate();
    }

    public boolean isShowWatermark() {
        return showWatermark;
    }

    public void setOnFieldTap(OnFieldTap l) {
        fieldTapListener = l;
    }

    public void refreshClock() {
        long t = (timeMode == 1 && fixedTime > 0L) ? fixedTime : System.currentTimeMillis();
        data.timeText = TimeUtils.timeAt(t, withSeconds);
        data.dateText = TimeUtils.dateWeekAt(t);
    }

    /** 设置水印时间的取法：自动（当前时刻）或固定指定时刻 */
    public void setTimeMode(int mode, long fixed) {
        timeMode = mode;
        fixedTime = fixed;
        refreshClock();
        invalidate();
    }

    /** 外部改完 data / style 后调用，触发重绘 */
    public void refresh() {
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!showWatermark) {
            hitBox.setEmpty();
            rowBoxes.clear();
            return;
        }
        WatermarkPainter.draw(canvas, getWidth(), getHeight(), data, style, hitBox, rowBoxes);
        if (pressing && !hitBox.isEmpty()) {
            // 按在某一行上就只高亮那一行，反馈更精准；按在空隙则高亮整卡
            RectF target = (pressedField != null) ? rowBoxes.get(pressedField) : null;
            if (target == null || target.isEmpty()) target = hitBox;
            float d = getResources().getDisplayMetrics().density;
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2f * d);
            p.setColor(0x99FFC400);
            RectF r = new RectF(target.left - 4 * d, target.top - 4 * d,
                    target.right + 4 * d, target.bottom + 4 * d);
            canvas.drawRoundRect(r, 16 * d, 16 * d, p);
        }
    }

    /**
     * 只有落在水印卡片上才消费触摸事件；其余位置一律返回 false，事件继续往下传。
     *
     * 注意：这里不能改调 super.onTouchEvent()。本 View 是 clickable 的，
     * View 默认实现会把任何 DOWN 都吃掉并在抬手时触发 performClick，
     * 结果就是点画面任意位置都能弹出改时间的对话框。
     */
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        final float slop = TOUCH_SLOP_DP * getResources().getDisplayMetrics().density;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (!hits(e.getX(), e.getY(), slop)) return false;
                pressedField = hitField(e.getX(), e.getY(), slop);
                pressing = true;
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!pressing) return false;
                if (!hits(e.getX(), e.getY(), slop)) {
                    pressing = false;
                    pressedField = null;
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP: {
                if (!pressing) return false;
                String field = pressedField;
                pressing = false;
                pressedField = null;
                invalidate();
                if (hits(e.getX(), e.getY(), slop * 2f)) {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                    if (field != null && fieldTapListener != null) {
                        fieldTapListener.onFieldTap(field);
                    } else {
                        // 没点中具体某一行（或宿主未监听字段）→ 保留旧的整卡点击行为
                        performClick();
                    }
                }
                return true;
            }
            case MotionEvent.ACTION_CANCEL:
                if (!pressing) return false;
                pressing = false;
                pressedField = null;
                invalidate();
                return true;
            default:
                return false;
        }
    }

    /** 判断某个预览坐标是否点在水印上（含容差） */
    public boolean hits(float x, float y, float slop) {
        if (!showWatermark || hitBox.isEmpty()) return false;
        return x >= hitBox.left - slop && x <= hitBox.right + slop
                && y >= hitBox.top - slop && y <= hitBox.bottom + slop;
    }

    /** 返回 (x, y) 落在哪个字段行内；不在任何行上返回 null */
    public String hitField(float x, float y, float slop) {
        if (!showWatermark || rowBoxes.isEmpty()) return null;
        for (Map.Entry<String, RectF> e : rowBoxes.entrySet()) {
            RectF r = e.getValue();
            if (x >= r.left - slop && x <= r.right + slop
                    && y >= r.top - slop && y <= r.bottom + slop) {
                return e.getKey();
            }
        }
        return null;
    }
}
