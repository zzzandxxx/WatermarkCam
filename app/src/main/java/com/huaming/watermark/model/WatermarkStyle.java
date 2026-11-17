package com.huaming.watermark.model;

/**
 * 水印外观（模板 / 位置 / 缩放 / 透明度）。
 */
public class WatermarkStyle {

    public static final int T_CLASSIC = 0; // 经典黄条 · 底部渐变
    public static final int T_CARD = 1;    // 白色卡片
    public static final int T_GLASS = 2;   // 深色玻璃
    public static final int T_BLUE = 3;    // 工程蓝标
    public static final int T_PLAIN = 4;   // 纯文字
    public static final int T_SIDE = 5;    // 侧边竖条
    public static final int T_ORANGE = 6;  // 考勤橙卡
    public static final int T_GREEN = 7;   // 质检绿单
    public static final int T_NAVY = 8;    // 深蓝商务
    public static final int T_RED = 9;     // 警示红条
    public static final int T_CYAN = 10;   // 科技青框
    public static final int T_GOLD = 11;   // 黑金铭牌
    public static final int T_PILL = 12;   // 胶囊浮标
    public static final int T_OUTLINE = 13; // 描边大字

    public static final int TEMPLATE_COUNT = 14;

    public static final int P_AUTO = 0;
    public static final int P_BOTTOM_LEFT = 1;
    public static final int P_BOTTOM_RIGHT = 2;
    public static final int P_TOP_LEFT = 3;
    public static final int P_TOP_RIGHT = 4;

    public int template = T_CLASSIC;
    public int position = P_AUTO;
    /** 0.5 ~ 2.0 */
    public float scale = 1.0f;
    /** 0.35 ~ 1.0 */
    public float opacity = 1.0f;
    /** 通栏：占满画面宽度 */
    public boolean fullWidth = true;
    /** 铺在整幅画面上的淡文字水印 */
    public WatermarkStamp stamp = new WatermarkStamp();

    public static WatermarkStyle of(int template) {
        WatermarkStyle s = new WatermarkStyle();
        s.template = template;
        s.fullWidth = isFullWidthTemplate(template);
        return s;
    }

    public static boolean isFullWidthTemplate(int t) {
        return t == T_CLASSIC || t == T_BLUE;
    }

    public static String templateName(int t) {
        switch (t) {
            case T_CLASSIC: return "经典黄条";
            case T_CARD: return "白色卡片";
            case T_GLASS: return "深色玻璃";
            case T_BLUE: return "工程蓝标";
            case T_PLAIN: return "纯文字";
            case T_SIDE: return "侧边竖条";
            case T_ORANGE: return "考勤橙卡";
            case T_GREEN: return "质检绿单";
            case T_NAVY: return "深蓝商务";
            case T_RED: return "警示红条";
            case T_CYAN: return "科技青框";
            case T_GOLD: return "黑金铭牌";
            case T_PILL: return "胶囊浮标";
            case T_OUTLINE: return "描边大字";
            default: return "经典黄条";
        }
    }

    public WatermarkStyle copy() {
        WatermarkStyle o = new WatermarkStyle();
        o.template = template;
        o.position = position;
        o.scale = scale;
        o.opacity = opacity;
        o.fullWidth = fullWidth;
        o.stamp = stamp.copy();
        return o;
    }

    public void applyTemplate(int t) {
        this.template = t;
        this.fullWidth = isFullWidthTemplate(t);
        if (t == T_CLASSIC || t == T_BLUE) {
            this.position = P_AUTO;
        } else if (this.position == P_AUTO) {
            this.position = P_BOTTOM_LEFT;
        }
    }
}
