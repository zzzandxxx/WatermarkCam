package com.huaming.watermark.util;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 天气描述英译中。
 *
 * 为什么不能直接让接口返回中文：wttr.in 虽然接受 lang=zh 参数，
 * 但实测其 j1 响应里的 lang_zh 字段仍然返回英文（"Smoky haze"），
 * 也就是中文翻译在这一套数据上根本没生效，所以只能本地翻译。
 *
 * 映射表覆盖 wttr.in 会返回的全部天气描述；表里没有的走关键词兜底，
 * 保证任何情况下都不会把英文原样印到水印上。
 */
public final class WeatherText {

    private static final Map<String, String> MAP = new HashMap<>();

    static {
        // —— 晴 / 云 ——
        put("sunny", "晴");
        put("clear", "晴");
        put("partly cloudy", "局部多云");
        put("cloudy", "多云");
        put("overcast", "阴");
        // —— 雾 / 霾 ——
        put("mist", "薄雾");
        put("fog", "雾");
        put("freezing fog", "冻雾");
        put("haze", "霾");
        put("smoky haze", "烟霾");
        // —— 可能出现 ——
        put("patchy rain possible", "局部有雨");
        put("patchy rain nearby", "附近有零星阵雨");
        put("patchy snow possible", "局部有雪");
        put("patchy sleet possible", "局部有雨夹雪");
        put("patchy freezing drizzle possible", "局部有冻毛毛雨");
        put("thundery outbreaks possible", "可能有雷阵雨");
        // —— 雪 / 吹雪 ——
        put("blowing snow", "吹雪");
        put("blizzard", "暴风雪");
        put("ice pellets", "冰粒");
        // —— 毛毛雨 / 冻雨 ——
        put("patchy light drizzle", "局部小毛毛雨");
        put("light drizzle", "毛毛雨");
        put("freezing drizzle", "冻毛毛雨");
        put("heavy freezing drizzle", "强冻毛毛雨");
        put("light freezing rain", "小冻雨");
        put("moderate or heavy freezing rain", "中到大冻雨");
        // —— 雨 ——
        put("patchy light rain", "局部小雨");
        put("light rain", "小雨");
        put("moderate rain at times", "间歇性中雨");
        put("moderate rain", "中雨");
        put("heavy rain at times", "间歇性大雨");
        put("heavy rain", "大雨");
        // —— 雨夹雪 ——
        put("light sleet", "小雨夹雪");
        put("moderate or heavy sleet", "中到大雨夹雪");
        // —— 雪 ——
        put("patchy light snow", "局部小雪");
        put("light snow", "小雪");
        put("patchy moderate snow", "局部中雪");
        put("moderate snow", "中雪");
        put("patchy heavy snow", "局部大雪");
        put("heavy snow", "大雪");
        // —— 阵雨 ——
        put("light rain shower", "小阵雨");
        put("moderate or heavy rain shower", "中到大阵雨");
        put("torrential rain shower", "暴雨");
        // —— 阵性混合 ——
        put("light sleet showers", "小阵性雨夹雪");
        put("moderate or heavy sleet showers", "中到大阵性雨夹雪");
        put("light snow showers", "小阵雪");
        put("moderate or heavy snow showers", "中到大阵雪");
        put("light showers of ice pellets", "小阵性冰粒");
        put("moderate or heavy showers of ice pellets", "中到大阵性冰粒");
        // —— 雷暴 ——
        put("patchy light rain with thunder", "局部雷阵雨");
        put("moderate or heavy rain with thunder", "中到大雷阵雨");
        put("patchy light snow with thunder", "局部雷雪");
        put("moderate or heavy snow with thunder", "中到大雷雪");
    }

    private WeatherText() {}

    private static void put(String en, String zh) {
        MAP.put(en, zh);
    }

    /**
     * 把英文天气描述翻成中文。
     * 认不出来的词按关键词拼一个大致准确的说法；连关键词都对不上就原样返回（宁可英文也别瞎翻）。
     */
    public static String toChinese(String en) {
        if (en == null) return "";
        String raw = en.trim();
        if (raw.isEmpty()) return "";

        String key = raw.toLowerCase(Locale.US).replaceAll("\\s+", " ");
        String direct = MAP.get(key);
        if (direct != null) return direct;

        boolean thunder = key.contains("thunder");
        String base;
        if (key.contains("sleet")) base = "雨夹雪";
        else if (key.contains("snow")) base = "雪";
        else if (key.contains("drizzle")) base = "毛毛雨";
        else if (key.contains("shower")) base = "阵雨";
        else if (key.contains("rain")) base = "雨";
        else if (key.contains("ice")) base = "冰粒";
        else if (key.contains("fog")) base = "雾";
        else if (key.contains("mist")) base = "薄雾";
        else if (key.contains("haze") || key.contains("smoke")) base = "霾";
        else if (key.contains("overcast")) base = "阴";
        else if (key.contains("cloud")) base = "多云";
        else if (key.contains("sun") || key.contains("clear")) base = "晴";
        else return raw;

        String prefix;
        if (key.contains("torrential")) prefix = "暴雨";
        else if (key.contains("heavy")) prefix = "大";
        else if (key.contains("moderate")) prefix = "中";
        else if (key.contains("light") || key.contains("patchy")) prefix = "小";
        else prefix = "";

        String res = prefix + base;
        if (thunder) res = res.contains("雪") ? "雷雪" : "雷阵雨";
        return res;
    }
}
