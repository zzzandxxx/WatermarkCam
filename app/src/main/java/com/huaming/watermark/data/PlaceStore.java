package com.huaming.watermark.data;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户自己保存的地点（名称 + 经纬度）。
 * 一个地点一旦保存，切换过去时水印里的地点名和经纬度会同时改变。
 */
public class PlaceStore {

    private static final String KEY = "saved_places";

    public static class Place {
        public String name;
        public double lat, lon;

        public Place(String name, double lat, double lon) {
            this.name = name;
            this.lat = lat;
            this.lon = lon;
        }
    }

    private static SharedPreferences sp(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences("wmc_places", Context.MODE_PRIVATE);
    }

    public static List<Place> load(Context ctx) {
        List<Place> list = new ArrayList<>();
        String raw = sp(ctx).getString(KEY, "");
        if (raw == null || raw.isEmpty()) return list;
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                list.add(new Place(o.optString("n"), o.optDouble("la", 0), o.optDouble("lo", 0)));
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    private static void save(Context ctx, List<Place> list) {
        JSONArray arr = new JSONArray();
        for (Place p : list) {
            try {
                JSONObject o = new JSONObject();
                o.put("n", p.name);
                o.put("la", p.lat);
                o.put("lo", p.lon);
                arr.put(o);
            } catch (Exception ignored) {
            }
        }
        sp(ctx).edit().putString(KEY, arr.toString()).apply();
    }

    /** 新增或覆盖同名地点，最新的排在最前 */
    public static void upsert(Context ctx, Place p) {
        List<Place> list = load(ctx);
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).name.equals(p.name)) list.remove(i);
        }
        list.add(0, p);
        while (list.size() > 40) list.remove(list.size() - 1);
        save(ctx, list);
    }

    public static void remove(Context ctx, String name) {
        List<Place> list = load(ctx);
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).name.equals(name)) list.remove(i);
        }
        save(ctx, list);
    }

    public static Place find(Context ctx, String name) {
        for (Place p : load(ctx)) if (p.name.equals(name)) return p;
        return null;
    }
}
