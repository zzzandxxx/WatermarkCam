package com.huaming.watermark.ui;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.huaming.watermark.R;
import com.huaming.watermark.data.Prefs;
import com.huaming.watermark.util.TimeUtils;

import java.util.Calendar;

/**
 * 水印时间编辑对话框。
 *
 * 取景页点水印、设置页「水印时间」两处共用，保证行为一致：
 * 默认取拍照时刻；也可以固定成一个指定时间（补录、跨天整理等场景）。
 */
public final class TimeEditDialog {

    private TimeEditDialog() {
    }

    /**
     * @param tip     说明文字，传 null 用默认文案
     * @param onSaved 保存后的回调（此时 prefs 已写入）
     */
    public static void show(final Activity act, final Prefs prefs,
                            final String tip, final Runnable onSaved) {
        final boolean[] fixed = {prefs.timeMode() == Prefs.TIME_FIXED};
        // 自动模式下用当前时刻做起点，避免显示上一次留下的旧固定值
        final long[] ts = {(fixed[0] && prefs.fixedTime() > 0L)
                ? prefs.fixedTime() : System.currentTimeMillis()};

        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(act, 22), dp(act, 14), dp(act, 22), 0);

        final TextView tvMode = new TextView(act);
        tvMode.setTextSize(12f);
        tvMode.setTextColor(0xFF8C99AA);

        final TextView tvValue = new TextView(act);
        tvValue.setTextColor(0xFFF2F5F9);
        tvValue.setTextSize(25f);
        tvValue.setTypeface(Typeface.DEFAULT_BOLD);
        tvValue.setPadding(0, dp(act, 6), 0, dp(act, 14));

        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);

        TextView btnDate = chip(act, "选日期");
        TextView btnTime = chip(act, "选时间");
        TextView btnNow = chip(act, "此刻");
        TextView btnAuto = chip(act, "自动");
        row.addView(btnDate);
        row.addView(btnTime);
        row.addView(btnNow);
        row.addView(btnAuto);

        final Runnable sync = () -> {
            tvValue.setText(TimeUtils.displayDateTime(ts[0]));
            tvMode.setText(fixed[0]
                    ? "固定时间 · 拍照时不再走时（再点「自动」可恢复）"
                    : "自动 · 取你按下快门的真实时刻");
            tvMode.setTextColor(fixed[0] ? 0xFFFFC400 : 0xFF8C99AA);
        };
        sync.run();

        btnDate.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(ts[0]);
            new DatePickerDialog(act, (view, y, m, d) -> {
                c.set(Calendar.YEAR, y);
                c.set(Calendar.MONTH, m);
                c.set(Calendar.DAY_OF_MONTH, d);
                ts[0] = c.getTimeInMillis();
                fixed[0] = true;
                sync.run();
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH),
                    c.get(Calendar.DAY_OF_MONTH)).show();
        });

        btnTime.setOnClickListener(v -> {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(ts[0]);
            new TimePickerDialog(act, (view, h, min) -> {
                c.set(Calendar.HOUR_OF_DAY, h);
                c.set(Calendar.MINUTE, min);
                c.set(Calendar.SECOND, 0);
                ts[0] = c.getTimeInMillis();
                fixed[0] = true;
                sync.run();
            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show();
        });

        btnNow.setOnClickListener(v -> {
            ts[0] = System.currentTimeMillis();
            fixed[0] = true;
            sync.run();
        });

        btnAuto.setOnClickListener(v -> {
            fixed[0] = false;
            ts[0] = System.currentTimeMillis();
            sync.run();
        });

        box.addView(tvMode);
        box.addView(tvValue);
        box.addView(row);

        new AlertDialog.Builder(act)
                .setTitle("水印时间")
                .setMessage(tip != null ? tip
                        : "只改水印上显示的时间，照片文件名和拍照记录里的时间仍是真实拍摄时刻。")
                .setView(box)
                .setPositiveButton("保存", (d, w) -> {
                    if (fixed[0]) {
                        prefs.setTimeMode(Prefs.TIME_FIXED);
                        prefs.setFixedTime(ts[0]);
                    } else {
                        prefs.setTimeMode(Prefs.TIME_AUTO);
                    }
                    if (onSaved != null) onSaved.run();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private static TextView chip(Activity act, String text) {
        TextView t = new TextView(act);
        t.setText(text);
        t.setTextSize(12f);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(0xFFD6DEEA);
        t.setBackgroundResource(R.drawable.bg_chip);
        t.setPadding(dp(act, 12), dp(act, 8), dp(act, 12), dp(act, 8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(act, 8);
        t.setLayoutParams(lp);
        t.setClickable(true);
        t.setFocusable(true);
        return t;
    }

    private static int dp(Activity act, int v) {
        return Math.round(v * act.getResources().getDisplayMetrics().density);
    }
}
