package com.huaming.watermark.util;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;

import java.util.ArrayList;
import java.util.List;

/**
 * 拼图 / 汇报长图渲染引擎。
 *
 * 拼图：多张照片合成一张（长图竖排 / 宫格自动排布）；
 * 汇报：白底长图，含标题、时间地点、汇报说明，每张照片下方带拍摄时间说明，底部页脚。
 */
public final class CollageMaker {

    public static final int LAYOUT_LONG = 0; // 长图竖排
    public static final int LAYOUT_GRID = 1; // 宫格（自动列数）

    public static final int MODE_COLLAGE = 0; // 纯拼图
    public static final int MODE_REPORT = 1;  // 汇报长图

    private static final int OUT_W = 1080;
    private static final int MAX_H = 12000;

    private CollageMaker() {
    }

    /** 纯拼图：layout 取 LAYOUT_LONG / LAYOUT_GRID */
    public static Bitmap collage(List<Bitmap> pics, int layout) {
        if (pics == null || pics.isEmpty()) return null;
        return layout == LAYOUT_GRID ? gridCollage(pics) : longCollage(pics);
    }

    // ------------------------------------------------------------------
    // 长图竖排
    // ------------------------------------------------------------------
    private static Bitmap longCollage(List<Bitmap> pics) {
        final float gap = 14f;
        long total = Math.round(gap * (pics.size() + 1));
        for (Bitmap b : pics) {
            total += Math.round(OUT_W * b.getHeight() / (float) b.getWidth());
        }
        float f = total > MAX_H ? MAX_H / (float) total : 1f;
        int w = Math.round(OUT_W * f);
        int h = Math.round(total * f);
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawColor(Color.parseColor("#10151C"));
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        float y = gap * f;
        for (Bitmap b : pics) {
            float ph = w * b.getHeight() / (float) b.getWidth();
            c.drawBitmap(b, null, new RectF(0, y, w, y + ph), p);
            y += ph + gap * f;
        }
        return out;
    }

    // ------------------------------------------------------------------
    // 宫格（列数 = ceil(√n)，居中裁切铺满格子）
    // ------------------------------------------------------------------
    private static Bitmap gridCollage(List<Bitmap> pics) {
        final int gap = 14;
        int n = pics.size();
        int cols = (int) Math.ceil(Math.sqrt(n));
        int rows = (int) Math.ceil(n / (float) cols);
        int cell = (OUT_W - (cols + 1) * gap) / cols;
        int h = rows * cell + (rows + 1) * gap;
        Bitmap out = Bitmap.createBitmap(OUT_W, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawColor(Color.parseColor("#10151C"));
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        for (int i = 0; i < n; i++) {
            int row = i / cols;
            int col = i % cols;
            float x = gap + col * (cell + gap);
            float y = gap + row * (cell + gap);
            drawCenterCrop(c, pics.get(i), x, y, cell, cell, p);
        }
        return out;
    }

    private static void drawCenterCrop(Canvas c, Bitmap b, float x, float y, float w, float h, Paint p) {
        int bw = b.getWidth(), bh = b.getHeight();
        float s = Math.max(w / bw, h / bh);
        float sw = w / s, sh = h / s;
        Rect src = new Rect(
                Math.round((bw - sw) / 2f), Math.round((bh - sh) / 2f),
                Math.round((bw + sw) / 2f), Math.round((bh + sh) / 2f));
        c.drawBitmap(b, src, new RectF(x, y, x + w, y + h), p);
    }

    // ------------------------------------------------------------------
    // 汇报长图
    // ------------------------------------------------------------------
    /**
     * @param title    大标题（如「XX 项目日报」）
     * @param meta     元信息行（时间 · 地点 · 天气 · 打卡人）
     * @param note     汇报说明（可多行，自动换行）
     * @param captions 每张照片下的说明（拍摄时间等），可为空串
     * @param footer   页脚文字
     */
    public static Bitmap report(String title, String meta, String note,
                                List<Bitmap> pics, List<String> captions, String footer) {
        final int pad = 56;
        final float titleSize = 60;
        final float metaSize = 34;
        final float noteSize = 38;
        final float capSize = 30;
        final float footSize = 30;
        final int contentW = OUT_W - 2 * pad;
        final int picW = contentW;

        Paint titleP = paint(titleSize, true, 0xFF0F172A);
        Paint metaP = paint(metaSize, false, 0xFF64748B);
        Paint noteP = paint(noteSize, false, 0xFF334155);
        Paint capP = paint(capSize, false, 0xFF64748B);
        Paint footP = paint(footSize, false, 0xFF94A3B8);

        List<String> noteLines = wrap(note, noteP, contentW);

        // —— 先量总高 ——
        long total = Math.round(pad + titleSize * 1.5f);
        if (meta != null && !meta.isEmpty()) total += Math.round(metaSize * 1.8f);
        if (!noteLines.isEmpty()) {
            total += Math.round(noteSize * 0.9f + noteLines.size() * noteSize * 1.55f + 24);
        }
        total += 2 + 40;
        for (int i = 0; i < pics.size(); i++) {
            Bitmap b = pics.get(i);
            float ph = picW * b.getHeight() / (float) b.getWidth();
            float drawH = Math.min(ph, picW * 4 / 3f);
            total += Math.round(drawH);
            String cap = (captions != null && i < captions.size()) ? captions.get(i) : "";
            if (cap != null && !cap.isEmpty()) total += Math.round(capSize * 2.0f);
            total += 24;
        }
        total += Math.round(pad * 0.6f + footSize * 2.4f + 120);

        Bitmap out = Bitmap.createBitmap(OUT_W, (int) Math.min(total, MAX_H), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawColor(Color.WHITE);
        float y = pad;

        if (title == null || title.isEmpty()) title = "工作汇报";
        c.drawText(title, pad, y + titleSize, titleP);
        y += titleSize * 1.5f;

        if (meta != null && !meta.isEmpty()) {
            c.drawText(meta, pad, y + metaSize, metaP);
            y += metaSize * 1.8f;
        }

        if (!noteLines.isEmpty()) {
            y += noteSize * 0.9f;
            for (String ln : noteLines) {
                c.drawText(ln, pad, y + noteSize, noteP);
                y += noteSize * 1.55f;
            }
            y += 24;
        }

        c.drawRect(pad, y, OUT_W - pad, y + 2, paint(2, false, 0xFFE2E8F0));
        y += 2 + 40;

        Paint bp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        for (int i = 0; i < pics.size(); i++) {
            Bitmap b = pics.get(i);
            float ph = picW * b.getHeight() / (float) b.getWidth();
            float drawH = Math.min(ph, picW * 4 / 3f);
            Rect src;
            if (ph > drawH) {
                float sh = drawH / ph * b.getHeight();
                int off = Math.round((b.getHeight() - sh) / 2f);
                src = new Rect(0, off, b.getWidth(), Math.round(off + sh));
            } else {
                src = new Rect(0, 0, b.getWidth(), b.getHeight());
            }
            if (y + drawH > out.getHeight()) break;
            c.drawBitmap(b, src, new RectF(pad, y, pad + picW, y + drawH), bp);
            y += drawH;
            String cap = (captions != null && i < captions.size()) ? captions.get(i) : "";
            if (cap != null && !cap.isEmpty()) {
                c.drawText(cap, pad, y + capSize * 1.2f, capP);
                y += capSize * 2.0f;
            }
            y += 24;
        }

        if (footer != null && !footer.isEmpty() && y + footSize < out.getHeight()) {
            float fw = footP.measureText(footer);
            c.drawText(footer, (OUT_W - fw) / 2f, Math.min(y + footSize, out.getHeight() - pad / 2f), footP);
        }
        return out;
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------
    private static Paint paint(float size, boolean bold, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        p.setTypeface(Typeface.create(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL));
        p.setTextSize(size);
        p.setColor(color);
        return p;
    }

    private static List<String> wrap(String s, Paint p, float maxW) {
        List<String> out = new ArrayList<>();
        if (s == null || s.trim().isEmpty()) return out;
        String[] paras = s.replace("\r", "").split("\n", -1);
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
        return out;
    }
}
