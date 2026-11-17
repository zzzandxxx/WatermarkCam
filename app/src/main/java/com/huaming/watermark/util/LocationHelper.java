package com.huaming.watermark.util;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;
import java.util.Locale;

/**
 * 定位 + 逆地理编码。
 *
 * 优先取系统缓存位置（快），没有则单次请求。
 * 地址解析优先高德 Web API（需在设置里填 Key，坐标自动转 GCJ-02），
 * 否则退回系统 Geocoder；都失败时返回经纬度，界面允许手动填写地点。
 */
public final class LocationHelper {

    public interface Callback {
        /** address 可能为 null，表示解析失败 */
        void onResult(double lat, double lon, String address, String source);

        void onError(String message);
    }

    private LocationHelper() {}

    public static void request(Context ctx, Callback cb, long timeoutMs) {
        final Context app = ctx.getApplicationContext();
        LocationManager lm = (LocationManager) app.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) {
            cb.onError("系统定位服务不可用");
            return;
        }

        Location best = null;
        try {
            for (String p : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(p);
                if (l == null) continue;
                if (best == null) {
                    best = l;
                } else {
                    boolean a = l.getTime() > best.getTime() - 60_000L;
                    if (l.getAccuracy() < best.getAccuracy() && a) best = l;
                }
            }
        } catch (SecurityException e) {
            cb.onError("缺少定位权限");
            return;
        }

        boolean fresh = best != null && (System.currentTimeMillis() - best.getTime()) < 120_000L;
        if (best != null && fresh && best.getAccuracy() <= 120f) {
            resolveAddress(app, best.getLatitude(), best.getLongitude(), cb);
            return;
        }

        final Handler main = new Handler(Looper.getMainLooper());
        final LocationListener[] holder = new LocationListener[1];
        final boolean[] done = new boolean[]{false};
        final Location seed = best;

        LocationListener listener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                if (done[0]) return;
                done[0] = true;
                try {
                    lm.removeUpdates(holder[0]);
                } catch (Exception ignored) {
                }
                main.removeCallbacksAndMessages(null);
                resolveAddress(app, location.getLatitude(), location.getLongitude(), cb);
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {}

            @Override
            public void onProviderEnabled(String provider) {}

            @Override
            public void onProviderDisabled(String provider) {}
        };
        holder[0] = listener;

        try {
            boolean started = false;
            for (String p : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
                if (lm.isProviderEnabled(p)) {
                    lm.requestLocationUpdates(p, 800L, 0f, listener, Looper.getMainLooper());
                    started = true;
                }
            }
            if (!started) {
                if (seed != null) {
                    resolveAddress(app, seed.getLatitude(), seed.getLongitude(), cb);
                } else {
                    cb.onError("定位服务已关闭，请在系统设置中开启");
                }
                return;
            }
        } catch (SecurityException e) {
            cb.onError("缺少定位权限");
            return;
        }

        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (done[0]) return;
                done[0] = true;
                try {
                    lm.removeUpdates(holder[0]);
                } catch (Exception ignored) {
                }
                if (seed != null) {
                    resolveAddress(app, seed.getLatitude(), seed.getLongitude(), cb);
                } else {
                    cb.onError("定位超时，请到室外或开阔处重试");
                }
            }
        }, Math.max(3000L, timeoutMs));
    }

    /** 逆地理编码：高德优先，失败退回系统 Geocoder */
    public static void resolveAddress(final Context ctx, final double lat, final double lon, final Callback cb) {
        final String amapKey = new com.huaming.watermark.data.Prefs(ctx).amapKey().trim();
        new Thread(() -> {
            String addr = null;
            String source = "";
            if (!amapKey.isEmpty()) {
                addr = amapReverse(amapKey, lat, lon);
                if (addr != null) source = "高德";
            }
            if (addr == null) {
                addr = systemReverse(ctx, lat, lon);
                if (addr != null) source = "系统";
            }
            final String a = addr;
            final String s = source;
            new Handler(Looper.getMainLooper()).post(() -> cb.onResult(lat, lon, a, s));
        }, "geo-resolve").start();
    }

    private static String amapReverse(String key, double lat, double lon) {
        try {
            double[] gcj = CoordTransform.wgs84ToGcj02(lat, lon);
            String url = "https://restapi.amap.com/v3/geocode/regeo?key="
                    + URLEncoder.encode(key, "UTF-8")
                    + "&location=" + String.format(Locale.US, "%.6f,%.6f", gcj[1], gcj[0])
                    + "&extensions=base&radius=200";
            String json = Net.get(url, 5000);
            if (json == null) return null;
            org.json.JSONObject o = new org.json.JSONObject(json);
            if (!"1".equals(o.optString("status"))) return null;
            org.json.JSONObject re = o.optJSONObject("regeocode");
            if (re == null) return null;
            String fa = re.optString("formatted_address", "");
            if (fa != null && fa.length() > 0 && !"[]".equals(fa)) return fa;
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String systemReverse(Context ctx, double lat, double lon) {
        try {
            if (!Geocoder.isPresent()) return null;
            Geocoder g = new Geocoder(ctx, Locale.CHINA);
            List<Address> list = g.getFromLocation(lat, lon, 1);
            if (list == null || list.isEmpty()) return null;
            Address a = list.get(0);
            StringBuilder sb = new StringBuilder();
            append(sb, a.getAdminArea());
            append(sb, a.getLocality());
            append(sb, a.getSubLocality());
            append(sb, a.getThoroughfare());
            append(sb, a.getSubThoroughfare());
            String s = sb.toString().trim();
            if (s.isEmpty()) {
                String line = a.getAddressLine(0);
                return (line != null && !line.isEmpty()) ? line : null;
            }
            return s;
        } catch (Exception e) {
            return null;
        }
    }

    private static void append(StringBuilder sb, String part) {
        if (part == null) return;
        String p = part.trim();
        if (p.isEmpty()) return;
        if (sb.length() > 0 && (sb.toString().endsWith(p) || sb.toString().contains(p))) return;
        sb.append(p);
    }

    /** 极简 HTTP GET（仅用于定位/天气等公开接口） */
    public static final class Net {
        private Net() {}

        public static String get(String urlStr, int timeoutMs) {
            HttpURLConnection conn = null;
            try {
                URL url = new URL(urlStr);
                conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(timeoutMs);
                conn.setReadTimeout(timeoutMs);
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "WatermarkCam/1.0 (Android)");
                conn.setRequestProperty("Accept", "application/json,text/plain,*/*");
                int code = conn.getResponseCode();
                InputStream in = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
                if (in == null) return null;
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
                in.close();
                return new String(bos.toByteArray(), "UTF-8");
            } catch (Exception e) {
                return null;
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
    }
}
