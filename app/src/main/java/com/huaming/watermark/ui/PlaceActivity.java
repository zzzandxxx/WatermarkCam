package com.huaming.watermark.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.huaming.watermark.R;
import com.huaming.watermark.data.PlaceDb;
import com.huaming.watermark.data.PlaceStore;
import com.huaming.watermark.data.Prefs;
import com.huaming.watermark.util.AmapPoi;
import com.huaming.watermark.util.LocationHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 地点与坐标：选一个地点，水印上的地点名和经纬度同时改掉。
 *
 * 两种检索方式并行：
 * 1. 在线（需高德 Key）——输入单位名/楼宇名，全国范围模糊匹配，返回精确到建筑的点位；
 * 2. 离线城市库 —— 339 个地级市，没网或没 Key 时兜底。
 */
public class PlaceActivity extends AppCompatActivity {

    /** 输入停顿多久后发请求，避免每敲一个字就打一次接口 */
    private static final long DEBOUNCE_MS = 420L;
    private static final int POI_LIMIT = 20;

    private static final String[] HOT = {
            "北京市", "上海市", "广州市", "深圳市", "杭州市", "成都市", "武汉市", "西安市",
            "南京市", "重庆市", "天津市", "苏州市", "长沙市", "郑州市", "青岛市", "沈阳市"
    };

    private Prefs prefs;
    private EditText etSearch;
    private ImageView btnClear;
    private TextView tvKeyHint;
    private Adapter adapter;

    private final List<Row> rows = new ArrayList<>();
    private final List<AmapPoi.Poi> poiHits = new ArrayList<>();

    private final Handler ui = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;
    private int searchSeq;
    private boolean searching;
    private String poiError;

    private static class Row {
        static final int HEADER = 0, SAVED = 1, DB = 2, POI = 3, FOOTER = 4;

        int type;
        String name;
        String addr;
        double lat, lon;
        String header;

        static Row header(String t) {
            Row r = new Row();
            r.type = HEADER;
            r.header = t;
            return r;
        }

        /** 列表末尾的说明性提示，样式与分组标题一致但可换行、颜色更淡 */
        static Row footer(String t) {
            Row r = new Row();
            r.type = FOOTER;
            r.header = t;
            return r;
        }

        static Row saved(PlaceStore.Place p) {
            Row r = new Row();
            r.type = SAVED;
            r.name = p.name;
            r.lat = p.lat;
            r.lon = p.lon;
            return r;
        }

        static Row db(PlaceDb.Entry e) {
            Row r = new Row();
            r.type = DB;
            r.name = e.name;
            r.lat = e.lat;
            r.lon = e.lon;
            return r;
        }

        static Row poi(AmapPoi.Poi p) {
            Row r = new Row();
            r.type = POI;
            r.name = p.name;
            r.addr = p.where();
            r.lat = p.lat;
            r.lon = p.lon;
            return r;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_place);
        prefs = new Prefs(this);

        etSearch = findViewById(R.id.etSearch);
        btnClear = findViewById(R.id.btnClear);
        tvKeyHint = findViewById(R.id.tvKeyHint);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnAuto).setOnClickListener(v -> {
            prefs.clearPlace();
            toast("已切回自动定位");
            finish();
        });
        findViewById(R.id.btnCurrent).setOnClickListener(v -> useCurrentLocation());
        findViewById(R.id.btnManual).setOnClickListener(v -> editDialog("", "", 0, 0, false));

        tvKeyHint.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new Adapter();
        list.setAdapter(adapter);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) { }

            @Override
            public void afterTextChanged(Editable s) {
                btnClear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                onQueryChanged(s.toString().trim());
            }
        });
        // 键盘上点「搜索」时不等防抖，立刻发请求
        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                String kw = etSearch.getText().toString().trim();
                cancelPending();
                if (!kw.isEmpty()) {
                    searching = AmapPoi.hasKey(this);
                    rebuild();
                    if (searching) runSearch(kw, searchSeq);
                }
                return true;
            }
            return false;
        });
        btnClear.setOnClickListener(v -> etSearch.setText(""));

        rebuild();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshKeyHint();
        // 从设置页填完 Key 回来：把当前关键词重新搜一次
        String kw = etSearch.getText().toString().trim();
        if (!kw.isEmpty() && poiHits.isEmpty() && !searching && AmapPoi.hasKey(this)) {
            onQueryChanged(kw);
        }
    }

    @Override
    protected void onPause() {
        cancelPending();
        super.onPause();
    }

    private void refreshKeyHint() {
        boolean needKey = !AmapPoi.hasKey(this) && etSearch.getText().toString().trim().isEmpty();
        tvKeyHint.setVisibility(needKey ? View.VISIBLE : View.GONE);
    }

    // ============================================================
    // 检索
    // ============================================================
    private void cancelPending() {
        if (pendingSearch != null) {
            ui.removeCallbacks(pendingSearch);
            pendingSearch = null;
        }
    }

    private void onQueryChanged(String kw) {
        cancelPending();
        searchSeq++;
        poiHits.clear();
        poiError = null;
        searching = !kw.isEmpty() && AmapPoi.hasKey(this);
        rebuild();
        refreshKeyHint();
        if (!searching) return;

        final int seq = searchSeq;
        pendingSearch = () -> runSearch(kw, seq);
        ui.postDelayed(pendingSearch, DEBOUNCE_MS);
    }

    /** 后台线程查高德，回来后在主线程更新列表 */
    private void runSearch(final String kw, final int seq) {
        searching = true;
        rebuild();
        new Thread(() -> {
            final AmapPoi.Result r = AmapPoi.search(PlaceActivity.this, kw, POI_LIMIT);
            ui.post(() -> {
                if (seq != searchSeq) return;   // 用户已经改了关键词，丢弃过期结果
                searching = false;
                poiHits.clear();
                if (r.ok()) {
                    poiHits.addAll(r.list);
                    poiError = null;
                } else {
                    poiError = r.noKey ? null : r.error;
                }
                rebuild();
            });
        }, "poi-search").start();
    }

    // ============================================================
    // 列表
    // ============================================================
    private void rebuild() {
        rows.clear();
        String kw = etSearch.getText().toString().trim();

        if (kw.isEmpty()) {
            List<PlaceStore.Place> saved = PlaceStore.load(this);
            if (!saved.isEmpty()) {
                rows.add(Row.header("我的地点"));
                for (PlaceStore.Place p : saved) rows.add(Row.saved(p));
            }
            rows.add(Row.header("常用城市"));
            for (String city : HOT) {
                List<PlaceDb.Entry> hit = PlaceDb.search(this, city, 1);
                if (!hit.isEmpty()) rows.add(Row.db(hit.get(0)));
            }
            adapter.notifyDataSetChanged();
            return;
        }

        // ---- 在线结果（单位 / 楼宇）----
        if (!AmapPoi.hasKey(this)) {
            rows.add(Row.header("未设置高德 Key，只能匹配城市名"));
        } else if (searching) {
            rows.add(Row.header("正在搜索「" + kw + "」…"));
        } else if (poiError != null) {
            rows.add(Row.header(poiError));
        } else if (!poiHits.isEmpty()) {
            rows.add(Row.header("单位 / 地点（高德）"));
            for (AmapPoi.Poi p : poiHits) rows.add(Row.poi(p));
            // 高德对小单位只做模糊匹配（「华明机电」会返回一堆天津带「华明」字样的公司），
            // 全都对不上时明确提示，避免选成外地坐标还以为选对了
            if (!containsExact(poiHits, kw)) {
                rows.add(Row.footer("以上都是名称相近的模糊匹配，没有找到叫「" + kw
                        + "」的地点。若是小公司 / 项目部 / 工地，高德通常没收录："
                        + "到现场点「用当前位置」自动采点，或点「自定义坐标」手填。"));
            }
        }

        // ---- 离线城市库 ----
        List<PlaceDb.Entry> cityHits = PlaceDb.search(this, kw, 30);
        if (!cityHits.isEmpty()) {
            rows.add(Row.header("城市"));
            for (PlaceDb.Entry e : cityHits) rows.add(Row.db(e));
        }

        // ---- 我的地点里也搜一遍，方便复用以前存过的单位 ----
        List<PlaceStore.Place> savedHits = new ArrayList<>();
        for (PlaceStore.Place p : PlaceStore.load(this)) {
            if (p.name != null && p.name.contains(kw)) savedHits.add(p);
        }
        if (!savedHits.isEmpty()) {
            rows.add(Row.header("我的地点"));
            for (PlaceStore.Place p : savedHits) rows.add(Row.saved(p));
        }

        if (rows.isEmpty() && !searching && poiError == null) {
            rows.add(Row.header("没有匹配结果，可点上方「自定义坐标」手填"));
        }
        adapter.notifyDataSetChanged();
    }

    /**
     * 判断结果里有没有「真的叫这个名字」的条目。
     * 只比名称，不看地址——地址里出现关键词（高德会把「华明镇」附近的公司都带出来）不算命中。
     */
    private static boolean containsExact(List<AmapPoi.Poi> list, String kw) {
        String q = kw.replace(" ", "");
        if (q.length() < 2) return true;
        for (AmapPoi.Poi p : list) {
            if (p.name == null) continue;
            String n = p.name.replace(" ", "");
            if (n.contains(q)) return true;
            // 反过来：结果名本身较短且被关键词完整包含（如搜「华明机电 3 号车间」命中「华明机电」）
            if (n.length() >= 4 && q.contains(n)) return true;
        }
        return false;
    }

    // ============================================================
    // 应用地点
    // ============================================================
    private void applyPlace(String name, double lat, double lon) {
        prefs.setPlace(name, lat, lon);
        PlaceStore.upsert(this, new PlaceStore.Place(name, lat, lon));
        toast("地点已设为：" + name);
        finish();
    }

    /**
     * 编辑地点名 + 经纬度。
     *
     * @param origName  编辑已保存地点时传原名称，改名后旧条目会被移除
     * @param lockCoord true 表示坐标是已经确定的精确点位（来自在线搜索或 GPS 采点），
     *                  改名字时不再被城市库覆盖；清空两个坐标框即可解除锁定
     */
    private void editDialog(final String origName, final String initName,
                            double initLat, double initLon, final boolean lockCoord) {
        int pad = dp(20);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, dp(6), pad, 0);

        final EditText etName = field(box, "地点名称", initName, InputType.TYPE_CLASS_TEXT,
                "例如：中国电信广州分公司 / 华明机电 3 号车间");

        final TextView tvHint = new TextView(this);
        tvHint.setTextColor(0xFF5F6B7C);
        tvHint.setTextSize(11f);
        tvHint.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = dp(6);
        box.addView(tvHint, hlp);

        final EditText etLat = field(box, "纬度（-90 ~ 90）", initLat == 0d ? "" : fmt(initLat),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED, "39.90420");
        final EditText etLon = field(box, "经度（-180 ~ 180）", initLon == 0d ? "" : fmt(initLon),
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED, "116.40740");

        // 手动改过坐标就不再被名称匹配覆盖；两个框都清空则恢复自动匹配
        final boolean[] touched = {lockCoord};
        final boolean[] syncing = {false};
        TextWatcher coordWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) { }

            @Override
            public void afterTextChanged(Editable s) {
                if (syncing[0]) return;
                boolean empty = etLat.getText().toString().trim().isEmpty()
                        && etLon.getText().toString().trim().isEmpty();
                touched[0] = !empty;
                if (empty) matchByName(etName.getText().toString().trim(), etLat, etLon, tvHint, syncing, touched);
            }
        };
        etLat.addTextChangedListener(coordWatcher);
        etLon.addTextChangedListener(coordWatcher);

        etName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) { }

            @Override
            public void afterTextChanged(Editable s) {
                if (syncing[0]) return;
                matchByName(s.toString().trim(), etLat, etLon, tvHint, syncing, touched);
            }
        });

        if (lockCoord) {
            tvHint.setTextColor(0xFF22C55E);
            tvHint.setText("已带入该地点的精确坐标，可再微调；改名字不会覆盖坐标（清空两个坐标框可改为按城市匹配）");
        } else {
            tvHint.setText("输入城市名（如 杭州市、浦东新区）会自动带出经纬度");
        }

        AlertDialog dlg = new AlertDialog.Builder(this)
                .setTitle("地点与坐标")
                .setView(box)
                .setPositiveButton("确定", null)
                .setNegativeButton("取消", null)
                .create();
        dlg.show();
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            if (name.isEmpty()) {
                toast("请填写地点名称");
                return;
            }
            double la, lo;
            try {
                la = parse(etLat.getText().toString(), Double.NaN);
                lo = parse(etLon.getText().toString(), Double.NaN);
            } catch (Exception e) {
                toast("坐标格式不正确");
                return;
            }
            if (Double.isNaN(la) || Double.isNaN(lo)) {
                toast("请填写纬度和经度");
                return;
            }
            if (la < -90 || la > 90) {
                toast("纬度超出范围");
                return;
            }
            if (lo < -180 || lo > 180) {
                toast("经度超出范围");
                return;
            }
            dlg.dismiss();
            if (origName != null && !origName.isEmpty() && !origName.equals(name)) {
                // 改了名字：删掉旧条目，避免列表里留下两个几乎一样的地点
                PlaceStore.remove(this, origName);
            }
            applyPlace(name, la, lo);
        });
    }

    /** 按名称匹配城市库坐标；坐标未被手动修改时直接写入 */
    private void matchByName(String name, EditText etLat, EditText etLon,
                             TextView tvHint, boolean[] syncing, boolean[] touched) {
        if (name.length() < 2) {
            tvHint.setTextColor(0xFF5F6B7C);
            tvHint.setText("输入城市名（如 杭州市、浦东新区）会自动带出经纬度");
            return;
        }
        List<PlaceDb.Entry> hits = PlaceDb.search(this, name, 1);
        PlaceDb.Entry hit = hits.isEmpty() ? null : hits.get(0);
        if (hit == null) {
            tvHint.setTextColor(0xFF5F6B7C);
            tvHint.setText("未匹配到城市坐标，请手动填写经纬度");
            return;
        }
        if (touched[0]) {
            tvHint.setTextColor(0xFFFFC400);
            tvHint.setText("已匹配「" + hit.name + "」，但坐标是手填的，已保留手填值（清空两个坐标框可重新匹配）");
            return;
        }
        syncing[0] = true;
        etLat.setText(fmt(hit.lat));
        etLon.setText(fmt(hit.lon));
        syncing[0] = false;
        tvHint.setTextColor(0xFF22C55E);
        tvHint.setText("已按「" + hit.name + "」自动带入坐标，可再手动微调");
    }

    private EditText field(LinearLayout parent, String label, String value, int inputType, String hint) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(0xFF98A4B5);
        tv.setTextSize(11.5f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        parent.addView(tv, lp);

        EditText et = new EditText(this);
        et.setText(value);
        et.setHint(hint);
        et.setInputType(inputType);
        et.setSingleLine(true);
        et.setTextColor(0xFFF2F5F9);
        et.setHintTextColor(0xFF5F6B7C);
        et.setTextSize(14.5f);
        et.setSelection(et.getText().length());
        parent.addView(et, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return et;
    }

    /** 用 GPS 采一个点，命名后存为常用地点 */
    private void useCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            toast("没有定位权限，可在设置里授予，或用「自定义坐标」");
            return;
        }
        toast("正在定位…");
        LocationHelper.request(this, new LocationHelper.Callback() {
            @Override
            public void onResult(double la, double lo, String addr, String source) {
                String init = (addr == null || addr.isEmpty()) ? "我的位置" : addr;
                editDialog("", init, la, lo, true);
            }

            @Override
            public void onError(String message) {
                toast("定位失败：" + message + "，可改用「自定义坐标」");
            }
        }, 12000L);
    }

    // ============================================================
    // 适配器
    // ============================================================
    private class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        class VH extends RecyclerView.ViewHolder {
            final TextView name, addr, coord, badge;
            final ImageView icon;

            VH(View v) {
                super(v);
                name = v.findViewById(R.id.tvName);
                addr = v.findViewById(R.id.tvAddr);
                coord = v.findViewById(R.id.tvCoord);
                badge = v.findViewById(R.id.tvBadge);
                icon = v.findViewById(R.id.imgIcon);
            }
        }

        class HVH extends RecyclerView.ViewHolder {
            final TextView tv;

            HVH(View v) {
                super(v);
                tv = v.findViewById(R.id.tvHeader);
            }
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == Row.HEADER || viewType == Row.FOOTER) {
                return new HVH(LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.item_place_header, parent, false));
            }
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_place, parent, false));
        }

        @Override
        public int getItemViewType(int position) {
            int t = rows.get(position).type;
            if (t == Row.HEADER) return Row.HEADER;
            if (t == Row.FOOTER) return Row.FOOTER;
            return Row.DB;
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            final Row r = rows.get(position);
            if (holder instanceof HVH) {
                TextView tv = ((HVH) holder).tv;
                tv.setText(r.header);
                if (r.type == Row.FOOTER) {
                    tv.setTextColor(0xFF5F6B7C);
                    tv.setTextSize(11f);
                    tv.setTypeface(null, android.graphics.Typeface.NORMAL);
                    tv.setLineSpacing(dp(3), 1f);
                } else {
                    tv.setTextColor(0xFFFFC400);
                    tv.setTextSize(11.5f);
                }
                return;
            }
            VH h = (VH) holder;
            h.name.setText(r.name);
            if (r.addr != null && !r.addr.isEmpty()) {
                h.addr.setText(r.addr);
                h.addr.setVisibility(View.VISIBLE);
            } else {
                h.addr.setVisibility(View.GONE);
            }
            h.coord.setText(String.format(Locale.US, "%.5f, %.5f", r.lat, r.lon));

            boolean inUse = prefs.customPlace()
                    && r.name.equals(prefs.manualLocation())
                    && Math.abs(r.lat - prefs.placeLat()) < 1e-6
                    && Math.abs(r.lon - prefs.placeLon()) < 1e-6;
            h.badge.setVisibility(inUse ? View.VISIBLE : View.GONE);
            h.icon.setColorFilter(r.type == Row.SAVED ? 0xFFFFC400
                    : r.type == Row.POI ? 0xFF22C55E : 0xFF5F6B7C);

            h.itemView.setOnClickListener(v -> {
                if (r.type == Row.SAVED) {
                    // 已保存的地点：单击直接切换，长按再编辑
                    applyPlace(r.name, r.lat, r.lon);
                } else if (r.type == Row.POI) {
                    // 在线搜到的单位：坐标已精确，改名字不覆盖
                    editDialog("", r.name, r.lat, r.lon, true);
                } else {
                    // 城市库条目：先确认名称与坐标，允许微调后再保存
                    editDialog("", r.name, r.lat, r.lon, false);
                }
            });
            h.itemView.setOnLongClickListener(v -> {
                if (r.type != Row.SAVED) return false;
                new AlertDialog.Builder(PlaceActivity.this)
                        .setTitle(r.name)
                        .setItems(new String[]{"编辑", "删除"}, (d, which) -> {
                            if (which == 0) {
                                editDialog(r.name, r.name, r.lat, r.lon, true);
                            } else {
                                PlaceStore.remove(PlaceActivity.this, r.name);
                                if (r.name.equals(prefs.manualLocation())) prefs.clearPlace();
                                rebuild();
                            }
                        })
                        .show();
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }
    }

    // ============================================================
    // 工具
    // ============================================================
    private static String fmt(double v) {
        return String.format(Locale.US, "%.5f", v);
    }

    private static double parse(String s, double def) {
        s = s == null ? "" : s.trim();
        if (s.isEmpty()) return def;
        return Double.parseDouble(s);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void toast(String m) {
        Toast.makeText(this, m, Toast.LENGTH_SHORT).show();
    }
}
