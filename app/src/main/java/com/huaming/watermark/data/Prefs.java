package com.huaming.watermark.data;

import android.content.Context;
import android.content.SharedPreferences;

public class Prefs {

    private static final String FILE = "wmc_prefs";

    private static final String K_TITLE = "title";
    private static final String K_PERSON = "person";
    private static final String K_PROJECT = "project";
    private static final String K_REMARK = "remark";
    private static final String K_SECONDS = "seconds";
    private static final String K_LOCATION = "loc";
    private static final String K_COORD = "coord";
    private static final String K_WEATHER = "weather";
    private static final String K_REMARK_ON = "remark_on";
    private static final String K_PERSON_ON = "person_on";
    private static final String K_PROJECT_ON = "project_on";
    private static final String K_TEMPLATE = "template";
    private static final String K_POSITION = "position";
    private static final String K_SCALE = "scale";
    private static final String K_OPACITY = "opacity";
    private static final String K_FULL = "full";
    private static final String K_LOGO = "logo";
    private static final String K_MANUAL_LOC = "manual_loc";
    private static final String K_LOC_MODE = "loc_mode";
    private static final String K_WEATHER_ON = "weather_on";
    private static final String K_AMAP = "amap_key";
    private static final String K_MAX_SIDE = "max_side";
    private static final String K_ORIGINAL = "save_original";
    private static final String K_GRID = "grid";
    private static final String K_FRONT_MIRROR = "front_mirror";
    private static final String K_PLACE_LAT = "place_lat";
    private static final String K_PLACE_LON = "place_lon";
    private static final String K_LOGO_VER = "logo_ver";
    private static final String K_LOGO_CUT_BG = "logo_cut_bg";
    private static final String K_LOGO_TRIM = "logo_trim";
    private static final String K_TIME_MODE = "time_mode";
    private static final String K_FIXED_TIME = "fixed_time";
    private static final String K_STAMP_ON = "stamp_on";
    private static final String K_STAMP_TEXT = "stamp_text";
    private static final String K_STAMP_OPACITY = "stamp_opacity";
    private static final String K_STAMP_SIZE = "stamp_size";
    private static final String K_STAMP_ANGLE = "stamp_angle";
    private static final String K_STAMP_TILED = "stamp_tiled";
    private static final String K_STAMP_COLOR = "stamp_color";
    private static final String K_WEATHER_MANUAL = "weather_manual";

    public static final int LOC_GPS = 0;
    public static final int LOC_MANUAL = 1;

    /** 水印时间取当前拍照时刻 */
    public static final int TIME_AUTO = 0;
    /** 水印时间取用户指定的固定时刻 */
    public static final int TIME_FIXED = 1;

    private final SharedPreferences sp;

    public Prefs(Context ctx) {
        sp = ctx.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public SharedPreferences raw() {
        return sp;
    }

    public String title() { return sp.getString(K_TITLE, "现场取证"); }
    public void setTitle(String v) { sp.edit().putString(K_TITLE, v).apply(); }

    public String person() { return sp.getString(K_PERSON, ""); }
    public void setPerson(String v) { sp.edit().putString(K_PERSON, v).apply(); }

    public String project() { return sp.getString(K_PROJECT, ""); }
    public void setProject(String v) { sp.edit().putString(K_PROJECT, v).apply(); }

    public String remark() { return sp.getString(K_REMARK, ""); }
    public void setRemark(String v) { sp.edit().putString(K_REMARK, v).apply(); }

    public boolean seconds() { return sp.getBoolean(K_SECONDS, true); }
    public void setSeconds(boolean v) { sp.edit().putBoolean(K_SECONDS, v).apply(); }

    public boolean showLocation() { return sp.getBoolean(K_LOCATION, true); }
    public void setShowLocation(boolean v) { sp.edit().putBoolean(K_LOCATION, v).apply(); }

    public boolean showCoord() { return sp.getBoolean(K_COORD, true); }
    public void setShowCoord(boolean v) { sp.edit().putBoolean(K_COORD, v).apply(); }

    public boolean showWeather() { return sp.getBoolean(K_WEATHER, false); }
    public void setShowWeather(boolean v) { sp.edit().putBoolean(K_WEATHER, v).apply(); }

    /**
     * 手动填写的天气文本；非空时优先于联网获取的结果显示。
     * 清空后恢复「自动获取」（需在设置里开启联网天气）。
     */
    public String manualWeather() { return sp.getString(K_WEATHER_MANUAL, ""); }
    public void setManualWeather(String v) {
        sp.edit().putString(K_WEATHER_MANUAL, v == null ? "" : v.trim()).apply();
    }

    public boolean showRemark() { return sp.getBoolean(K_REMARK_ON, true); }
    public void setShowRemark(boolean v) { sp.edit().putBoolean(K_REMARK_ON, v).apply(); }

    public boolean showPerson() { return sp.getBoolean(K_PERSON_ON, true); }
    public void setShowPerson(boolean v) { sp.edit().putBoolean(K_PERSON_ON, v).apply(); }

    public boolean showProject() { return sp.getBoolean(K_PROJECT_ON, true); }
    public void setShowProject(boolean v) { sp.edit().putBoolean(K_PROJECT_ON, v).apply(); }

    public int template() { return sp.getInt(K_TEMPLATE, 0); }
    public void setTemplate(int v) { sp.edit().putInt(K_TEMPLATE, v).apply(); }

    public int position() { return sp.getInt(K_POSITION, 0); }
    public void setPosition(int v) { sp.edit().putInt(K_POSITION, v).apply(); }

    public float scale() { return sp.getFloat(K_SCALE, 1.0f); }
    public void setScale(float v) {
        if (v < 0.5f) v = 0.5f;
        if (v > 2.0f) v = 2.0f;
        sp.edit().putFloat(K_SCALE, v).apply();
    }

    public float opacity() { return sp.getFloat(K_OPACITY, 1.0f); }
    public void setOpacity(float v) { sp.edit().putFloat(K_OPACITY, v).apply(); }

    public boolean fullWidth() { return sp.getBoolean(K_FULL, true); }
    public void setFullWidth(boolean v) { sp.edit().putBoolean(K_FULL, v).apply(); }

    public String logoId() { return sp.getString(K_LOGO, LogoStore.NONE); }
    public void setLogoId(String v) { sp.edit().putString(K_LOGO, v).apply(); }

    public String manualLocation() { return sp.getString(K_MANUAL_LOC, ""); }
    public void setManualLocation(String v) { sp.edit().putString(K_MANUAL_LOC, v).apply(); }

    public int locMode() { return sp.getInt(K_LOC_MODE, LOC_GPS); }
    public void setLocMode(int v) { sp.edit().putInt(K_LOC_MODE, v).apply(); }

    public boolean weatherOnline() { return sp.getBoolean(K_WEATHER_ON, false); }
    public void setWeatherOnline(boolean v) { sp.edit().putBoolean(K_WEATHER_ON, v).apply(); }

    /**
     * 内置的默认高德 Web服务 Key（本机申请，日配额 5000 次）。
     * 用户可在「设置」里改成自己的；一旦改过就以用户填的为准，
     * 填成空串也视为「用户主动清空」，此时会退回内置 Key 以便仍能搜索。
     */
    private static final String DEFAULT_AMAP_KEY = "2db40c1728b42402b48240afc4c35a31";

    public String amapKey() {
        String v = sp.getString(K_AMAP, "").trim();
        return v.isEmpty() ? DEFAULT_AMAP_KEY : v;
    }

    /** 用户自定义的 Key，未设置时为空串（用于设置页回显） */
    public String customAmapKey() { return sp.getString(K_AMAP, "").trim(); }

    public void setAmapKey(String v) { sp.edit().putString(K_AMAP, v == null ? "" : v.trim()).apply(); }

    public int maxSide() { return sp.getInt(K_MAX_SIDE, 2560); }
    public void setMaxSide(int v) { sp.edit().putInt(K_MAX_SIDE, v).apply(); }

    public boolean saveOriginal() { return sp.getBoolean(K_ORIGINAL, false); }
    public void setSaveOriginal(boolean v) { sp.edit().putBoolean(K_ORIGINAL, v).apply(); }

    public boolean grid() { return sp.getBoolean(K_GRID, false); }
    public void setGrid(boolean v) { sp.edit().putBoolean(K_GRID, v).apply(); }

    public boolean frontMirror() { return sp.getBoolean(K_FRONT_MIRROR, true); }
    public void setFrontMirror(boolean v) { sp.edit().putBoolean(K_FRONT_MIRROR, v).apply(); }

    // ---------- 自定义地点（地点名与经纬度联动） ----------
    public double placeLat() { return Double.longBitsToDouble(sp.getLong(K_PLACE_LAT, 0L)); }
    public double placeLon() { return Double.longBitsToDouble(sp.getLong(K_PLACE_LON, 0L)); }

    /** 一次性写入地点名与坐标，并切到自定义地点模式 */
    public void setPlace(String name, double lat, double lon) {
        sp.edit()
                .putString(K_MANUAL_LOC, name)
                .putLong(K_PLACE_LAT, Double.doubleToRawLongBits(lat))
                .putLong(K_PLACE_LON, Double.doubleToRawLongBits(lon))
                .putInt(K_LOC_MODE, LOC_MANUAL)
                .apply();
    }

    /** 回到自动定位 */
    public void clearPlace() {
        sp.edit()
                .putInt(K_LOC_MODE, LOC_GPS)
                .apply();
    }

    public boolean customPlace() { return sp.getInt(K_LOC_MODE, LOC_GPS) == LOC_MANUAL; }

    public boolean hasPlaceCoord() { return placeLat() != 0d || placeLon() != 0d; }

    // ---------- Logo 导入偏好 ----------
    public int logoVer() { return sp.getInt(K_LOGO_VER, 0); }
    public void setLogoVer(int v) { sp.edit().putInt(K_LOGO_VER, v).apply(); }

    /** 导入时是否自动去掉白底/纯色底 */
    public boolean logoCutBg() { return sp.getBoolean(K_LOGO_CUT_BG, true); }
    public void setLogoCutBg(boolean v) { sp.edit().putBoolean(K_LOGO_CUT_BG, v).apply(); }

    /** 导入时是否自动裁掉四周空白边 */
    public boolean logoTrim() { return sp.getBoolean(K_LOGO_TRIM, true); }
    public void setLogoTrim(boolean v) { sp.edit().putBoolean(K_LOGO_TRIM, v).apply(); }

    // ---------- 水印时间（自动 / 固定指定） ----------
    public int timeMode() { return sp.getInt(K_TIME_MODE, TIME_AUTO); }
    public void setTimeMode(int v) { sp.edit().putInt(K_TIME_MODE, v).apply(); }

    /** 固定模式下水印显示的时刻（毫秒） */
    public long fixedTime() { return sp.getLong(K_FIXED_TIME, 0L); }
    public void setFixedTime(long v) { sp.edit().putLong(K_FIXED_TIME, v).apply(); }

    /** 水印上实际显示的时间：固定模式用指定时刻，否则用当前时刻 */
    public long effectiveTime(long shotAt) {
        if (timeMode() == TIME_FIXED && fixedTime() > 0L) return fixedTime();
        return shotAt;
    }

    // ---------- 照片淡文字水印 ----------
    public boolean stampOn() { return sp.getBoolean(K_STAMP_ON, false); }
    public void setStampOn(boolean v) { sp.edit().putBoolean(K_STAMP_ON, v).apply(); }

    public String stampText() { return sp.getString(K_STAMP_TEXT, ""); }
    public void setStampText(String v) { sp.edit().putString(K_STAMP_TEXT, v == null ? "" : v).apply(); }

    /** 不透明度 0.02 ~ 0.5 */
    public float stampOpacity() { return sp.getFloat(K_STAMP_OPACITY, 0.10f); }
    public void setStampOpacity(float v) { sp.edit().putFloat(K_STAMP_OPACITY, v).apply(); }

    /** 字号占画面宽度的比例 0.01 ~ 0.10 */
    public float stampSize() { return sp.getFloat(K_STAMP_SIZE, 0.045f); }
    public void setStampSize(float v) { sp.edit().putFloat(K_STAMP_SIZE, v).apply(); }

    /** 倾斜角度 -90 ~ 90 */
    public float stampAngle() { return sp.getFloat(K_STAMP_ANGLE, -30f); }
    public void setStampAngle(float v) { sp.edit().putFloat(K_STAMP_ANGLE, v).apply(); }

    /** true = 平铺满屏；false = 单条居中 */
    public boolean stampTiled() { return sp.getBoolean(K_STAMP_TILED, true); }
    public void setStampTiled(boolean v) { sp.edit().putBoolean(K_STAMP_TILED, v).apply(); }

    /** 文字颜色 */
    public int stampColor() { return sp.getInt(K_STAMP_COLOR, 0xFFFFFFFF); }
    public void setStampColor(int v) { sp.edit().putInt(K_STAMP_COLOR, v).apply(); }
}
