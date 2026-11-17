package com.huaming.watermark.render;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import com.huaming.watermark.model.WatermarkData;
import com.huaming.watermark.model.WatermarkStamp;
import com.huaming.watermark.model.WatermarkStyle;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 水印绘制引擎。
 *
 * 预览层与成片共用本类，因此预览所见即所得。
 * 所有尺寸以「画面短边 / 1080」为基准等比缩放：
 * 手机无论竖拍还是横拍，水印的视觉大小保持一致，方向跟随成片方向。
 */
public final class WatermarkPainter {

    private static final float BASE_W = 1080f;
    private static final int YELLOW = 0xFFFFC400;
    private static final int BLUE = 0xFF1266F1;

    /** 行命中矩形的 key：标题 / 时间（含日期）/ 各信息行 */
    public static final String F_TITLE = "title";
    public static final String F_TIME = "time";
    public static final String F_LOCATION = "location";
    public static final String F_COORD = "coord";
    public static final String F_WEATHER = "weather";
    public static final String F_REMARK = "remark";
    public static final String F_PERSON = "person";
    public static final String F_PROJECT = "project";

    private WatermarkPainter() {}

    public static void draw(Canvas c, int W, int H, WatermarkData data, WatermarkStyle style) {
        draw(c, W, H, data, style, null, null);
    }

    public static void draw(Canvas c, int W, int H, WatermarkData data, WatermarkStyle style,
                            RectF outBox) {
        draw(c, W, H, data, style, outBox, null);
    }

    /**
     * @param outBox  非空时把水印卡片实际占用的矩形回填进去（不含通栏模板的背景渐变），
     *               供预览层做「点水印」命中判断。
     * @param outRows 非空时回填「字段 → 该行所占矩形」的映射（换行折行会自动合并），
     *               供预览层实现「点哪一行就改哪一项」。
     */
    public static void draw(Canvas c, int W, int H, WatermarkData data, WatermarkStyle style,
                            RectF outBox, Map<String, RectF> outRows) {
        if (outBox != null) outBox.setEmpty();
        if (outRows != null) outRows.clear();
        if (c == null || W <= 0 || H <= 0) return;
        final WatermarkData d = (data == null) ? new WatermarkData() : data;
        final WatermarkStyle st = (style == null) ? WatermarkStyle.of(WatermarkStyle.T_CLASSIC) : style;

        // ---------- 整幅画面的淡文字水印（画在最底层，不干扰信息块） ----------
        drawStamp(c, W, H, st.stamp);

        // 以短边为基准：横拍/竖拍时水印视觉大小一致
        final float k = (Math.min(W, H) / BASE_W) * clamp(st.scale, 0.5f, 2.0f);
        final int a = (int) (255 * clamp(st.opacity, 0.2f, 1f));

        // ---------- 尺寸 ----------
        final float margin = 44 * k;
        final float padH = 38 * k;
        final float padV = 34 * k;
        final float titleSize = 33 * k;
        final float timeSize = 74 * k;
        final float dateSize = 25 * k;
        final float infoSize = 25 * k;
        final float labelGap = 14 * k;
        /** logo 的最大高度 / 最大宽度，实际按图片真实宽高比取其中一个 */
        final float logoMaxH = 62 * k;
        final float logoMaxW = 190 * k;
        final float radius = 24 * k;
        final float gapTitle = 14 * k;
        final float gapTime = 6 * k;
        final float ruleGap = 16 * k;

        // ---------- 画笔 ----------
        Paint titleP = textPaint(titleSize, Typeface.BOLD, null);
        Paint timeP = textPaint(timeSize, Typeface.BOLD, "sans-serif-condensed");
        Paint dateP = textPaint(dateSize, Typeface.NORMAL, null);
        Paint labelP = textPaint(infoSize, Typeface.NORMAL, null);
        Paint valueP = textPaint(infoSize, Typeface.BOLD, null);

        // ---------- 内容行 ----------
        List<Row> raw = new ArrayList<>();
        if (WatermarkData.has(d.location)) raw.add(new Row("地点", d.location.trim(), F_LOCATION));
        if (WatermarkData.has(d.coord)) raw.add(new Row("坐标", d.coord.trim(), F_COORD));
        if (WatermarkData.has(d.weather)) raw.add(new Row("天气", d.weather.trim(), F_WEATHER));
        if (WatermarkData.has(d.remark)) raw.add(new Row("备注", d.remark.trim(), F_REMARK));
        if (WatermarkData.has(d.person)) raw.add(new Row("打卡人", d.person.trim(), F_PERSON));
        if (WatermarkData.has(d.project)) raw.add(new Row("单位", d.project.trim(), F_PROJECT));

        float maxLabelW = 0;
        for (Row r : raw) maxLabelW = Math.max(maxLabelW, labelP.measureText(r.label));

        float maxContentW = Math.max(W - 2 * margin - 2 * padH, 140 * k);
        float maxValueW = Math.max(maxContentW - maxLabelW - labelGap, 90 * k);

        List<Row> rows = new ArrayList<>();
        for (Row r : raw) {
            List<String> lines = wrap(r.value, valueP, maxValueW, 3);
            for (int i = 0; i < lines.size(); i++) {
                rows.add(new Row(i == 0 ? r.label : "", lines.get(i), r.field));
            }
        }

        // ---------- logo ----------
        // 真实企业 logo 多为横版（图形 + 中英文全称），必须按原图宽高比排版：
        // 先按最大高度定尺寸，超出最大宽度再等比回缩，否则会被压扁/拉伸。
        Bitmap logo = d.logo;
        boolean logoOk = logo != null && !logo.isRecycled();
        float logoW = 0, logoH = 0;
        if (logoOk) {
            float ar = logo.getWidth() / (float) Math.max(1, logo.getHeight());
            logoH = logoMaxH;
            logoW = logoH * ar;
            if (logoW > logoMaxW) {
                logoW = logoMaxW;
                logoH = logoW / ar;
            }
        }

        boolean hasTitle = WatermarkData.has(d.title);
        boolean hasTitleRow = hasTitle || logoOk;
        float titleRowH = Math.max(hasTitle ? titleSize * 1.4f : 0f, logoOk ? logoH : 0f);
        float titleRowW = logoW
                + (logoOk && hasTitle ? 16 * k : 0)
                + (hasTitle ? titleP.measureText(d.title) : 0);

        boolean hasTime = WatermarkData.has(d.timeText);
        boolean hasDate = WatermarkData.has(d.dateText);
        float timeW = hasTime ? timeP.measureText(d.timeText) : 0;
        float dateW = hasDate ? dateP.measureText(d.dateText) : 0;

        float infoW = 0;
        for (Row r : rows) {
            infoW = Math.max(infoW, maxLabelW + labelGap + valueP.measureText(r.value));
        }

        float contentW = Math.min(Math.max(Math.max(titleRowW, Math.max(timeW, dateW)), infoW), maxContentW);

        // ---------- 高度 ----------
        float contentH = 0;
        if (hasTitleRow) contentH += titleRowH;
        if (hasTitleRow && (hasTime || hasDate)) contentH += gapTitle;
        if (hasTime) contentH += timeSize * 1.18f;
        if (hasDate) {
            if (hasTime) contentH += gapTime;
            contentH += dateSize * 1.5f;
        }
        if (!rows.isEmpty()) {
            if (contentH > 0) contentH += ruleGap;
            contentH += 1.2f * k;
            contentH += ruleGap;
            for (int i = 0; i < rows.size(); i++) contentH += infoSize * 1.58f;
        }
        if (contentH <= 0) return;

        // ---------- 块位置 ----------
        float bh = contentH + 2 * padV;
        float bw = st.fullWidth ? W : Math.min(contentW + 2 * padH, W);

        int pos = st.position;
        if (pos == WatermarkStyle.P_AUTO) {
            pos = (st.template == WatermarkStyle.T_BLUE)
                    ? WatermarkStyle.P_TOP_LEFT : WatermarkStyle.P_BOTTOM_LEFT;
        }
        boolean bottom = (pos == WatermarkStyle.P_BOTTOM_LEFT || pos == WatermarkStyle.P_BOTTOM_RIGHT);
        boolean right = (pos == WatermarkStyle.P_BOTTOM_RIGHT || pos == WatermarkStyle.P_TOP_RIGHT);

        float x0 = st.fullWidth ? 0 : (right ? W - margin - bw : margin);
        float y0 = bottom ? (H - (st.fullWidth ? 0 : margin) - bh)
                          : (st.fullWidth ? 0 : margin);

        if (outBox != null) outBox.set(x0, y0, x0 + bw, y0 + bh);

        // ---------- 背景 ----------
        drawBackdrop(c, W, H, x0, y0, bw, bh, st, a, k, bottom, padV, radius, hasTitleRow, titleRowH);

        // ---------- 配色 ----------
        int cTitle, cTime, cDate, cLabel, cValue, cRule;
        switch (st.template) {
            case WatermarkStyle.T_CARD:
                cTitle = 0xFF0F172A; cTime = 0xFF0F172A; cDate = 0xFF64748B;
                cLabel = 0xFF94A3B8; cValue = 0xFF1E293B; cRule = 0xFFE2E8F0;
                break;
            case WatermarkStyle.T_GLASS:
                cTitle = YELLOW; cTime = 0xFFFFFFFF; cDate = 0xB3FFFFFF;
                cLabel = YELLOW; cValue = 0xE6FFFFFF; cRule = 0x40FFFFFF;
                break;
            case WatermarkStyle.T_BLUE:
                cTitle = 0xFFFFFFFF; cTime = 0xFF0F172A; cDate = 0xFF64748B;
                cLabel = BLUE; cValue = 0xFF1E293B; cRule = 0xFFE2E8F0;
                break;
            case WatermarkStyle.T_PLAIN:
                cTitle = YELLOW; cTime = 0xFFFFFFFF; cDate = 0xF2FFFFFF;
                cLabel = YELLOW; cValue = 0xFFFFFFFF; cRule = 0x66FFFFFF;
                break;
            case WatermarkStyle.T_SIDE:
                cTitle = YELLOW; cTime = 0xFFFFFFFF; cDate = 0xB3FFFFFF;
                cLabel = YELLOW; cValue = 0xEBFFFFFF; cRule = 0x40FFFFFF;
                break;
            case WatermarkStyle.T_ORANGE:
                cTitle = 0xFFF97316; cTime = 0xFF1E293B; cDate = 0xFF64748B;
                cLabel = 0xFFF97316; cValue = 0xFF1E293B; cRule = 0xFFE2E8F0;
                break;
            case WatermarkStyle.T_GREEN:
                cTitle = 0xFF15803D; cTime = 0xFF1E293B; cDate = 0xFF64748B;
                cLabel = 0xFF16A34A; cValue = 0xFF1E293B; cRule = 0xFFE2E8F0;
                break;
            case WatermarkStyle.T_NAVY:
                cTitle = 0xFFFFC400; cTime = 0xFFFFFFFF; cDate = 0xB3FFFFFF;
                cLabel = 0xFF8FB7FF; cValue = 0xE6FFFFFF; cRule = 0x40FFFFFF;
                break;
            case WatermarkStyle.T_RED:
                cTitle = 0xFFFF5252; cTime = 0xFFFFFFFF; cDate = 0xB3FFFFFF;
                cLabel = 0xFFFF5252; cValue = 0xEBFFFFFF; cRule = 0x40FFFFFF;
                break;
            case WatermarkStyle.T_CYAN:
                cTitle = 0xFF22D3EE; cTime = 0xFFFFFFFF; cDate = 0xB3FFFFFF;
                cLabel = 0xFF22D3EE; cValue = 0xE6FFFFFF; cRule = 0x40FFFFFF;
                break;
            case WatermarkStyle.T_GOLD:
                cTitle = 0xFFFFC400; cTime = 0xFFFFFFFF; cDate = 0xB3FFFFFF;
                cLabel = 0xFFFFC400; cValue = 0xFFFFFFFF; cRule = 0x59FFC400;
                break;
            case WatermarkStyle.T_PILL:
                cTitle = 0xFF1E293B; cTime = 0xFF0F172A; cDate = 0xFF475569;
                cLabel = 0xFF334155; cValue = 0xFF0F172A; cRule = 0xFFCBD5E1;
                break;
            case WatermarkStyle.T_OUTLINE:
                cTitle = 0xFFFFFFFF; cTime = 0xFFFFFFFF; cDate = 0xF2FFFFFF;
                cLabel = 0xFFFFFFFF; cValue = 0xFFFFFFFF; cRule = 0x66FFFFFF;
                break;
            case WatermarkStyle.T_CLASSIC:
            default:
                cTitle = YELLOW; cTime = 0xFFFFFFFF; cDate = 0xCCFFFFFF;
                cLabel = YELLOW; cValue = 0xF2FFFFFF; cRule = 0x4DFFFFFF;
                break;
        }

        titleP.setColor(applyAlpha(cTitle, a));
        timeP.setColor(applyAlpha(cTime, a));
        dateP.setColor(applyAlpha(cDate, a));
        labelP.setColor(applyAlpha(cLabel, a));
        valueP.setColor(applyAlpha(cValue, a));

        // 纯文字/描边模板：为每种字号单独生成「同字形」描边，避免字形不匹配发虚
        Paint outTitle = null, outTime = null, outDate = null, outLabel = null, outValue = null;
        if (st.template == WatermarkStyle.T_PLAIN || st.template == WatermarkStyle.T_OUTLINE) {
            boolean thick = st.template == WatermarkStyle.T_OUTLINE;
            int oc = Color.argb((int) (a * (thick ? 0.8f : 0.55f)), 0, 0, 0);
            float ow = thick ? 7.5f * k : 5.0f * k;
            outTitle = makeOutline(titleP, ow, oc);
            outTime = makeOutline(timeP, ow + k, oc);
            outDate = makeOutline(dateP, ow, oc);
            outLabel = makeOutline(labelP, ow, oc);
            outValue = makeOutline(valueP, ow, oc);
        }

        // ---------- 绘制内容 ----------
        // 通栏模板的左右内边距比卡片模板更大，避免文字贴边
        final float leftPad = st.fullWidth ? 46 * k : padH;
        final float rightEdge = st.fullWidth ? (W - leftPad) : (x0 + bw - padH);
        float cx = x0 + leftPad;
        float cy = y0 + padV;

        if (hasTitleRow) {
            float lx = cx;
            if (logoOk) {
                RectF dst = new RectF(lx, cy + (titleRowH - logoH) / 2f,
                        lx + logoW, cy + (titleRowH - logoH) / 2f + logoH);
                Paint lp = new Paint(Paint.ANTI_ALIAS_FLAG);
                lp.setFilterBitmap(true);
                lp.setAlpha(a);
                c.drawBitmap(logo, null, dst, lp);
                lx += logoW + (hasTitle ? 16 * k : 0);
            }
            if (hasTitle) {
                float base = centerBaseline(titleP, cy, titleRowH);
                text(c, d.title, lx, base, titleP, outTitle);
            }
            putRow(outRows, F_TITLE, cx, cy, rightEdge, cy + titleRowH);
            cy += titleRowH + gapTitle;
        }

        if (hasTime) {
            float rowH = timeSize * 1.18f;
            text(c, d.timeText, cx, centerBaseline(timeP, cy, rowH), timeP, outTime);
            putRow(outRows, F_TIME, cx, cy, rightEdge, cy + rowH);
            cy += rowH;
        }

        if (hasDate) {
            if (hasTime) cy += gapTime;
            float rowH = dateSize * 1.5f;
            text(c, d.dateText, cx, centerBaseline(dateP, cy, rowH), dateP, outDate);
            putRow(outRows, F_TIME, cx, cy, rightEdge, cy + rowH);
            cy += rowH;
        }

        if (!rows.isEmpty()) {
            if (cy > y0 + padV) cy += ruleGap;
            Paint ruleP = new Paint(Paint.ANTI_ALIAS_FLAG);
            ruleP.setColor(applyAlpha(cRule, a));
            ruleP.setStrokeWidth(1.2f * k);
            float ruleRight = Math.min(cx + contentW, rightEdge);
            c.drawLine(cx, cy, ruleRight, cy, ruleP);
            cy += ruleGap;

            for (Row r : rows) {
                float rowH = infoSize * 1.58f;
                float base = centerBaseline(valueP, cy, rowH);
                if (r.label != null && r.label.length() > 0) {
                    text(c, r.label, cx, base, labelP, outLabel);
                }
                text(c, r.value, cx + maxLabelW + labelGap, base, valueP, outValue);
                putRow(outRows, r.field, cx, cy, rightEdge, cy + rowH);
                cy += rowH;
            }
        }
    }

    /** 记录/合并某一字段占用的矩形（折行时多次 union） */
    private static void putRow(Map<String, RectF> outRows, String field,
                               float l, float t, float r, float b) {
        if (outRows == null || field == null) return;
        RectF have = outRows.get(field);
        if (have == null) {
            outRows.put(field, new RectF(l, t, r, b));
        } else {
            have.union(l, t, r, b);
        }
    }

    // ==================================================================
    // 淡文字水印
    // ==================================================================
    /**
     * 铺在整幅画面上的浅色文字。
     *
     * 做法：把画布按指定角度旋转，然后在对角线范围内按网格重复绘制文字。
     * 相邻行左右错开半格，避免出现呆板的棋盘感。
     */
    private static void drawStamp(Canvas c, int W, int H, WatermarkStamp s) {
        if (s == null || !s.enabled) return;
        String txt = s.text == null ? "" : s.text.trim();
        if (txt.isEmpty()) return;

        String[] lines = txt.split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) lines[i] = lines[i].trim();

        float size = W * clamp(s.sizeRatio, 0.008f, 0.12f);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        p.setTextSize(size);
        int alpha = (int) (255 * clamp(s.opacity, 0.01f, 1f));
        p.setColor(Color.argb(alpha, Color.red(s.color), Color.green(s.color), Color.blue(s.color)));

        Paint.FontMetrics fm = p.getFontMetrics();
        float lineH = size * 1.30f;
        float maxW = 0;
        for (String ln : lines) maxW = Math.max(maxW, p.measureText(ln));

        c.save();
        c.rotate(s.angle, W / 2f, H / 2f);

        if (s.tiled) {
            double diag = Math.hypot(W, H);
            float left = W / 2f - (float) diag / 2f;
            float top = H / 2f - (float) diag / 2f;
            float colW = maxW * 1.75f;
            float rowH = lineH * 2.6f;
            int row = 0;
            for (float y = top; y < top + (float) diag + rowH; y += rowH, row++) {
                float offsetX = (row % 2 == 0) ? 0 : colW / 2f;
                for (float x = left + offsetX; x < left + (float) diag + colW; x += colW) {
                    drawStampLines(c, lines, p, fm, x, y, lineH);
                }
            }
        } else {
            // 单条模式：文字过长时自动收窄，避免两端被画面切掉
            float limit = W * 0.86f;
            if (maxW > limit && maxW > 0) {
                float k = limit / maxW;
                p.setTextSize(size * k);
                fm = p.getFontMetrics();
                lineH = size * k * 1.30f;
                maxW = 0;
                for (String ln : lines) maxW = Math.max(maxW, p.measureText(ln));
            }
            float totalH = lines.length * lineH;
            float firstBase = H / 2f - totalH / 2f - fm.ascent;
            drawStampLines(c, lines, p, fm, W / 2f - maxW / 2f, firstBase, lineH);
        }
        c.restore();
    }

    /** 以 firstBaseline 为第一行基线，逐行绘制 */
    private static void drawStampLines(Canvas c, String[] lines, Paint p, Paint.FontMetrics fm,
                                       float x, float firstBaseline, float lineH) {
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].isEmpty()) continue;
            c.drawText(lines[i], x, firstBaseline + i * lineH, p);
        }
    }

    // ==================================================================
    // 背景
    // ==================================================================
    private static void drawBackdrop(Canvas c, int W, int H, float x0, float y0, float bw, float bh,
                                     WatermarkStyle st, int a, float k, boolean bottom,
                                     float padV, float radius, boolean hasTitleRow, float titleRowH) {
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        RectF box = new RectF(x0, y0, x0 + bw, y0 + bh);

        switch (st.template) {
            case WatermarkStyle.T_CLASSIC: {
                int c1 = Color.argb(0, 0, 0, 0);
                int c2 = Color.argb((int) (a * 0.58f), 0, 0, 0);
                int c3 = Color.argb((int) (a * 0.88f), 0, 0, 0);
                float top = bottom ? Math.min(y0 - 40 * k, H - bh - 40 * k) : y0;
                top = Math.max(0, top);
                bg.setShader(new LinearGradient(0, top, 0, H,
                        new int[]{c1, c2, c3}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
                c.drawRect(0, top, W, H, bg);
                // 顶部黄线
                Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
                line.setColor(applyAlpha(YELLOW, a));
                c.drawRect(x0, y0, x0 + bw, y0 + 2.6f * k, line);
                break;
            }
            case WatermarkStyle.T_CARD: {
                bg.setColor(Color.argb((int) (a * 0.95f), 255, 255, 255));
                c.drawRoundRect(box, radius, radius, bg);
                Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
                bar.setColor(applyAlpha(YELLOW, a));
                float inset = 7 * k;
                RectF r = new RectF(x0 + inset, y0 + padV, x0 + inset + 4.5f * k, y0 + bh - padV);
                c.drawRoundRect(r, 2.5f * k, 2.5f * k, bar);
                break;
            }
            case WatermarkStyle.T_GLASS: {
                bg.setColor(Color.argb((int) (a * 0.66f), 12, 16, 22));
                c.drawRoundRect(box, radius, radius, bg);
                Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
                stroke.setStyle(Paint.Style.STROKE);
                stroke.setStrokeWidth(1.4f * k);
                stroke.setColor(Color.argb((int) (a * 0.20f), 255, 255, 255));
                c.drawRoundRect(box, radius, radius, stroke);
                break;
            }
            case WatermarkStyle.T_BLUE: {
                bg.setColor(Color.argb((int) (a * 0.94f), 255, 255, 255));
                c.drawRoundRect(box, radius, radius, bg);
                float barH = hasTitleRow ? (padV + titleRowH + padV * 0.3f)
                                         : Math.max(padV * 1.15f, 14 * k);
                Path p = new Path();
                p.addRoundRect(new RectF(x0, y0, x0 + bw, y0 + barH),
                        new float[]{radius, radius, radius, radius, 0, 0, 0, 0}, Path.Direction.CW);
                c.save();
                c.clipPath(p);
                Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
                bar.setColor(Color.argb((int) (a * 0.97f), 18, 102, 241));
                c.drawRect(x0, y0, x0 + bw, y0 + barH, bar);
                c.restore();
                break;
            }
            case WatermarkStyle.T_PLAIN:
                break;
            case WatermarkStyle.T_SIDE: {
                bg.setColor(Color.argb((int) (a * 0.72f), 10, 14, 20));
                c.drawRect(box, bg);
                Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
                bar.setColor(applyAlpha(YELLOW, a));
                c.drawRect(x0, y0, x0 + 5.0f * k, y0 + bh, bar);
                break;
            }
            case WatermarkStyle.T_ORANGE: {
                bg.setColor(Color.argb((int) (a * 0.95f), 255, 255, 255));
                c.drawRoundRect(box, radius, radius, bg);
                Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
                bar.setColor(applyAlpha(0xFFF97316, a));
                float inset = 7 * k;
                RectF r = new RectF(x0 + inset, y0 + padV, x0 + inset + 4.5f * k, y0 + bh - padV);
                c.drawRoundRect(r, 2.5f * k, 2.5f * k, bar);
                break;
            }
            case WatermarkStyle.T_GREEN: {
                bg.setColor(Color.argb((int) (a * 0.94f), 255, 255, 255));
                c.drawRoundRect(box, radius, radius, bg);
                float barH = hasTitleRow ? (padV + titleRowH + padV * 0.3f)
                                         : Math.max(padV * 1.15f, 14 * k);
                Path p = new Path();
                p.addRoundRect(new RectF(x0, y0, x0 + bw, y0 + barH),
                        new float[]{radius, radius, radius, radius, 0, 0, 0, 0}, Path.Direction.CW);
                c.save();
                c.clipPath(p);
                Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
                bar.setColor(Color.argb((int) (a * 0.97f), 22, 163, 74));
                c.drawRect(x0, y0, x0 + bw, y0 + barH, bar);
                c.restore();
                break;
            }
            case WatermarkStyle.T_NAVY: {
                bg.setColor(Color.argb((int) (a * 0.93f), 11, 37, 69));
                c.drawRoundRect(box, radius, radius, bg);
                Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
                stroke.setStyle(Paint.Style.STROKE);
                stroke.setStrokeWidth(1.4f * k);
                stroke.setColor(Color.argb((int) (a * 0.30f), 143, 183, 255));
                c.drawRoundRect(box, radius, radius, stroke);
                break;
            }
            case WatermarkStyle.T_RED: {
                bg.setColor(Color.argb((int) (a * 0.72f), 10, 14, 20));
                c.drawRect(box, bg);
                Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
                bar.setColor(applyAlpha(0xFFFF5252, a));
                c.drawRect(x0, y0, x0 + 5.0f * k, y0 + bh, bar);
                break;
            }
            case WatermarkStyle.T_CYAN: {
                bg.setColor(Color.argb((int) (a * 0.66f), 6, 18, 26));
                c.drawRoundRect(box, radius, radius, bg);
                Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
                stroke.setStyle(Paint.Style.STROKE);
                stroke.setStrokeWidth(1.6f * k);
                stroke.setColor(Color.argb((int) (a * 0.55f), 34, 211, 238));
                c.drawRoundRect(box, radius, radius, stroke);
                break;
            }
            case WatermarkStyle.T_GOLD: {
                bg.setColor(Color.argb((int) (a * 0.80f), 8, 10, 14));
                c.drawRoundRect(box, radius, radius, bg);
                Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
                stroke.setStyle(Paint.Style.STROKE);
                stroke.setStrokeWidth(1.6f * k);
                stroke.setColor(Color.argb((int) (a * 0.62f), 255, 196, 0));
                c.drawRoundRect(box, radius, radius, stroke);
                break;
            }
            case WatermarkStyle.T_PILL: {
                // 圆角上限 30k：内容较矮时不至于吞掉文字（比普通卡片更圆润但安全）
                float pr = Math.min(bh / 2f, 30f * k);
                bg.setColor(Color.argb((int) (a * 0.92f), 255, 255, 255));
                c.drawRoundRect(box, pr, pr, bg);
                break;
            }
            case WatermarkStyle.T_OUTLINE:
                break;
            default:
                break;
        }
    }

    // ==================================================================
    // 工具
    // ==================================================================
    private static void text(Canvas c, String s, float x, float y, Paint fill, Paint outline) {
        if (s == null || s.length() == 0) return;
        if (outline != null) c.drawText(s, x, y, outline);
        c.drawText(s, x, y, fill);
    }

    private static Paint makeOutline(Paint src, float strokeW, int color) {
        Paint p = new Paint(src);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(strokeW);
        p.setStrokeJoin(Paint.Join.ROUND);
        p.setColor(color);
        return p;
    }

    private static Paint textPaint(float size, int style, String family) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        Typeface base = (family == null) ? Typeface.DEFAULT : Typeface.create(family, Typeface.NORMAL);
        p.setTypeface(Typeface.create(base, style));
        p.setTextSize(size);
        return p;
    }

    private static float centerBaseline(Paint p, float top, float rowH) {
        Paint.FontMetrics fm = p.getFontMetrics();
        return top + (rowH - (fm.descent - fm.ascent)) / 2f - fm.ascent;
    }

    private static int applyAlpha(int color, int a) {
        int ca = Color.alpha(color);
        return Color.argb(ca * a / 255, Color.red(color), Color.green(color), Color.blue(color));
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static List<String> wrap(String s, Paint p, float maxW, int maxLines) {
        List<String> out = new ArrayList<>();
        if (s == null || s.isEmpty()) return out;
        if (maxW <= 1f) {
            out.add(s);
            return out;
        }
        String[] paras = s.split("\n", -1);
        for (String para : paras) {
            if (para.isEmpty()) {
                out.add("");
                continue;
            }
            int start = 0;
            while (start < para.length()) {
                int end = para.length();
                while (end > start + 1 && p.measureText(para, start, end) > maxW) {
                    end--;
                }
                out.add(para.substring(start, end));
                start = end;
            }
        }
        if (out.size() > maxLines) {
            List<String> cut = new ArrayList<>(out.subList(0, maxLines));
            cut.set(maxLines - 1, cut.get(maxLines - 1) + "…");
            return cut;
        }
        return out;
    }

    private static final class Row {
        final String label;
        final String value;
        final String field;

        Row(String label, String value, String field) {
            this.label = label;
            this.value = value;
            this.field = field;
        }
    }
}
