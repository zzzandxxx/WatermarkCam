package com.huaming.watermark.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.huaming.watermark.R;
import com.huaming.watermark.data.RecordStore;
import com.huaming.watermark.util.ImageUtils;

public class PhotoActivity extends AppCompatActivity {

    public static final String EXTRA_URI = "extra_uri";
    public static final String EXTRA_ID = "extra_id";
    public static final String EXTRA_INFO = "extra_info";

    private String uriStr;
    private long recordId = -1L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo);

        uriStr = getIntent().getStringExtra(EXTRA_URI);
        recordId = getIntent().getLongExtra(EXTRA_ID, -1L);
        String info = getIntent().getStringExtra(EXTRA_INFO);

        ImageView img = findViewById(R.id.img);
        TextView tvInfo = findViewById(R.id.tvInfo);
        tvInfo.setText(info == null ? "" : info);

        if (uriStr != null) {
            Bitmap b = ImageUtils.decodeUri(this, uriStr, 2200);
            img.setImageBitmap(b);
        }

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnShare).setOnClickListener(v -> share());
        findViewById(R.id.btnDel).setOnClickListener(v -> confirmDelete());
    }

    private void share() {
        if (uriStr == null) return;
        try {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("image/jpeg");
            i.putExtra(Intent.EXTRA_STREAM, Uri.parse(uriStr));
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(i, "分享照片"));
        } catch (Exception e) {
            Toast.makeText(this, "分享失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("删除这张照片")
                .setMessage("会同时从手机相册中移除，操作不可撤销。")
                .setPositiveButton("删除", (d, w) -> {
                    if (uriStr != null) {
                        try {
                            getContentResolver().delete(Uri.parse(uriStr), null, null);
                        } catch (Exception ignored) {
                        }
                    }
                    if (recordId > 0) {
                        new RecordStore(this).delete(recordId);
                    }
                    finish();
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
