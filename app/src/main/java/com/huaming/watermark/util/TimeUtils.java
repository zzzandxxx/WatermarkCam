package com.huaming.watermark.util;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public final class TimeUtils {

    private static final String[] WEEK = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};

    private TimeUtils() {}

    public static String time(boolean withSeconds) {
        return new SimpleDateFormat(withSeconds ? "HH:mm:ss" : "HH:mm", Locale.CHINA)
                .format(new Date());
    }

    public static String timeAt(long millis, boolean withSeconds) {
        return new SimpleDateFormat(withSeconds ? "HH:mm:ss" : "HH:mm", Locale.CHINA)
                .format(new Date(millis));
    }

    public static String dateWeek() {
        return dateWeekAt(System.currentTimeMillis());
    }

    public static String dateWeekAt(long millis) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(millis);
        String d = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(cal.getTime());
        return d + " " + WEEK[cal.get(Calendar.DAY_OF_WEEK) - 1];
    }

    public static String fullStamp() {
        return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(new Date());
    }

    public static String fullStampAt(long millis) {
        return new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(new Date(millis));
    }

    public static String displayDateTime(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(new Date(millis));
    }

    public static String itemStamp(long millis) {
        Calendar now = Calendar.getInstance();
        Calendar t = Calendar.getInstance();
        t.setTimeInMillis(millis);
        boolean sameDay = now.get(Calendar.YEAR) == t.get(Calendar.YEAR)
                && now.get(Calendar.DAY_OF_YEAR) == t.get(Calendar.DAY_OF_YEAR);
        String pattern = sameDay ? "HH:mm" : "MM-dd HH:mm";
        return new SimpleDateFormat(pattern, Locale.CHINA).format(t.getTime());
    }

    /** 31.230416°N 121.473701°E */
    public static String coord(double lat, double lon) {
        if (lat == 0d && lon == 0d) return "";
        String ns = lat >= 0 ? "N" : "S";
        String ew = lon >= 0 ? "E" : "W";
        return String.format(Locale.US, "%.6f°%s %.6f°%s", Math.abs(lat), ns, Math.abs(lon), ew);
    }

    /** 相近精度但不带方向的简写：31.2304, 121.4737 */
    public static String coordShort(double lat, double lon) {
        if (lat == 0d && lon == 0d) return "";
        return String.format(Locale.US, "%.6f, %.6f", lat, lon);
    }
}
