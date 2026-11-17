package com.huaming.watermark.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.huaming.watermark.R;
import com.huaming.watermark.data.Prefs;
import com.huaming.watermark.data.RecordStore;
import com.huaming.watermark.util.CollageMaker;
import com.huaming.watermark.util.ImageUtils;
import com.huaming.watermark.util.TimeUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 拼图 / 汇报：
 * 拼图 = 多张打卡照片合成一张（长图 / 宫格）；
 * 汇报 = 白底长图，标题 + 时间地点 + 汇报说明 + 逐张带时间说明的照片 + 页脚。
 */
public class CollageActivity extends AppCompatActivity {

    private static final int REQ_PICK = 51;

    private Prefs prefs;
    private RecordStore store;
    private ExecutorService io;

    private final List<RecordStore.Record> records = new ArrayList<>();
    private final List<String> albumUris = new ArrayList<>();
    private final Map<String, Bitmap> thumbs = new HashMap<>();
    /** 已选照片 uri，顺序 = 拼图中的顺序 */
    private final List<String> selected = new ArrayList<>();

    private int mode = CollageMaker.MODE_COLLAGE;
    private int layout = CollageMaker.LAYOUT_LONG;

    private TextView tvSel, tvEmpty;
    private LinearLayout modeRow, layoutRow;
    private EditText etTitle, etNote;
    private RecyclerView grid;
    private ProgressBar progress;
    private PickAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_collage);

        prefs = new Prefs(this);
        store = new RecordStore(this);
        io = Executors.newSingleThreadExecutor();

        tvSel = findViewById(R.id.tvSel);
        tvEmpty = findViewById(R.id.tvEmpty);
        modeRow = findViewById(R.id.modeRow);
        layoutRow = findViewById(R.id.layoutRow);
        etTitle = findViewById(R.id.etTitle);
        etNote = findViewById(R.id.etNote);
        grid = findViewById(R.id.grid);
        progress = findViewById(R.id.progress);

        grid.setLayoutManager(new GridLayoutManager(this, 3));
        adapter = new PickAdapter();
        grid.setAdapter(adapter);

        etTitle.setText(prefs.title().isEmpty() ? "工作汇报" : prefs.title());

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnGenerate).setOnClickListener(v -> generate());

        buildModeChips();
        buildLayoutChips();
        refreshModeUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (io != null) io.shutdownNow();
    }

    // ============================================================
    // 模式 / 布局选择
    // ============================================================
    private TextView makeChip(String text, boolean on) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(13f);
        t.setGravity(android.view.Gravity.CENTER);
        t.setTextColor(on ? 0xFFFFC400 : 0xFFD6DEEA);
        t.setBackgroundResource(on ? R.drawable.bg_chip_on : R.drawable.bg_chip);
        t.setPadding(dp(16), dp(8), dp(16), dp(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(8);
        t.setLayoutParams(lp);
        t.setClickable(true);
        t.setFocusable(true);
        return t;
    }

    private void buildModeChips() {
        modeRow.removeAllViews();
        String[] names = {"拼图", "汇报"};
        for (int i = 0; i < names.length; i++) {
            final int m = i;
            TextView tv = makeChip(names[i], mode == m);
            tv.setOnClickListener(v -> {
                mode = m;
                buildModeChips();
                refreshModeUi();
            });
            modeRow.addView(tv);
        }
    }

    private void buildLayoutChips() {
        layoutRow.removeAllViews();
        String[] names = {"长图拼接", "宫格拼图"};
        for (int i = 0; i < names.length; i++) {
            final int l = i;
            TextView tv = makeChip(names[i], layout == l);
            tv.setOnClickListener(v -> {
                layout = l;
                buildLayoutChips();
            });
            layoutRow.addView(tv);
        }
    }

    private void refreshModeUi() {
        boolean report = mode == CollageMaker.MODE_REPORT;
        layoutRow.setVisibility(report ? View.GONE : View.VISIBLE);
        etTitle.setVisibility(report ? View.VISIBLE : View.GONE);
        etNote.setVisibility(report ? View.VISIBLE : View.GONE);
    }

    // ============================================================
    // 照片选择
    // ============================================================
    private void reload() {
        records.clear();
        records.addAll(store.list(500));
        adapter.notifyDataSetChanged();
        boolean has = !records.isEmpty() || !albumUris.isEmpty();
        tvEmpty.setVisibility(has ? View.GONE : View.VISIBLE);
    }

    /** 网格条目 key：记录用自身 uri，相册导入用 alb: 前缀 */
    private String keyOf(int pos) {
        if (pos < records.size()) {
            String u = records.get(pos).uri;
            return u == null ? "rec:" + pos : u;
        }
        int a = pos - records.size();
        return "alb:" + albumUris.get(a);
    }

    private long timeOf(int pos) {
        return pos < records.size() ? records.get(pos).time : 0L;
    }

    private String locOf(int pos) {
        return pos < records.size() ? records.get(pos).location : "";
    }

    private int indexOfKey(String key) {
        for (int i = 0; i < records.size() + albumUris.size(); i++) {
            if (keyOf(i).equals(key)) return i;
        }
        return -1;
    }

    private void toggleSelect(String key) {
        if (selected.contains(key)) {
            selected.remove(key);
        } else {
            selected.add(key);
        }
        updateSelHint();
        adapter.notifyDataSetChanged();
    }

    private void updateSelHint() {
        tvSel.setText(selected.isEmpty() ? "" : "已选 " + selected.size() + " 张");
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_PICK && res == RESULT_OK && data != null) {
            android.content.ClipData clip = data.getClipData();
            if (clip != null) {
                for (int i = 0; i < clip.getItemCount(); i++) {
                    Uri u = clip.getItemAt(i).getUri();
                    String s = u.toString();
                    if (!albumUris.contains(s)) albumUris.add(s);
                }
            } else if (data.getData() != null) {
                String s = data.getData().toString();
                if (!albumUris.contains(s)) albumUris.add(s);
            }
            reload();
        }
    }

    // ============================================================
    // 生成
    // ============================================================
    private void generate() {
        if (selected.isEmpty()) {
            toast("请先选择照片");
            return;
        }
        if (mode == CollageMaker.MODE_COLLAGE && selected.size() < 2) {
            toast("拼图至少选择 2 张照片");
            return;
        }
        progress.setVisibility(View.VISIBLE);

        final List<String> uris = new ArrayList<>(selected);
        final int m = mode, l = layout;
        final String title = etTitle.getText().toString().trim();
        final String note = etNote.getText().toString().trim();
        final long now = System.currentTimeMillis();

        io.execute(() -> {
            Bitmap out = null;
            String err = null;
            try {
                List<Bitmap> pics = new ArrayList<>();
                List<String> caps = new ArrayList<>();
                for (String key : uris) {
                    String uri = key.startsWith("alb:") ? key.substring(4) : key;
                    Bitmap b = ImageUtils.decodeUri(CollageActivity.this, uri, 1600);
                    if (b == null) continue;
                    pics.add(b);
                    int pos = indexOfKey(key);
                    String cap = "";
                    if (m == CollageMaker.MODE_REPORT) {
                        long t = timeOf(Math.max(0, pos));
                        String loc = locOf(Math.max(0, pos));
                        if (t > 0) cap = TimeUtils.displayDateTime(t);
                        if (loc != null && !loc.isEmpty()) cap = cap.isEmpty() ? loc : cap + " · " + loc;
                    }
                    caps.add(cap);
                }
                if (pics.size() < 2 && m == CollageMaker.MODE_COLLAGE) {
                    err = "可用照片不足 2 张";
                } else if (!pics.isEmpty()) {
                    if (m == CollageMaker.MODE_REPORT) {
                        StringBuilder meta = new StringBuilder(TimeUtils.displayDateTime(now));
                        String loc = prefs.manualLocation();
                        String w = prefs.manualWeather();
                        if (!loc.isEmpty()) meta.append(" · ").append(loc);
                        if (!w.isEmpty()) meta.append(" · ").append(w);
                        String person = prefs.person();
                        if (!person.isEmpty()) meta.append(" · ").append(person);
                        out = CollageMaker.report(title, meta.toString(), note, pics, caps,
                                "水印打卡 · " + TimeUtils.displayDateTime(now));
                    } else {
                        out = CollageMaker.collage(pics, l);
                    }
                }
            } catch (OutOfMemoryError oom) {
                err = "内存不足，请减少一次拼图的照片数量";
            } catch (Throwable t) {
                err = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
            }

            final Bitmap result = out;
            final String error = err;
            runOnUiThread(() -> {
                progress.setVisibility(View.GONE);
                if (result == null) {
                    toast("生成失败" + (error != null ? "：" + error : ""));
                    return;
                }
                saveAndOfferShare(result, m == CollageMaker.MODE_REPORT ? "WM_汇报_" : "WM_拼图_");
            });
        });
    }

    private void saveAndOfferShare(final Bitmap bmp, String namePrefix) {
        io.execute(() -> {
            Uri saved = ImageUtils.saveToGallery(this,
                    bmp, namePrefix + TimeUtils.fullStamp());
            File share = new File(getCacheDir(), "share_" + System.currentTimeMillis() + ".jpg");
            try (FileOutputStream os = new FileOutputStream(share)) {
                bmp.compress(Bitmap.CompressFormat.JPEG, 92, os);
            } catch (Exception ignored) {
            }
            final Uri uri = saved;
            final File shareFile = share;
            runOnUiThread(() -> {
                if (uri == null) {
                    toast("保存失败");
                    return;
                }
                updateSelHint();
                new AlertDialog.Builder(this)
                        .setTitle("已保存到相册「水印打卡」")
                        .setMessage("要现在分享这张" + (mode == CollageMaker.MODE_REPORT ? "汇报" : "拼图") + "吗？")
                        .setPositiveButton("分享", (d, w) -> {
                            try {
                                Uri fu = FileProvider.getUriForFile(this,
                                        getPackageName() + ".fileprovider", shareFile);
                                Intent send = new Intent(Intent.ACTION_SEND);
                                send.setType("image/jpeg");
                                send.putExtra(Intent.EXTRA_STREAM, fu);
                                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                startActivity(Intent.createChooser(send, "分享"));
                            } catch (Exception e) {
                                toast("分享失败：" + e.getMessage());
                            }
                        })
                        .setNegativeButton("完成", null)
                        .show();
            });
        });
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    // ============================================================
    // 网格适配器：第 0 项 = 从相册添加，其后为记录照片 + 导入照片
    // ============================================================
    private class PickAdapter extends RecyclerView.Adapter<PickAdapter.VH> {

        class VH extends RecyclerView.ViewHolder {
            final ImageView thumb;
            final TextView badge;

            VH(View v) {
                super(v);
                thumb = v.findViewById(R.id.thumb);
                badge = v.findViewById(R.id.badge);
            }
        }

        @Override
        public int getItemViewType(int position) {
            return position == 0 ? 0 : 1;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_pick, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            if (position == 0) {
                h.thumb.setImageResource(R.drawable.ic_plus);
                h.thumb.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                h.badge.setVisibility(View.GONE);
                h.itemView.setOnClickListener(v -> pickFromAlbum());
                return;
            }

            int pos = position - 1;
            String key;
            String uri;
            if (pos < records.size()) {
                RecordStore.Record r = records.get(pos);
                uri = r.uri;
                key = uri == null ? "rec:" + pos : uri;
            } else {
                uri = albumUris.get(pos - records.size());
                key = "alb:" + uri;
            }

            Bitmap b = (uri == null) ? null : thumbs.get(key);
            if (b == null && uri != null) {
                b = ImageUtils.decodeUri(CollageActivity.this, uri, 220);
                if (b != null) thumbs.put(key, b);
            }
            h.thumb.setImageBitmap(b);
            h.thumb.setScaleType(ImageView.ScaleType.CENTER_CROP);

            int order = selected.indexOf(key);
            if (order >= 0) {
                h.badge.setVisibility(View.VISIBLE);
                h.badge.setText(String.valueOf(order + 1));
            } else {
                h.badge.setVisibility(View.GONE);
            }

            h.itemView.setOnClickListener(v -> {
                if (uri == null) return;
                toggleSelect(key);
            });
        }

        @Override
        public int getItemCount() {
            return 1 + records.size() + albumUris.size();
        }
    }

    private void pickFromAlbum() {
        try {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("image/*");
            i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            startActivityForResult(i, REQ_PICK);
        } catch (Exception e) {
            toast("无法打开相册：" + e.getMessage());
        }
    }
}
