package com.huaming.watermark.model;

import android.graphics.Bitmap;

/**
 * 水印要显示的内容。字段为空字符串时不绘制该行。
 */
public class WatermarkData {

    /** 标题，如「工程巡检记录」「考勤打卡」 */
    public String title = "现场取证";
    /** 大号时间，如 14:32 或 14:32:05 */
    public String timeText = "";
    /** 日期 + 星期，如 2026-10-22 周四 */
    public String dateText = "";
    /** 地点名称 */
    public String location = "";
    /** 经纬度文本 */
    public String coord = "";
    /** 天气，如 晴 24℃ */
    public String weather = "";
    /** 备注（多行用 \n） */
    public String remark = "";
    /** 打卡人 */
    public String person = "";
    /** 单位 / 项目 */
    public String project = "";

    /** 当前选中的 logo 位图，可为 null；由 LogoStore 负责加载与释放 */
    public Bitmap logo = null;

    public WatermarkData copy() {
        WatermarkData o = new WatermarkData();
        o.title = title;
        o.timeText = timeText;
        o.dateText = dateText;
        o.location = location;
        o.coord = coord;
        o.weather = weather;
        o.remark = remark;
        o.person = person;
        o.project = project;
        o.logo = logo;
        return o;
    }

    public static boolean has(String s) {
        return s != null && s.trim().length() > 0;
    }
}
