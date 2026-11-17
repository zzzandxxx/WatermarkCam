package com.huaming.watermark.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

/**
 * 固定宽高比的容器（默认 3:4 竖幅）。
 * 用于让预览区域与成片比例严格一致，从而水印位置所见即所得。
 *
 * 「今日水印」式旋转交互：界面保持竖屏锁定，手机横握时仅把相机画面
 * 在取景框内旋转显示（{@link #setContentRotation}）。
 * 只有 id 为 cameraWrap 的相机容器参与旋转；水印/网格覆盖层不旋转、
 * 始终以取景框坐标系绘制，保证与成片一致。
 */
public class AspectRatioLayout extends FrameLayout {

    /** height / width */
    private float ratio = 4f / 3f;
    /** 相机画面相对取景框的旋转角：0/90/180/270 */
    private int contentRotation = 0;

    public AspectRatioLayout(Context context) {
        super(context);
    }

    public AspectRatioLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AspectRatioLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setRatio(float r) {
        if (r > 0 && r != ratio) {
            ratio = r;
            requestLayout();
        }
    }

    /** 旋转框内的相机画面（横握手机时转 90/270） */
    public void setContentRotation(int rotation) {
        int r = ((rotation % 360) + 360) % 360;
        if (r != contentRotation) {
            contentRotation = r;
            requestLayout();
        }
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int wMode = MeasureSpec.getMode(widthSpec);
        int hMode = MeasureSpec.getMode(heightSpec);
        int wAvail = (wMode == MeasureSpec.UNSPECIFIED) ? 1080 : MeasureSpec.getSize(widthSpec);
        int hAvail = (hMode == MeasureSpec.UNSPECIFIED) ? 1440 : MeasureSpec.getSize(heightSpec);

        int w = wAvail;
        int h = Math.round(w * ratio);
        if (h > hAvail) {
            h = hAvail;
            w = Math.round(h / ratio);
        }
        if (w <= 0 || h <= 0) {
            w = Math.max(1, wAvail);
            h = Math.max(1, Math.round(w * ratio));
        }
        setMeasuredDimension(w, h);

        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            // 仅相机画面容器按旋转交换宽高；覆盖层始终与取景框同尺寸
            boolean swap = isCameraWrap(child)
                    && (contentRotation == 90 || contentRotation == 270);
            int cw = Math.max(1, swap ? h : w);
            int ch = Math.max(1, swap ? w : h);
            child.measure(MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(ch, MeasureSpec.EXACTLY));
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int w = r - l;
        int h = b - t;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            boolean rotated = isCameraWrap(child) && contentRotation != 0;
            if (rotated && (contentRotation == 90 || contentRotation == 270)) {
                int cw = child.getMeasuredWidth();
                int ch = child.getMeasuredHeight();
                int left = (w - cw) / 2;
                int top = (h - ch) / 2;
                child.layout(left, top, left + cw, top + ch);
                child.setRotation(contentRotation);
            } else {
                child.layout(0, 0, w, h);
                child.setRotation(0);
            }
        }
    }

    private boolean isCameraWrap(View child) {
        return child.getId() == com.huaming.watermark.R.id.cameraWrap;
    }
}
