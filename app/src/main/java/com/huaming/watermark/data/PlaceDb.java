package com.huaming.watermark.data;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 内置地点库（省市/区县中心点）。
 *
 * 数据来源：Wikidata（CC0）中国市级行政区中心点坐标，离线打包，
 * 无需联网即可按名称搜索并把经纬度填进水印。
 * 坐标为中心点近似值，用于水印地点标注；需要精确取证时请用真实 GPS 或手动微调。
 */
public class PlaceDb {

    public static class Entry {
        public final String name;
        public final double lat, lon;

        Entry(String name, double lat, double lon) {
            this.name = name;
            this.lat = lat;
            this.lon = lon;
        }
    }

    private static List<Entry> cache;

    public static synchronized List<Entry> all(Context ctx) {
        if (cache != null) return cache;
        List<Entry> list = new ArrayList<>();
        try (InputStream in = ctx.getAssets().open("places_cn.csv");
             BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] p = line.split(",");
                if (p.length < 3) continue;
                try {
                    list.add(new Entry(p[0], Double.parseDouble(p[1]), Double.parseDouble(p[2])));
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (Exception e) {
            android.util.Log.e("PlaceDb", "load failed", e);
        }
        cache = list;
        return list;
    }

    /** 按名称模糊搜索：完全匹配 > 前缀匹配 > 包含，短名称优先 */
    public static List<Entry> search(Context ctx, String keyword, int limit) {
        List<Entry> out = new ArrayList<>();
        String q = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) return out;
        final List<Entry> src = all(ctx);
        List<Entry> exact = new ArrayList<>(), prefix = new ArrayList<>(), contains = new ArrayList<>();
        for (Entry e : src) {
            String n = e.name.toLowerCase(Locale.ROOT);
            if (n.equals(q)) exact.add(e);
            else if (n.startsWith(q)) prefix.add(e);
            else if (n.contains(q)) contains.add(e);
        }
        Comparator<Entry> byLen = new Comparator<Entry>() {
            @Override
            public int compare(Entry a, Entry b) {
                if (a.name.length() != b.name.length()) return a.name.length() - b.name.length();
                return a.name.compareTo(b.name);
            }
        };
        Collections.sort(exact, byLen);
        Collections.sort(prefix, byLen);
        Collections.sort(contains, byLen);
        out.addAll(exact);
        out.addAll(prefix);
        out.addAll(contains);
        if (out.size() > limit) return new ArrayList<>(out.subList(0, limit));
        return out;
    }
}
