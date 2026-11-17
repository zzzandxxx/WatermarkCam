package com.huaming.watermark.util;

import android.graphics.Bitmap;
import android.graphics.Color;

/**
 * Logo 预处理：把「官网存下来的带白底截图」变成「水印上能用的透明图」。
 *
 * 处理顺序：
 * 1. 去背景 —— 从四周边框取色判断底色，再从边缘做洪水填充把相连的底色变透明，
 *    只清「和边缘连通」的区域，所以 logo 内部同样颜色的部分不会被误伤；
 * 2. 边缘羽化 —— 抗锯齿边缘按「与底色的色差」给半透明，避免放大后出现白边；
 * 3. 裁空白边 —— 去掉四周多余留白，让 logo 占满画布，水印里才够大；
 * 4. 限制尺寸 —— 最长边不超过 maxSide，控制内存。
 *
 * 全程不改宽高比：横版 logo（如「中国电信 CHINA TELECOM」这种长条）会被原样保留，
 * 交给水印引擎按实际比例排版。
 */
public final class LogoProcessor {

    /** 底色色差容差（RGB 欧氏距离），JPEG 压缩的灰边也能吃掉 */
    private static final int TOL = 46;
    /** 边框里同色像素占比超过这个值，才认定这是一个纯色底 */
    private static final float UNIFORM_RATIO = 0.80f;

    public static final class Options {
        /** 去掉纯色/白色底 */
        public boolean removeBg = true;
        /** 裁掉四周空白边 */
        public boolean trim = true;
        /** 最长边像素 */
        public int maxSide = 512;

        public Options() {}

        public Options(boolean removeBg, boolean trim) {
            this.removeBg = removeBg;
            this.trim = trim;
        }
    }

    private LogoProcessor() {}

    /** 便捷入口：默认去底 + 裁边 */
    public static Bitmap process(Bitmap src) {
        return process(src, new Options());
    }

    public static Bitmap process(Bitmap src, Options opt) {
        if (src == null || src.isRecycled()) return null;
        Options o = (opt == null) ? new Options() : opt;

        Bitmap cur = src;
        if (o.removeBg) {
            Bitmap cut = removeBackground(cur);
            if (cut != null && cut != cur) cur = cut;
        }
        if (o.trim) {
            Bitmap cropped = trimMargins(cur);
            if (cropped != null && cropped != cur) cur = cropped;
        }
        if (o.maxSide > 0 && Math.max(cur.getWidth(), cur.getHeight()) > o.maxSide) {
            float f = o.maxSide / (float) Math.max(cur.getWidth(), cur.getHeight());
            int w = Math.max(1, Math.round(cur.getWidth() * f));
            int h = Math.max(1, Math.round(cur.getHeight() * f));
            Bitmap scaled = Bitmap.createScaledBitmap(cur, w, h, true);
            cur = scaled;
        }
        return cur;
    }

    // ==================================================================
    // 去背景
    // ==================================================================
    private static Bitmap removeBackground(Bitmap src) {
        final int w = src.getWidth(), h = src.getHeight();
        if (w < 8 || h < 8) return src;

        final int[] px = new int[w * h];
        src.getPixels(px, 0, w, 0, 0, w, h);

        // 已经有大片透明的 PNG，说明本来就是透明底，不必再动
        if (transparentRatio(px) > 0.15f) return src;

        int bg = borderColor(px, w, h);
        if (!isUniformBorder(px, w, h, bg)) return src;

        final boolean[] bgMask = new boolean[w * h];
        final int[] queue = new int[w * h];
        int head = 0, tail = 0;

        // 从四条边上与底色相近的像素出发，只清理与边缘连通的那片区域
        for (int x = 0; x < w; x++) {
            tail = seed(px, bgMask, queue, tail, x, 0, bg, w);
            tail = seed(px, bgMask, queue, tail, x, h - 1, bg, w);
        }
        for (int y = 0; y < h; y++) {
            tail = seed(px, bgMask, queue, tail, 0, y, bg, w);
            tail = seed(px, bgMask, queue, tail, w - 1, y, bg, w);
        }

        while (head < tail) {
            int idx = queue[head++];
            int x = idx % w, y = idx / w;
            if (x > 0) tail = seed(px, bgMask, queue, tail, x - 1, y, bg, w);
            if (x < w - 1) tail = seed(px, bgMask, queue, tail, x + 1, y, bg, w);
            if (y > 0) tail = seed(px, bgMask, queue, tail, x, y - 1, bg, w);
            if (y < h - 1) tail = seed(px, bgMask, queue, tail, x, y + 1, bg, w);
        }

        // 统计清掉了多少。如果几乎全被清掉，说明整张图就是一块底色（或 logo 与底同色），放弃
        int cleared = 0;
        for (int i = 0; i < bgMask.length; i++) if (bgMask[i]) cleared++;
        if (w * h - cleared < 64) return src;

        // 羽化：清掉的像素外围一圈按色差给半透明，消除白边
        int[] out = new int[w * h];
        System.arraycopy(px, 0, out, 0, px.length);
        final int feather = TOL * 2;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                if (bgMask[i]) {
                    out[i] = 0x00000000;
                    continue;
                }
                if (!nearCleared(bgMask, w, h, x, y)) continue;
                int d = dist(px[i], bg);
                if (d >= feather) continue;
                int alpha = Math.round(255f * d / feather);
                out[i] = (out[i] & 0x00FFFFFF) | (alpha << 24);
            }
        }

        Bitmap dst = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        dst.setPixels(out, 0, w, 0, 0, w, h);
        return dst;
    }

    /** 入队：已标记过、或与底色差太远，都不入 */
    private static int seed(int[] px, boolean[] mask, int[] queue, int tail,
                            int x, int y, int bg, int w) {
        int i = y * w + x;
        if (mask[i]) return tail;
        if (dist(px[i], bg) > TOL) return tail;
        mask[i] = true;
        queue[tail] = i;
        return tail + 1;
    }

    // ==================================================================
    // 工具
    // ==================================================================
    private static float transparentRatio(int[] px) {
        int n = 0;
        for (int p : px) if (Color.alpha(p) < 40) n++;
        return n / (float) px.length;
    }

    /** 取四边像素的中位数颜色（用中位数，个别杂色不干扰） */
    private static int borderColor(int[] px, int w, int h) {
        int[] rs = new int[4 * (w + h)];
        int[] gs = new int[rs.length];
        int[] bs = new int[rs.length];
        int n = 0;
        for (int x = 0; x < w; x++) {
            addColor(px[x], rs, gs, bs, n++);
            addColor(px[(h - 1) * w + x], rs, gs, bs, n++);
        }
        for (int y = 0; y < h; y++) {
            addColor(px[y * w], rs, gs, bs, n++);
            addColor(px[y * w + w - 1], rs, gs, bs, n++);
        }
        return Color.rgb(median(rs, n), median(gs, n), median(bs, n));
    }

    private static void addColor(int c, int[] r, int[] g, int[] b, int i) {
        if (i >= r.length) return;
        r[i] = Color.red(c);
        g[i] = Color.green(c);
        b[i] = Color.blue(c);
    }

    private static int median(int[] arr, int n) {
        if (n <= 0) return 255;
        int len = Math.min(n, arr.length);
        int[] copy = new int[len];
        System.arraycopy(arr, 0, copy, 0, len);
        java.util.Arrays.sort(copy);
        return copy[len / 2];
    }

    /** 边框是不是接近纯色（纯白底、纯色底都算） */
    private static boolean isUniformBorder(int[] px, int w, int h, int bg) {
        int hit = 0, total = 0;
        int step = Math.max(1, (w + h) / 120);
        for (int x = 0; x < w; x += step) {
            if (dist(px[x], bg) <= TOL) hit++;
            if (dist(px[(h - 1) * w + x], bg) <= TOL) hit++;
            total += 2;
        }
        for (int y = 0; y < h; y += step) {
            if (dist(px[y * w], bg) <= TOL) hit++;
            if (dist(px[y * w + w - 1], bg) <= TOL) hit++;
            total += 2;
        }
        return total > 0 && hit / (float) total >= UNIFORM_RATIO;
    }

    private static boolean nearCleared(boolean[] mask, int w, int h, int x, int y) {
        if (x > 0 && mask[y * w + x - 1]) return true;
        if (x < w - 1 && mask[y * w + x + 1]) return true;
        if (y > 0 && mask[(y - 1) * w + x]) return true;
        if (y < h - 1 && mask[(y + 1) * w + x]) return true;
        return false;
    }

    private static int dist(int c1, int c2) {
        int dr = Color.red(c1) - Color.red(c2);
        int dg = Color.green(c1) - Color.green(c2);
        int db = Color.blue(c1) - Color.blue(c2);
        return (int) Math.sqrt(dr * dr + dg * dg + db * db);
    }

    // ==================================================================
    // 裁空白边
    // ==================================================================
    /**
     * 裁掉四周的空白：既能裁透明边，也能裁「还没去底但四周是纯白」的边。
     */
    private static Bitmap trimMargins(Bitmap src) {
        final int w = src.getWidth(), h = src.getHeight();
        if (w < 8 || h < 8) return src;

        final int[] px = new int[w * h];
        src.getPixels(px, 0, w, 0, 0, w, h);

        int bg = borderColor(px, w, h);
        boolean uniform = isUniformBorder(px, w, h, bg);

        int top = 0, bottom = h - 1, left = 0, right = w - 1;
        while (top < bottom && rowBlank(px, w, h, top, bg, uniform)) top++;
        while (bottom > top && rowBlank(px, w, h, bottom, bg, uniform)) bottom--;
        while (left < right && colBlank(px, w, h, left, bg, uniform)) left++;
        while (right > left && colBlank(px, w, h, right, bg, uniform)) right--;

        // 留 1px 呼吸位，避免贴边被抗锯齿削掉
        top = Math.max(0, top - 1);
        left = Math.max(0, left - 1);
        bottom = Math.min(h - 1, bottom + 1);
        right = Math.min(w - 1, right + 1);

        int nw = right - left + 1, nh = bottom - top + 1;
        if (nw <= 0 || nh <= 0) return src;
        if (nw == w && nh == h) return src;
        return Bitmap.createBitmap(src, left, top, nw, nh);
    }

    private static boolean rowBlank(int[] px, int w, int h, int y, int bg, boolean uniform) {
        for (int x = 0; x < w; x++) {
            if (!isBlank(px[y * w + x], bg, uniform)) return false;
        }
        return true;
    }

    private static boolean colBlank(int[] px, int w, int h, int x, int bg, boolean uniform) {
        for (int y = 0; y < h; y++) {
            if (!isBlank(px[y * w + x], bg, uniform)) return false;
        }
        return true;
    }

    private static boolean isBlank(int c, int bg, boolean uniform) {
        if (Color.alpha(c) < 14) return true;
        return uniform && dist(c, bg) <= TOL;
    }
}
