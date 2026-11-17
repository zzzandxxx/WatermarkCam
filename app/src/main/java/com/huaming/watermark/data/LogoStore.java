package com.huaming.watermark.data;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.OpenableColumns;

import androidx.core.content.ContextCompat;

import com.huaming.watermark.util.ImageUtils;
import com.huaming.watermark.util.LogoProcessor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Logo 素材库。
 *
 * 三类素材：
 * 1. 场景化矢量图标（工地/巡检/考勤等），本应用自绘；
 * 2. 企业标识：按「企业简称 + 品牌主色」实时生成的文字标，分行业归类、可搜索，
 *    不含任何第三方企业的商标图样；
 * 3. 用户自行导入的图片（只保存在本机，不随安装包分发）。
 *
 * 需要官方图形时，在素材库底部「导入」里一次选多张图片即可。
 */
public class LogoStore {

    public static final String NONE = "none";
    public static final String PREFIX_RES = "res:";
    public static final String PREFIX_FILE = "file:";
    public static final String PREFIX_BRAND = "brand:";

    /** 自定义 logo 的存储格式版本，用于老图一次性迁移 */
    private static final int LOGO_VER = 2;

    /** 分类标签，顺序即界面上的顺序 */
    public static final String CAT_HOT = "常用";
    public static final String CAT_ICON = "场景图标";
    public static final String CAT_MINE = "我的";

    public static final String[] CATS = {
            "常用", "通信", "能源电力", "建筑基建", "制造工业",
            "科技互联网", "物流零售", "金融地产", "生活服务",
            CAT_ICON, CAT_MINE
    };

    /** 内置场景图标：{资源名, 显示名} */
    private static final String[][] BUILTIN = {
            {"ic_wm_site", "工地"},
            {"ic_wm_check", "巡检"},
            {"ic_wm_attend", "考勤"},
            {"ic_wm_truck", "物流"},
            {"ic_wm_store", "门店"},
            {"ic_wm_helmet", "安全"},
            {"ic_wm_gear", "设备"},
            {"ic_wm_leaf", "环保"},
            {"ic_wm_bolt", "电力"},
            {"ic_wm_build", "建筑"},
            {"ic_wm_shield", "质安"},
            {"ic_wm_mark", "字母标"},
    };

    /** 企业文字标：{分类, 全称, 简称, 品牌主色, 是否常用} */
    private static final String[][] BRANDS = {
            // ---------- 通信 ----------
            {"通信", "中国电信", "电信", "#0066B3", "1"},
            {"通信", "中国移动", "移动", "#00A0E9", "1"},
            {"通信", "中国联通", "联通", "#E60012", "1"},
            {"通信", "中国广电", "广电", "#1E6FD9", "0"},
            {"通信", "中移铁通", "铁通", "#0093D9", "0"},
            {"通信", "中国铁塔", "铁塔", "#C8102E", "0"},

            // ---------- 能源电力 ----------
            {"能源电力", "国家电网", "国网", "#009944", "1"},
            {"能源电力", "南方电网", "南网", "#00A0E9", "0"},
            {"能源电力", "中国石油", "石油", "#D7000F", "0"},
            {"能源电力", "中国石化", "石化", "#E4002B", "0"},
            {"能源电力", "中国海油", "海油", "#005BAC", "0"},
            {"能源电力", "国家能源", "国能", "#007A33", "0"},
            {"能源电力", "中国华能", "华能", "#0072BC", "0"},
            {"能源电力", "中国大唐", "大唐", "#E60012", "0"},
            {"能源电力", "中国华电", "华电", "#1F7A3F", "0"},
            {"能源电力", "国家电投", "电投", "#0072BC", "0"},
            {"能源电力", "三峡集团", "三峡", "#0B6E4F", "0"},
            {"能源电力", "中核集团", "中核", "#C8102E", "0"},

            // ---------- 建筑基建 ----------
            {"建筑基建", "中国建筑", "中建", "#0057A5", "1"},
            {"建筑基建", "中国电建", "电建", "#0B4F9E", "1"},
            {"建筑基建", "中国中铁", "中铁", "#005BAC", "0"},
            {"建筑基建", "中国铁建", "铁建", "#C8102E", "0"},
            {"建筑基建", "中国交建", "中交", "#005BAC", "0"},
            {"建筑基建", "中国能建", "能建", "#007A33", "0"},
            {"建筑基建", "中国中冶", "中冶", "#005BAC", "0"},
            {"建筑基建", "上海建工", "建工", "#1B4E9B", "0"},

            // ---------- 制造工业 ----------
            {"制造工业", "中国中车", "中车", "#005BAC", "0"},
            {"制造工业", "中国船舶", "船舶", "#003C7D", "0"},
            {"制造工业", "中国商飞", "商飞", "#1B4E9B", "0"},
            {"制造工业", "中国宝武", "宝武", "#005BAC", "0"},
            {"制造工业", "中国五矿", "五矿", "#005BAC", "0"},
            {"制造工业", "中国电科", "电科", "#005BAC", "0"},
            {"制造工业", "航天科技", "航天", "#C8102E", "0"},
            {"制造工业", "上汽集团", "上汽", "#1B6CB5", "0"},
            {"制造工业", "广汽集团", "广汽", "#E60012", "0"},
            {"制造工业", "三一重工", "三一", "#E4002B", "0"},
            {"制造工业", "徐工集团", "徐工", "#E4002B", "0"},
            {"制造工业", "格力电器", "格力", "#0E5FB5", "0"},
            {"制造工业", "美的集团", "美的", "#1F8FD4", "0"},
            {"制造工业", "海尔智家", "海尔", "#005BA8", "0"},
            {"制造工业", "正泰集团", "正泰", "#005BAC", "1"},

            // ---------- 科技互联网 ----------
            {"科技互联网", "华为", "华为", "#CF0A2C", "1"},
            {"科技互联网", "小米", "小米", "#FF6900", "1"},
            {"科技互联网", "腾讯", "腾讯", "#0052D9", "0"},
            {"科技互联网", "阿里巴巴", "阿里", "#FF6A00", "0"},
            {"科技互联网", "百度", "百度", "#2932E1", "0"},
            {"科技互联网", "字节跳动", "字节", "#325AB4", "0"},
            {"科技互联网", "网易", "网易", "#E60012", "0"},
            {"科技互联网", "联想", "联想", "#E2231A", "0"},
            {"科技互联网", "比亚迪", "比亚迪", "#0A2D5E", "1"},
            {"科技互联网", "宁德时代", "宁德", "#00A0E9", "0"},
            {"科技互联网", "大疆创新", "大疆", "#1A1A1A", "0"},

            // ---------- 物流零售 ----------
            {"物流零售", "中国邮政", "邮政", "#007A33", "0"},
            {"物流零售", "顺丰速运", "顺丰", "#D2222A", "1"},
            {"物流零售", "京东", "京东", "#E1251B", "0"},
            {"物流零售", "中通快递", "中通", "#0055A4", "0"},
            {"物流零售", "圆通速递", "圆通", "#0057A8", "0"},
            {"物流零售", "韵达股份", "韵达", "#E60012", "0"},
            {"物流零售", "申通快递", "申通", "#005BAC", "0"},
            {"物流零售", "德邦快递", "德邦", "#005BAC", "0"},
            {"物流零售", "永辉超市", "永辉", "#E60012", "0"},

            // ---------- 金融地产 ----------
            {"金融地产", "工商银行", "工行", "#C7000B", "0"},
            {"金融地产", "建设银行", "建行", "#005BAC", "0"},
            {"金融地产", "农业银行", "农行", "#00954E", "0"},
            {"金融地产", "中国银行", "中行", "#A71C2B", "0"},
            {"金融地产", "交通银行", "交行", "#005BAC", "0"},
            {"金融地产", "招商银行", "招行", "#C7000B", "0"},
            {"金融地产", "中国人保", "人保", "#C7000B", "1"},
            {"金融地产", "中国人寿", "国寿", "#C7000B", "0"},
            {"金融地产", "中国平安", "平安", "#EE7800", "0"},
            {"金融地产", "万科", "万科", "#E60012", "0"},
            {"金融地产", "保利发展", "保利", "#1B6CB5", "0"},
            {"金融地产", "碧桂园", "碧桂园", "#005BAC", "0"},
            {"金融地产", "贝壳找房", "贝壳", "#1C8FE0", "1"},
            {"金融地产", "链家", "链家", "#00A64F", "1"},

            // ---------- 生活服务 ----------
            {"生活服务", "美团", "美团", "#FFD100", "1"},
            {"生活服务", "蜜雪冰城", "蜜雪", "#E60028", "1"},
            {"生活服务", "海底捞", "海底捞", "#E4002B", "1"},
            {"生活服务", "瑞幸咖啡", "瑞幸", "#002D62", "0"},
            {"生活服务", "星巴克", "星巴克", "#006241", "0"},
            {"生活服务", "麦当劳", "麦当劳", "#FFC72C", "0"},
            {"生活服务", "肯德基", "肯德基", "#E4002B", "0"},
            {"生活服务", "牧原股份", "牧原", "#2E7D32", "1"},
            {"生活服务", "万达集团", "万达", "#005BAC", "0"},
    };

    public static class Item {
        public final String id;
        public String name;
        public final boolean builtin;

        public Item(String id, String name, boolean builtin) {
            this.id = id;
            this.name = name;
            this.builtin = builtin;
        }
    }

    public static List<Item> builtins() {
        List<Item> list = new ArrayList<>();
        for (String[] b : BUILTIN) {
            list.add(new Item(PREFIX_RES + b[0], b[1], true));
        }
        return list;
    }

    public static List<Item> brands() {
        List<Item> list = new ArrayList<>();
        for (String[] b : BRANDS) {
            list.add(new Item(PREFIX_BRAND + b[1], b[1], true));
        }
        return list;
    }

    /** 某个分类下的品牌；CAT_HOT 返回标记为常用的品牌 */
    public static List<Item> brandsOf(String cat) {
        List<Item> list = new ArrayList<>();
        for (String[] b : BRANDS) {
            boolean hit = CAT_HOT.equals(cat) ? "1".equals(b[4]) : cat.equals(b[0]);
            if (hit) list.add(new Item(PREFIX_BRAND + b[1], b[1], true));
        }
        return list;
    }

    /** 按品牌名/简称模糊搜索，跨全部分类 */
    public static List<Item> search(String keyword) {
        List<Item> list = new ArrayList<>();
        String q = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return list;
        for (String[] b : BRANDS) {
            String full = b[1].toLowerCase(Locale.ROOT);
            String shrt = b[2].toLowerCase(Locale.ROOT);
            if (full.contains(q) || shrt.contains(q)) {
                list.add(new Item(PREFIX_BRAND + b[1], b[1], true));
            }
        }
        for (String[] b : BUILTIN) {
            if (b[1].contains(keyword.trim())) {
                list.add(new Item(PREFIX_RES + b[0], b[1], true));
            }
        }
        return list;
    }

    private static String[] brandRow(String key) {
        for (String[] b : BRANDS) if (b[1].equals(key)) return b;
        return null;
    }

    /** 品牌主色，用于界面上的小色点 */
    public static int brandColor(String key) {
        String[] row = brandRow(key);
        if (row == null) return 0xFF5F6B7C;
        try {
            return Color.parseColor(row[3]);
        } catch (Exception e) {
            return 0xFF5F6B7C;
        }
    }

    /** 把品牌文字标画成方图 */
    private static Bitmap renderBrand(String shortName, int color, int size) {
        Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);

        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setShader(new LinearGradient(0, 0, size, size,
                color, shade(color, 0.78f), Shader.TileMode.CLAMP));
        float r = size * 0.235f;
        c.drawRoundRect(new RectF(0, 0, size, size), r, r, bg);

        // 顶部一层极淡高光，避免整块死平
        Paint gloss = new Paint(Paint.ANTI_ALIAS_FLAG);
        gloss.setShader(new LinearGradient(0, 0, 0, size * 0.62f,
                0x24FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawRoundRect(new RectF(0, 0, size, size), r, r, gloss);

        String monogram = shortName == null ? "" : shortName.trim();
        if (monogram.length() > 3) monogram = monogram.substring(0, 3);
        if (monogram.isEmpty()) return bmp;

        Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
        t.setColor(isLight(color) ? 0xFF1A1A1A : 0xFFFFFFFF);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setTextAlign(Paint.Align.CENTER);
        float fs = monogram.length() <= 1 ? 0.50f : monogram.length() == 2 ? 0.38f : 0.27f;
        t.setTextSize(size * fs);
        Paint.FontMetrics fm = t.getFontMetrics();
        float baseline = size / 2f - (fm.ascent + fm.descent) / 2f;
        c.drawText(monogram, size / 2f, baseline, t);
        return bmp;
    }

    private static int shade(int color, float factor) {
        int a = Color.alpha(color);
        int r = Math.round(Color.red(color) * factor);
        int g = Math.round(Color.green(color) * factor);
        int b = Math.round(Color.blue(color) * factor);
        return Color.argb(a, Math.min(255, r), Math.min(255, g), Math.min(255, b));
    }

    private static boolean isLight(int color) {
        double l = 0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color);
        return l > 165;
    }

    public static File logoDir(Context ctx) {
        File dir = new File(ctx.getFilesDir(), "logos");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static List<Item> customs(Context ctx) {
        List<Item> list = new ArrayList<>();
        File[] files = logoDir(ctx).listFiles();
        if (files == null) return list;
        Arrays.sort(files, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return Long.compare(b.lastModified(), a.lastModified());
            }
        });
        for (File f : files) {
            if (!f.isFile()) continue;
            String n = f.getName();
            int dot = n.lastIndexOf('.');
            String show = dot > 0 ? n.substring(0, dot) : n;
            list.add(new Item(PREFIX_FILE + f.getAbsolutePath(), show, false));
        }
        return list;
    }

    public static Item find(Context ctx, String id) {
        if (id == null || NONE.equals(id)) return null;
        for (Item i : brands()) if (i.id.equals(id)) return i;
        for (Item i : builtins()) if (i.id.equals(id)) return i;
        for (Item i : customs(ctx)) if (i.id.equals(id)) return i;
        return null;
    }

    public static String displayName(Context ctx, String id) {
        Item i = find(ctx, id);
        return i == null ? "无" : i.name;
    }

    /** 加载 logo 位图；size 为渲染边长（内置图标）或最大边（自定义图片） */
    public static Bitmap load(Context ctx, String id, int size) {
        if (id == null || NONE.equals(id) || id.isEmpty()) return null;
        try {
            if (id.startsWith(PREFIX_BRAND)) {
                String key = id.substring(PREFIX_BRAND.length());
                String[] row = brandRow(key);
                if (row == null) return null;
                return renderBrand(row[2], Color.parseColor(row[3]), size);
            }
            if (id.startsWith(PREFIX_RES)) {
                String name = id.substring(PREFIX_RES.length());
                int resId = ctx.getResources().getIdentifier(name, "drawable", ctx.getPackageName());
                if (resId == 0) return null;
                Drawable d = ContextCompat.getDrawable(ctx, resId);
                if (d == null) return null;
                Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                Canvas c = new Canvas(bmp);
                d.setBounds(0, 0, size, size);
                d.draw(c);
                return bmp;
            }
            if (id.startsWith(PREFIX_FILE)) {
                String path = id.substring(PREFIX_FILE.length());
                // 不强制方形：真实企业 logo 多为横版，压成方形会在水印里缩成一小条
                return ImageUtils.loadBitmap(path, Math.max(size, 96));
            }
        } catch (Exception | OutOfMemoryError ignored) {
        }
        return null;
    }

    /** 取相册里这张图的文件名，作为 logo 名称 */
    public static String nameOfUri(Context ctx, Uri uri) {
        try (Cursor c = ctx.getContentResolver()
                .query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                String n = c.getString(0);
                if (n != null && !n.isEmpty()) {
                    int dot = n.lastIndexOf('.');
                    return dot > 0 ? n.substring(0, dot) : n;
                }
            }
        } catch (Exception ignored) {
        }
        return "我的Logo";
    }

    /** 从相册 Uri 导入为本地 logo，返回新 id（默认去白底 + 裁空白边） */
    public static String importFrom(Context ctx, Uri uri, String baseName) throws IOException {
        return importFrom(ctx, uri, baseName, new LogoProcessor.Options());
    }

    /** 从相册 Uri 导入为本地 logo，可指定是否去底、是否裁边 */
    public static String importFrom(Context ctx, Uri uri, String baseName,
                                    LogoProcessor.Options opt) throws IOException {
        String safe = (baseName == null ? "" : baseName).trim();
        if (safe.isEmpty()) safe = "logo";
        safe = safe.replaceAll("[\\\\/:*?\"<>|]", "_");
        File dir = logoDir(ctx);
        File dst = new File(dir, safe + ".png");
        int n = 2;
        while (dst.exists()) {
            dst = new File(dir, safe + "_" + n + ".png");
            n++;
        }
        File tmp = new File(dir, ".tmp_" + System.currentTimeMillis() + ".img");
        ImageUtils.copyUriToFile(ctx, uri, tmp);
        // 处理时用 1024 解码：去底靠的是像素色差，先缩太小会把细笔画和底色糊在一起
        Bitmap decoded = ImageUtils.loadBitmap(tmp.getAbsolutePath(), 1024);
        tmp.delete();
        if (decoded == null) throw new IOException("无法解码该图片");

        Bitmap out = LogoProcessor.process(decoded, opt);
        if (out == null) throw new IOException("图片处理失败");

        boolean ok = false;
        try (FileOutputStream os = new FileOutputStream(dst)) {
            ok = out.compress(Bitmap.CompressFormat.PNG, 100, os);
            os.flush();
        } catch (IOException e) {
            dst.delete();
            throw e;
        }
        if (!ok) {
            dst.delete();
            throw new IOException("图片保存失败");
        }
        return PREFIX_FILE + dst.getAbsolutePath();
    }

    /** 对已有的自定义 logo 重新处理（比如之前没去白底） */
    public static boolean reprocess(Context ctx, String id, LogoProcessor.Options opt) {
        if (id == null || !id.startsWith(PREFIX_FILE)) return false;
        File src = new File(id.substring(PREFIX_FILE.length()));
        if (!src.exists()) return false;
        Bitmap raw = ImageUtils.loadBitmap(src.getAbsolutePath(), 1024);
        if (raw == null) return false;
        Bitmap out = LogoProcessor.process(raw, opt);
        if (out == null) return false;

        // 先写临时文件再替换，避免写一半断电把原图毁了
        File tmp = new File(src.getParentFile(), ".rp_" + System.currentTimeMillis() + ".png");
        try (FileOutputStream os = new FileOutputStream(tmp)) {
            if (!out.compress(Bitmap.CompressFormat.PNG, 100, os)) return false;
            os.flush();
        } catch (Exception e) {
            tmp.delete();
            return false;
        }
        File backup = new File(src.getParentFile(), ".bak_" + src.getName());
        if (backup.exists()) backup.delete();
        if (!src.renameTo(backup)) {
            tmp.delete();
            return false;
        }
        if (tmp.renameTo(src)) {
            backup.delete();
            return true;
        }
        backup.renameTo(src);
        tmp.delete();
        return false;
    }

    /**
     * 老版本导入的 logo 都被压成了 512×512 的方形（四周透明留白），
     * 在按真实宽高比排版的水印里会显得又小又空。
     * 这里做一次性迁移：把方形画布上的空白边裁掉，原图不动内容。
     */
    public static void ensureModernLogos(Context ctx) {
        Prefs p = new Prefs(ctx);
        if (p.logoVer() >= LOGO_VER) return;
        File[] files = logoDir(ctx).listFiles();
        if (files != null) {
            for (File f : files) {
                if (!f.isFile() || f.getName().startsWith(".")) continue;
                try {
                    Bitmap raw = ImageUtils.loadBitmap(f.getAbsolutePath(), 1024);
                    if (raw == null) continue;
                    if (raw.getWidth() != raw.getHeight()) continue;  // 已经是新格式
                    LogoProcessor.Options opt = new LogoProcessor.Options(false, true);
                    Bitmap out = LogoProcessor.process(raw, opt);
                    if (out == null || out == raw) continue;
                    if (out.getWidth() == raw.getWidth() && out.getHeight() == raw.getHeight()) continue;
                    File tmp = new File(f.getParentFile(), ".mg_" + System.currentTimeMillis() + ".png");
                    try (FileOutputStream os = new FileOutputStream(tmp)) {
                        out.compress(Bitmap.CompressFormat.PNG, 100, os);
                        os.flush();
                    }
                    if (!tmp.renameTo(f)) tmp.delete();
                } catch (Exception | OutOfMemoryError ignored) {
                }
            }
        }
        p.setLogoVer(LOGO_VER);
    }

    public static void delete(Context ctx, String id) {
        if (id == null || !id.startsWith(PREFIX_FILE)) return;
        File f = new File(id.substring(PREFIX_FILE.length()));
        if (f.exists()) f.delete();
    }

    public static boolean rename(Context ctx, String id, String newName) {
        if (id == null || !id.startsWith(PREFIX_FILE)) return false;
        File src = new File(id.substring(PREFIX_FILE.length()));
        if (!src.exists()) return false;
        String safe = newName.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        if (safe.isEmpty()) return false;
        File dst = new File(src.getParentFile(), safe + ".png");
        if (dst.exists()) return false;
        return src.renameTo(dst);
    }

    public static String idAfterRename(String oldId, String newName) {
        if (oldId == null || !oldId.startsWith(PREFIX_FILE)) return oldId;
        File src = new File(oldId.substring(PREFIX_FILE.length()));
        String safe = newName.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
        return PREFIX_FILE + new File(src.getParentFile(), safe + ".png").getAbsolutePath();
    }
}
