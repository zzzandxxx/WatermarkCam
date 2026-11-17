package com.huaming.watermark.ui;

import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.huaming.watermark.R;
import com.huaming.watermark.data.LogoStore;
import com.huaming.watermark.data.Prefs;
import com.huaming.watermark.util.LogoProcessor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 品牌库：分类 + 搜索 + 双列网格，选中即用作水印 Logo。
 */
public class LogoActivity extends AppCompatActivity {

    private Prefs prefs;
    private RecyclerView grid;
    private EditText etSearch;
    private ImageView btnClear;
    private LinearLayout chipRow;
    private TextView tvEmpty;

    private final List<LogoStore.Item> items = new ArrayList<>();
    private final Map<String, Bitmap> cache = new HashMap<>();
    private Adapter adapter;

    private String cat = LogoStore.CAT_HOT;
    private String keyword = "";

    private final ActivityResultLauncher<String> picker =
            registerForActivityResult(new ActivityResultContracts.GetMultipleContents(), uris -> {
                if (uris != null && !uris.isEmpty()) importLogos(uris);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_logo);

        prefs = new Prefs(this);
        grid = findViewById(R.id.grid);
        chipRow = findViewById(R.id.chipRow);
        tvEmpty = findViewById(R.id.tvEmpty);
        etSearch = findViewById(R.id.etSearch);
        btnClear = findViewById(R.id.btnClear);

        grid.setLayoutManager(new GridLayoutManager(this, 2));
        adapter = new Adapter();
        grid.setAdapter(adapter);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnNone).setOnClickListener(v -> {
            prefs.setLogoId(LogoStore.NONE);
            finish();
        });
        findViewById(R.id.btnImport).setOnClickListener(v -> startImport());

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) { }

            @Override
            public void afterTextChanged(Editable s) {
                boolean wasSearching = !keyword.isEmpty();
                keyword = s.toString().trim();
                btnClear.setVisibility(s.length() > 0 ? View.VISIBLE : View.GONE);
                // 进入/退出搜索时刷新分类标签的高亮状态
                if (wasSearching != !keyword.isEmpty()) buildChips();
                apply();
            }
        });
        btnClear.setOnClickListener(v -> etSearch.setText(""));

        buildChips();
        apply();
    }

    // ============================================================
    // 分类标签
    // ============================================================
    private void buildChips() {
        chipRow.removeAllViews();
        for (final String c : LogoStore.CATS) {
            boolean on = c.equals(cat) && keyword.isEmpty();
            TextView t = new TextView(this);
            t.setText(c);
            t.setTextSize(12.5f);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(13), dp(7), dp(13), dp(7));
            t.setBackgroundResource(on ? R.drawable.bg_chip_on : R.drawable.bg_chip);
            t.setTextColor(on ? 0xFFFFC400 : 0xFF98A4B5);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.rightMargin = dp(8);
            t.setLayoutParams(lp);
            t.setOnClickListener(v -> {
                cat = c;
                etSearch.setText("");
                keyword = "";
                buildChips();
                apply();
                grid.scrollToPosition(0);
            });
            chipRow.addView(t);
        }
    }

    // ============================================================
    // 数据装配
    // ============================================================
    private void apply() {
        items.clear();
        if (!keyword.isEmpty()) {
            items.addAll(LogoStore.search(keyword));
        } else if (LogoStore.CAT_ICON.equals(cat)) {
            items.addAll(LogoStore.builtins());
        } else if (LogoStore.CAT_MINE.equals(cat)) {
            items.addAll(LogoStore.customs(this));
        } else {
            items.addAll(LogoStore.brandsOf(cat));
        }
        tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        adapter.notifyDataSetChanged();
    }

    private void importLogos(final List<Uri> uris) {
        toast("正在处理 " + uris.size() + " 张…");
        final LogoProcessor.Options opt =
                new LogoProcessor.Options(prefs.logoCutBg(), prefs.logoTrim());
        new Thread(() -> {
            int ok = 0;
            String firstId = null;
            String failure = null;
            for (Uri uri : uris) {
                try {
                    String name = LogoStore.nameOfUri(LogoActivity.this, uri);
                    String id = LogoStore.importFrom(LogoActivity.this, uri, name, opt);
                    if (firstId == null) firstId = id;
                    ok++;
                } catch (Exception e) {
                    failure = e.getMessage();
                }
            }
            final int okCount = ok;
            final String logoId = firstId;
            final String err = failure;
            runOnUiThread(() -> {
                cache.clear();
                if (logoId != null) prefs.setLogoId(logoId);
                cat = LogoStore.CAT_MINE;
                etSearch.setText("");
                keyword = "";
                buildChips();
                apply();
                if (okCount == 0) {
                    toast("导入失败：" + (err == null ? "未知错误" : err));
                } else {
                    toast("已导入 " + okCount + " 个 Logo，并设为当前");
                }
            });
        }, "logo-import").start();
    }

    // ============================================================
    // 导入前的处理选项
    // ============================================================
    private void startImport() {
        int pad = dp(20);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, dp(10), pad, 0);

        final CheckBox cbBg = new CheckBox(this);
        cbBg.setText("自动去掉白底 / 纯色底");
        cbBg.setTextSize(14f);
        cbBg.setTextColor(0xFFF2F5F9);
        cbBg.setChecked(prefs.logoCutBg());
        box.addView(cbBg);

        final CheckBox cbTrim = new CheckBox(this);
        cbTrim.setText("自动裁掉四周空白边");
        cbTrim.setTextSize(14f);
        cbTrim.setTextColor(0xFFF2F5F9);
        cbTrim.setChecked(prefs.logoTrim());
        box.addView(cbTrim);

        TextView tip = new TextView(this);
        tip.setText("从官网或图片搜索存下来的 logo 一般带白底、四周还有大片留白。"
                + "两项都勾上，logo 才会自然压在照片上，不糊一块白方块。\n"
                + "处理得不理想，可以在「我的」里长按选「重新处理」。");
        tip.setTextColor(0xFF5F6B7C);
        tip.setTextSize(11f);
        tip.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        box.addView(tip, lp);

        new AlertDialog.Builder(this)
                .setTitle("导入 Logo")
                .setView(box)
                .setPositiveButton("选择图片", (d, w) -> {
                    prefs.setLogoCutBg(cbBg.isChecked());
                    prefs.setLogoTrim(cbTrim.isChecked());
                    try {
                        picker.launch("image/*");
                    } catch (Exception e) {
                        toast("无法打开相册：" + e.getMessage());
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 对已经导入的 logo 再跑一遍去底 + 裁边 */
    private void reprocess(final LogoStore.Item item) {
        toast("正在重新处理…");
        final LogoProcessor.Options opt =
                new LogoProcessor.Options(prefs.logoCutBg(), prefs.logoTrim());
        new Thread(() -> {
            final boolean ok = LogoStore.reprocess(LogoActivity.this, item.id, opt);
            runOnUiThread(() -> {
                cache.clear();
                apply();
                toast(ok ? "已重新处理「" + item.name + "」" : "处理失败，可换个更清晰的图片来源");
            });
        }, "logo-reprocess").start();
    }

    // ============================================================
    // 自定义 Logo 的重命名 / 删除
    // ============================================================
    private void renameDialog(final LogoStore.Item item) {
        final EditText et = new EditText(this);
        et.setText(item.name);
        et.setSingleLine(true);
        et.setTextColor(0xFFF2F5F9);
        et.setHintTextColor(0xFF5F6B7C);
        int pad = dp(20);
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(pad, dp(4), pad, 0);
        wrap.addView(et, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
                .setTitle("重命名")
                .setView(wrap)
                .setPositiveButton("保存", (d, w) -> {
                    String name = et.getText().toString().trim();
                    if (name.isEmpty()) return;
                    boolean wasCurrent = item.id.equals(prefs.logoId());
                    String newId = LogoStore.idAfterRename(item.id, name);
                    if (LogoStore.rename(this, item.id, name)) {
                        if (wasCurrent) prefs.setLogoId(newId);
                        cache.clear();
                        apply();
                    } else {
                        toast("重命名失败，名称可能重复");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void confirmDelete(final LogoStore.Item item) {
        new AlertDialog.Builder(this)
                .setTitle("删除 Logo")
                .setMessage("确定删除「" + item.name + "」？")
                .setPositiveButton("删除", (d, w) -> {
                    LogoStore.delete(this, item.id);
                    if (item.id.equals(prefs.logoId())) prefs.setLogoId(LogoStore.NONE);
                    cache.clear();
                    apply();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    // ============================================================
    // 适配器
    // ============================================================
    private class Adapter extends RecyclerView.Adapter<Adapter.VH> {

        class VH extends RecyclerView.ViewHolder {
            final ImageView img, imgNone;
            final TextView name;

            VH(View v) {
                super(v);
                img = v.findViewById(R.id.img);
                imgNone = v.findViewById(R.id.imgNone);
                name = v.findViewById(R.id.name);
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_logo, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            final LogoStore.Item it = items.get(position);
            String current = prefs.logoId();
            boolean selected = it.id.equals(current);

            h.itemView.setBackgroundResource(selected ? R.drawable.bg_tile_on : R.drawable.bg_tile);
            h.name.setText(it.name);
            h.name.setTextColor(selected ? 0xFFFFC400 : 0xFF98A4B5);

            h.imgNone.setVisibility(View.GONE);
            h.img.setVisibility(View.VISIBLE);
            Bitmap b = cache.get(it.id);
            if (b == null) {
                b = LogoStore.load(LogoActivity.this, it.id, 180);
                if (b != null) cache.put(it.id, b);
            }
            h.img.setImageBitmap(b);

            h.itemView.setOnClickListener(v -> {
                prefs.setLogoId(it.id);
                notifyDataSetChanged();
                grid.postDelayed(LogoActivity.this::finish, 120);
            });

            h.itemView.setOnLongClickListener(v -> {
                if (it.builtin) return false;
                new AlertDialog.Builder(LogoActivity.this)
                        .setTitle(it.name)
                        .setItems(new String[]{"重命名", "重新处理（去白底 / 裁边）", "删除"}, (d, which) -> {
                            if (which == 0) renameDialog(it);
                            else if (which == 1) reprocess(it);
                            else confirmDelete(it);
                        })
                        .show();
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }
}
