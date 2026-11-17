package com.huaming.watermark.util;

import android.util.Log;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WeatherHelper {

    private static final String TAG = "WmWeather";
    /** "Sunny +11°C" → 描述 + 温度 */
    private static final Pattern FMT = Pattern.compile("^(.*?)\\s*([+-]?\\d+)\\s*°?C$");

    private WeatherHelper() {}

    /**
     * 通过 wttr.in 取当前天气（免费、无需 Key）。失败返回 null，调用方静默忽略。
     *
     * 返回值是中文，例如「多云 11℃」。
     */
    public static String fetch(double lat, double lon) {
        try {
            if (lat == 0d && lon == 0d) return null;
            String url = "https://wttr.in/" + String.format(Locale.US, "%.4f,%.4f", lat, lon)
                    + "?format=%C+%t";
            String s = LocationHelper.Net.get(url, 6000);
            Log.d(TAG, "req=" + url + " resp=" + s);
            if (s == null) return null;
            s = s.trim().replace("\n", " ").trim();
            if (s.isEmpty() || s.length() > 60) return null;
            if (s.startsWith("<") || s.toLowerCase(Locale.US).contains("unknown")) return null;

            String desc;
            String temp = null;
            Matcher m = FMT.matcher(s);
            if (m.matches()) {
                desc = m.group(1).trim();
                try {
                    temp = String.valueOf(Math.abs(Integer.parseInt(m.group(2))));
                } catch (NumberFormatException e) {
                    temp = null;
                }
            } else {
                desc = s;
            }
            if (desc.isEmpty()) return null;

            String zh = WeatherText.toChinese(desc);
            if (zh.isEmpty()) return null;
            return temp == null ? zh : zh + " " + temp + "℃";
        } catch (Exception e) {
            return null;
        }
    }
}
