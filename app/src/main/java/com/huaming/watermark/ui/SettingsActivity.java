package com.huaming.watermark.ui;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.huaming.watermark.R;
import com.huaming.watermark.data.Prefs;
import com.huaming.watermark.util.TimeUtils;

public class SettingsActivity extends AppCompatActivity {

    private interface BoolCb {
        void on(boolean value);
    }

    private interface IntCb {
        void on(int value);
    }

    private Prefs prefs;
    private LinearLayout container;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = new Prefs(this);
        container = findViewById(R.id.container);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        build();
    }

    private void build() {
        container.removeAllViews();

        addSection("水印内容");
        addValueRow("水印时间", timeLabel(), this::editWatermarkTime);
        addSwitch("时间显示到秒", "关闭则只显示到分钟", prefs.seconds(), prefs::setSeconds);
        addSwitch("显示地点", "自动解析的中文地址", prefs.showLocation(), prefs::setShowLocation);
        addSwitch("显示经纬度", "GPS 原始坐标（WGS84）", prefs.showCoord(), prefs::setShowCoord);
        addSwitch("显示天气", "需同时开启「在线天气」并有网络", prefs.showWeather(), prefs::setShowWeather);
        addSwitch("显示备注", null, prefs.showRemark(), prefs::setShowRemark);
        addSwitch("显示打卡人", null, prefs.showPerson(), prefs::setShowPerson);
        addSwitch("显示单位 / 项目", null, prefs.showProject(), prefs::setShowProject);

        addSection("照片淡文字水印");
        addSwitch("启用淡文字", "在整张照片上平铺一层浅色大字，用于「内部资料」这类标记",
                prefs.stampOn(), prefs::setStampOn);
        addValueRow("文字内容", stampTextLabel(), this::editStampText);
        addValueRow("不透明度", Math.round(prefs.stampOpacity() * 100f) + "%", this::pickStampOpacity);
        addValueRow("字号", Math.round(prefs.stampSize() * 1000f) / 10f + "% 画面宽", this::pickStampSize);
        addValueRow("倾斜角度", Math.round(prefs.stampAngle()) + "°", this::pickStampAngle);
        addValueRow("排布", prefs.stampTiled() ? "平铺满屏" : "单条居中", this::pickStampLayout);
        addValueRow("颜色", prefs.stampColor() == 0xFFFFFFFF ? "白色" : "黑色", this::pickStampColor);

        addSection("拍照");
        addSwitch("取景网格", "三分线辅助构图，不会出现在照片里", prefs.grid(), prefs::setGrid);
        addSwitch("前置自拍镜像", "前置拍摄后水平翻转，与人眼所见一致",
                prefs.frontMirror(), prefs::setFrontMirror);
        addValueRow("照片长边", maxSideLabel(prefs.maxSide()), this::chooseMaxSide);
        addSwitch("同时保存原图", "不带水印的原始照片也存一份到相册",
                prefs.saveOriginal(), prefs::setSaveOriginal);

        addSection("定位与网络");
        addSwitch("在线天气", "拍照时自动取当地天气（wttr.in，可能较慢）",
                prefs.weatherOnline(), prefs::setWeatherOnline);
        addValueRow("高德 Web 服务 Key",
                prefs.customAmapKey().isEmpty() ? "内置（可用）" : "自定义", this::editAmapKey);

        addSection("关于");
        addValueRow("版本", versionName(), null);
        addActionRow("照片存放位置", () -> new AlertDialog.Builder(this)
                .setTitle("照片存放位置")
                .setMessage("相册 → Pictures → 水印打卡\n\n"
                        + "照片通过系统相册接口写入，卸载本应用不会删除已保存的照片。")
                .setPositiveButton("知道了", null)
                .show());
        addActionRow("隐私与数据说明", this::showPrivacy);
    }

    // ============================================================
    // 行构建
    // ============================================================
    private void addSection(String title) {
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(0xFFFFC400);
        t.setTextSize(12f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLetterSpacing(0.08f);
        t.setPadding(dp(4), dp(20), dp(4), dp(9));
        container.addView(t);
    }

    private LinearLayout newRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.bg_card_row);
        row.setPadding(dp(14), dp(12), dp(10), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(7);
        row.setLayoutParams(lp);
        return row;
    }

    private TextView titleView(String title) {
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(0xFFF2F5F9);
        t.setTextSize(14f);
        return t;
    }

    private void addSwitch(String title, String desc, boolean value, final BoolCb cb) {
        LinearLayout row = newRow();

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        col.addView(titleView(title));

        if (desc != null && desc.length() > 0) {
            TextView t2 = new TextView(this);
            t2.setText(desc);
            t2.setTextColor(0xFF5F6B7C);
            t2.setTextSize(11f);
            t2.setPadding(0, dp(3), 0, 0);
            col.addView(t2);
        }
        row.addView(col);

        SwitchCompat sw = new SwitchCompat(this);
        sw.setChecked(value);
        sw.setOnCheckedChangeListener((btn, checked) -> cb.on(checked));
        row.addView(sw);

        container.addView(row);
    }

    private void addValueRow(String title, String value, final Runnable action) {
        LinearLayout row = newRow();

        TextView t1 = titleView(title);
        t1.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(t1);

        TextView tv = new TextView(this);
        tv.setText(value);
        tv.setTextColor(0xFF98A4B5);
        tv.setTextSize(12.5f);
        row.addView(tv);

        if (action != null) {
            ImageView arrow = new ImageView(this);
            arrow.setImageResource(R.drawable.ic_chevron_right);
            arrow.setColorFilter(0xFF5F6B7C);
            LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(15), dp(15));
            alp.leftMargin = dp(5);
            arrow.setLayoutParams(alp);
            row.addView(arrow);
            row.setClickable(true);
            row.setFocusable(true);
            row.setOnClickListener(v -> action.run());
        } else {
            row.setClickable(false);
        }
        container.addView(row);
    }

    private void addActionRow(String title, final Runnable action) {
        LinearLayout row = newRow();
        TextView t1 = titleView(title);
        t1.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(t1);

        ImageView arrow = new ImageView(this);
        arrow.setImageResource(R.drawable.ic_chevron_right);
        arrow.setColorFilter(0xFF5F6B7C);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(dp(15), dp(15));
        arrow.setLayoutParams(alp);
        row.addView(arrow);

        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(v -> action.run());
        container.addView(row);
    }

    // ============================================================
    // 具体操作
    // ============================================================
    private String maxSideLabel(int v) {
        switch (v) {
            case 1920: return "1920 px · 省空间";
            case 3200: return "3200 px";
            case 4000: return "4000 px · 最清晰";
            default: return v + " px";
        }
    }

    private void chooseMaxSide() {
        final int[] values = {1920, 2560, 3200, 4000};
        final String[] labels = {
                "1920 px · 单张约 1 MB，适合微信发送",
                "2560 px · 推荐，兼顾清晰与体积",
                "3200 px",
                "4000 px · 最清晰，单张约 4 MB"
        };
        int idx = 1;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == prefs.maxSide()) idx = i;
        }
        new AlertDialog.Builder(this)
                .setTitle("照片长边")
                .setSingleChoiceItems(labels, idx, (d, which) -> {
                    prefs.setMaxSide(values[which]);
                    d.dismiss();
                    build();
                })
                .show();
    }

    private void editAmapKey() {
        final EditText et = new EditText(this);
        et.setText(prefs.customAmapKey());
        et.setSingleLine(true);
        et.setHint("留空 = 使用内置 Key");
        et.setTextColor(0xFFF2F5F9);
        et.setHintTextColor(0xFF5F6B7C);

        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(dp(20), dp(6), dp(20), 0);
        wrap.addView(et, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
                .setTitle("高德 Web 服务 Key")
                .setMessage("本应用已内置一个 Web 服务 Key，装好即可按单位名精确搜索，无需配置。\n\n"
                        + "如果内置 Key 的日额度（5000 次）被别人用完了，可以换成你自己的："
                        + "到 lbs.amap.com 创建 Key，服务平台选「Web服务」，粘贴到这里。\n\n"
                        + "留空则回到内置 Key。")
                .setView(wrap)
                .setPositiveButton("保存", (d, w) -> {
                    prefs.setAmapKey(et.getText().toString().trim());
                    build();
                    Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .setNeutralButton("用内置", (d, w) -> {
                    prefs.setAmapKey("");
                    build();
                    Toast.makeText(this, "已切回内置 Key", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "";
        }
    }

    // ============================================================
    // 水印时间：自动 / 固定指定
    // ============================================================
    private String timeLabel() {
        if (prefs.timeMode() == Prefs.TIME_FIXED && prefs.fixedTime() > 0L) {
            return "固定 " + TimeUtils.displayDateTime(prefs.fixedTime());
        }
        return "自动（拍照时刻）";
    }

    private void editWatermarkTime() {
        TimeEditDialog.show(this, prefs,
                "默认取拍照时刻。可固定成一个指定时间，适合补录、跨天整理或演示场景。\n\n"
                        + "取景页上直接点水印，也能改这里。",
                () -> {
                    build();
                    Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
                });
    }

    private TextView chip(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(12f);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(0xFFD6DEEA);
        t.setBackgroundResource(R.drawable.bg_chip);
        t.setPadding(dp(13), dp(7), dp(13), dp(7));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(8);
        t.setLayoutParams(lp);
        t.setClickable(true);
        t.setFocusable(true);
        return t;
    }

    // ============================================================
    // 照片淡文字水印
    // ============================================================
    private String stampTextLabel() {
        String t = prefs.stampText();
        if (t == null || t.trim().isEmpty()) return "未设置";
        String one = t.replace("\n", " ").trim();
        return one.length() > 14 ? one.substring(0, 14) + "…" : one;
    }

    private void editStampText() {
        final EditText et = new EditText(this);
        et.setText(prefs.stampText());
        et.setHint("例如：华明机电 · 内部资料 · 禁止外传");
        et.setGravity(Gravity.TOP | Gravity.START);
        et.setMinLines(2);
        et.setMaxLines(4);
        et.setTextColor(0xFFF2F5F9);
        et.setHintTextColor(0xFF5F6B7C);
        et.setTextSize(14f);

        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(dp(20), dp(6), dp(20), 0);
        wrap.addView(et, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(88)));

        new AlertDialog.Builder(this)
                .setTitle("淡文字内容")
                .setMessage("铺在照片上的浅色大字。可以写公司名、内部编号、警示语，\n支持换行（每行都会平铺）。")
                .setView(wrap)
                .setPositiveButton("保存", (d, w) -> {
                    prefs.setStampText(et.getText().toString().trim());
                    build();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void pickStampOpacity() {
        sliderDialog("淡文字不透明度", 2, 40, Math.round(prefs.stampOpacity() * 100f), "%",
                v -> {
                    prefs.setStampOpacity(v / 100f);
                    build();
                });
    }

    private void pickStampSize() {
        sliderDialog("淡文字字号", 10, 100, Math.round(prefs.stampSize() * 1000f), " ‰画面宽",
                v -> {
                    prefs.setStampSize(v / 1000f);
                    build();
                });
    }

    private void pickStampAngle() {
        sliderDialog("淡文字倾斜角度", -90, 90, Math.round(prefs.stampAngle()), "°",
                v -> {
                    prefs.setStampAngle(v);
                    build();
                });
    }

    private void pickStampLayout() {
        String[] labels = {"平铺满屏（防转发常用）", "单条居中（像一条签名）"};
        int idx = prefs.stampTiled() ? 0 : 1;
        new AlertDialog.Builder(this)
                .setTitle("排布方式")
                .setSingleChoiceItems(labels, idx, (d, which) -> {
                    prefs.setStampTiled(which == 0);
                    d.dismiss();
                    build();
                })
                .show();
    }

    private void pickStampColor() {
        String[] labels = {"白色（照片偏暗时用）", "黑色（照片偏亮时用）"};
        int idx = prefs.stampColor() == 0xFFFFFFFF ? 0 : 1;
        new AlertDialog.Builder(this)
                .setTitle("文字颜色")
                .setSingleChoiceItems(labels, idx, (d, which) -> {
                    prefs.setStampColor(which == 0 ? 0xFFFFFFFF : 0xFF000000);
                    d.dismiss();
                    build();
                })
                .show();
    }

    /** 通用滑杆对话框 */
    private void sliderDialog(String title, final int min, final int max, int value,
                              final String unit, final IntCb cb) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(14), dp(20), 0);

        final TextView tv = new TextView(this);
        tv.setTextColor(0xFFF2F5F9);
        tv.setTextSize(16f);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setText(value + unit);

        final SeekBar sb = new SeekBar(this);
        sb.setMax(max - min);
        sb.setProgress(Math.max(0, Math.min(max - min, value - min)));
        sb.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFFFFC400));
        sb.setThumbTintList(android.content.res.ColorStateList.valueOf(0xFFFFC400));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                tv.setText((min + p) + unit);
            }

            @Override
            public void onStartTrackingTouch(SeekBar sb) { }

            @Override
            public void onStopTrackingTouch(SeekBar sb) { }
        });

        box.addView(tv);
        box.addView(sb);

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(box)
                .setPositiveButton("确定", (d, w) -> cb.on(min + sb.getProgress()))
                .setNegativeButton("取消", null)
                .show();
    }

    private void showPrivacy() {
        new AlertDialog.Builder(this)
                .setTitle("隐私与数据说明")
                .setMessage("· 所有照片、打卡记录、自定义 Logo 都只保存在这台手机上，不上传任何服务器。\n\n"
                        + "· 相机画面仅用于取景与拍照，不做采集。\n\n"
                        + "· 定位只用于生成水印上的地点和经纬度；坐标会写入照片本身。\n\n"
                        + "· 开启「在线天气」时会向 wttr.in 发送当前经纬度；填写高德 Key 后地址解析会请求高德服务器。两者都可关闭。\n\n"
                        + "· 照片通过系统相册接口写入，卸载本应用不会删除已保存的照片。")
                .setPositiveButton("知道了", null)
                .show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
