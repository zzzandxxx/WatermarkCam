package com.huaming.watermark.util;

import android.content.Context;
import android.text.TextUtils;

import com.huaming.watermark.data.Prefs;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * 高德 POI 正向搜索：输入单位名 / 建筑名，返回带精确经纬度的候选地点。
 *
 * 用 Web 服务 API 的「关键字搜索」(/v3/place/text)：
 * 不限定城市、按全国相关性排序，所以「中国电信广州分公司」「华明机电」这类
 * 具体单位名也能直接命中，而不用先选城市再选街道。
 *
 * 高德返回的是 GCJ-02 坐标，本应用内部统一用 WGS84（与手机 GPS 一致），
 * 因此入库前要反算一次，否则同一地点在「自动定位」和「手动选点」下水印坐标会差几百米。
 *
 * 必须在子线程调用（内部是阻塞 HTTP）。
 */
public final class AmapPoi {

    private static final String ENDPOINT = "https://restapi.amap.com/v3/place/text";

    /** 一条候选地点 */
    public static final class Poi {
        public String name = "";
        public String address = "";
        public String district = "";
        public String city = "";
        public String province = "";
        public String type = "";
        /** WGS84 */
        public double lat;
        public double lon;

        /** 行政区划 + 详细地址，用于列表第二行 */
        public String where() {
            StringBuilder sb = new StringBuilder();
            appendPart(sb, province);
            appendPart(sb, city);
            appendPart(sb, district);
            String head = sb.toString();
            if (!TextUtils.isEmpty(address)) {
                if (!TextUtils.isEmpty(head) && !address.startsWith(head)) head += " ";
                head += address;
            }
            return head;
        }

        private static void appendPart(StringBuilder sb, String p) {
            if (TextUtils.isEmpty(p)) return;
            String v = p.trim();
            if (v.isEmpty() || sb.indexOf(v) >= 0) return;
            sb.append(v);
        }
    }

    /** 搜索结果的三种状态：成功 / 出错 / 没配 Key */
    public static final class Result {
        public final List<Poi> list = new ArrayList<>();
        public String error;
        public boolean noKey;

        public boolean ok() {
            return error == null;
        }
    }

    private AmapPoi() {}

    public static String key(Context ctx) {
        return new Prefs(ctx).amapKey().trim();
    }

    public static boolean hasKey(Context ctx) {
        return !key(ctx).isEmpty();
    }

    /**
     * 按关键字搜索 POI。
     *
     * @param keyword 单位名、楼宇名、地址片段
     * @param limit   最多返回几条（高德单页上限 25）
     */
    public static Result search(Context ctx, String keyword, int limit) {
        Result r = new Result();
        String kw = keyword == null ? "" : keyword.trim();
        if (kw.isEmpty()) {
            r.error = "请输入要搜索的单位或地点名称";
            return r;
        }
        String k = key(ctx);
        if (k.isEmpty()) {
            r.noKey = true;
            r.error = "未设置高德 Key";
            return r;
        }

        int offset = Math.max(1, Math.min(limit <= 0 ? 25 : limit, 25));
        StringBuilder url = new StringBuilder(ENDPOINT)
                .append("?key=").append(enc(k))
                .append("&keywords=").append(enc(kw))
                .append("&offset=").append(offset)
                .append("&page=1")
                .append("&extensions=base")
                .append("&citylimit=false");

        String json = LocationHelper.Net.get(url.toString(), 8000);
        if (json == null) {
            r.error = "网络请求失败，请检查网络连接";
            return r;
        }

        try {
            JSONObject o = new JSONObject(json);
            String status = o.optString("status", "0");
            if (!"1".equals(status)) {
                r.error = mapError(o.optString("infocode", ""), o.optString("info", ""));
                return r;
            }
            JSONArray arr = o.optJSONArray("pois");
            if (arr == null) return r;

            for (int i = 0; i < arr.length(); i++) {
                JSONObject p = arr.optJSONObject(i);
                if (p == null) continue;
                Poi poi = parsePoi(p);
                if (poi != null) r.list.add(poi);
            }
            if (r.list.isEmpty()) r.error = "没有找到「" + kw + "」，换个写法试试（例如加上城市名）";
            return r;
        } catch (Exception e) {
            r.error = "返回数据解析失败";
            return r;
        }
    }

    /** 解析单条 POI；坐标无效时返回 null */
    private static Poi parsePoi(JSONObject p) {
        String loc = str(p, "location");
        if (TextUtils.isEmpty(loc)) return null;
        int comma = loc.indexOf(',');
        if (comma <= 0) return null;
        double gcjLon, gcjLat;
        try {
            gcjLon = Double.parseDouble(loc.substring(0, comma).trim());
            gcjLat = Double.parseDouble(loc.substring(comma + 1).trim());
        } catch (Exception e) {
            return null;
        }
        if (Math.abs(gcjLat) < 1e-9 && Math.abs(gcjLon) < 1e-9) return null;

        double[] wgs = CoordTransform.gcj02ToWgs84(gcjLat, gcjLon);

        Poi poi = new Poi();
        poi.name = str(p, "name");
        if (TextUtils.isEmpty(poi.name)) return null;
        poi.address = str(p, "address");
        poi.district = str(p, "adname");
        poi.city = str(p, "cityname");
        poi.province = str(p, "pname");
        poi.type = str(p, "type");
        poi.lat = wgs[0];
        poi.lon = wgs[1];

        // 直辖市的 cityname 可能为空，用 province 兜底展示
        if (TextUtils.isEmpty(poi.city)) poi.city = poi.province;
        return poi;
    }

    /**
     * 高德把缺失的字符串字段返回成空数组 []，直接 optString 会拿到 "[]"，
     * 这里统一洗掉，避免界面上出现方括号。
     */
    private static String str(JSONObject p, String name) {
        if (!p.has(name)) return "";
        Object v = p.opt(name);
        if (v == null || v == JSONObject.NULL) return "";
        String s = String.valueOf(v).trim();
        if (s.isEmpty() || "[]".equals(s) || "null".equals(s)) return "";
        return s;
    }

    /** 把高德的错误码翻译成人能看懂的话 */
    private static String mapError(String code, String info) {
        switch (code) {
            case "10001":
                return "Key 无效。请到高德控制台复制「Web服务」类型的 Key（不是 Android 平台的）";
            case "10002":
                return "Key 无权限访问该接口，请确认 Key 类型是「Web服务」";
            case "10003":
                return "今日调用量已用完（个人版每日 5000 次），明天再试或换个 Key";
            case "10004":
                return "请求太频繁，稍等几秒再搜";
            case "10005":
                return "Key 绑定的 IP 白名单不允许本机访问，请把 IP 白名单清空";
            case "10009":
                return "Key 与平台不匹配。请重新创建 Key，服务平台必须选「Web服务」";
            case "10044":
                return "该 Key 的日调用量或并发量已达上限";
            case "20000":
                return "请求参数有误";
            default:
                return TextUtils.isEmpty(info) ? "搜索失败（" + code + "）" : info + "（" + code + "）";
        }
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }
}
