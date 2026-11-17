package com.huaming.watermark.model;

/**
 * 照片上的淡文字水印（平铺在画面上的浅色大字）。
 *
 * 与左下角那块信息水印是两回事：信息水印承载「时间/地点/经纬度」这类取证字段，
 * 淡文字水印是整幅画面都铺着的自定义文字，通常用来标「内部资料」「禁止外传」
 * 这类提示，特点是透明度很低、字号大、斜着平铺。
 */
public class WatermarkStamp {

    /** 是否启用 */
    public boolean enabled = false;
    /** 文字内容，支持 \n 换行 */
    public String text = "";
    /** 不透明度 0.02 ~ 0.5，默认 10% */
    public float opacity = 0.10f;
    /** 字号占画面宽度的比例 0.01 ~ 0.10，默认 4.5% */
    public float sizeRatio = 0.045f;
    /** 倾斜角度（度） -90 ~ 90 */
    public float angle = -30f;
    /** true = 平铺满屏；false = 单条居中 */
    public boolean tiled = true;
    /** 文字颜色，默认白色 */
    public int color = 0xFFFFFFFF;

    public WatermarkStamp copy() {
        WatermarkStamp o = new WatermarkStamp();
        o.enabled = enabled;
        o.text = text;
        o.opacity = opacity;
        o.sizeRatio = sizeRatio;
        o.angle = angle;
        o.tiled = tiled;
        o.color = color;
        return o;
    }
}
