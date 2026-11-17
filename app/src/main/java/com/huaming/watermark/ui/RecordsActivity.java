package com.huaming.watermark.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.huaming.watermark.R;
import com.huaming.watermark.data.RecordStore;
import com.huaming.watermark.util.ImageUtils;
import com.huaming.watermark.util.TimeUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RecordsActivity extends AppCompatActivity {

    private RecordStore store;
    private final List<RecordStore.Record> data = new ArrayList<>();
    private final Map<String, Bitmap> thumbs = new HashMap<>();
    private Adapter adapter;
    private TextView empty, tvCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_records);

        store = new RecordStore(this);
        RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new Adapter();
        list.setAdapter(adapter);

        empty = findViewById(R.id.empty);
        tvCount = findViewById(R.id.tvCount);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnCollage).setOnClickListener(v ->
                startActivity(new Intent(this, CollageActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private void reload() {
        data.clear();
        data.addAll(store.list(999));
        adapter.notifyDataSetChanged();
        boolean has = !data.isEmpty();
        empty.setVisibility(has ? View.GONE : View.VISIBLE);
        tvCount.setText(has ? (data.size() + " 条") : "");
    }

    private class Adapter extends RecyclerView.Adapter<Adapter.VH> {

        class VH extends RecyclerView.ViewHolder {
            final ImageView thumb;
            final TextView title, time, loc;

            VH(View v) {
                super(v);
                thumb = v.findViewById(R.id.thumb);
                title = v.findViewById(R.id.tvTitle);
                time = v.findViewById(R.id.tvTime);
                loc = v.findViewById(R.id.tvLoc);
            }
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_record, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            final RecordStore.Record r = data.get(position);

            h.title.setText(r.title == null || r.title.isEmpty() ? "打卡记录" : r.title);
            h.time.setText(TimeUtils.displayDateTime(r.time));
            String locRaw = r.location;
            if (locRaw == null || locRaw.isEmpty()) {
                locRaw = (r.remark == null || r.remark.isEmpty()) ? "" : r.remark;
            }
            final String loc = locRaw;
            h.loc.setText(loc);
            h.loc.setVisibility(loc.isEmpty() ? View.GONE : View.VISIBLE);

            Bitmap b = (r.uri == null) ? null : thumbs.get(r.uri);
            if (b == null && r.uri != null) {
                b = ImageUtils.decodeUri(RecordsActivity.this, r.uri, 180);
                if (b != null) thumbs.put(r.uri, b);
            }
            h.thumb.setImageBitmap(b);

            h.itemView.setOnClickListener(v -> {
                if (r.uri == null) return;
                Intent i = new Intent(RecordsActivity.this, PhotoActivity.class);
                i.putExtra(PhotoActivity.EXTRA_URI, r.uri);
                i.putExtra(PhotoActivity.EXTRA_ID, r.id);
                String info = TimeUtils.displayDateTime(r.time);
                if (loc != null && !loc.isEmpty()) info = info + " · " + loc;
                i.putExtra(PhotoActivity.EXTRA_INFO, info);
                startActivity(i);
            });

            h.itemView.findViewById(R.id.btnDel).setOnClickListener(v ->
                    new AlertDialog.Builder(RecordsActivity.this)
                            .setTitle("删除记录")
                            .setMessage("同时从手机相册删除这张照片？")
                            .setPositiveButton("删除", (d, w) -> {
                                if (r.uri != null) {
                                    try {
                                        getContentResolver().delete(
                                                android.net.Uri.parse(r.uri), null, null);
                                    } catch (Exception ignored) {
                                    }
                                }
                                store.delete(r.id);
                                thumbs.remove(r.uri);
                                reload();
                            })
                            .setNegativeButton("取消", null)
                            .show());
        }

        @Override
        public int getItemCount() {
            return data.size();
        }
    }
}
