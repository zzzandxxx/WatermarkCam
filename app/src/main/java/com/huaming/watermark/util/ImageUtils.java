package com.huaming.watermark.util;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class ImageUtils {

    public static final String ALBUM = "水印打卡";

    private ImageUtils() {}

    /** 读取图片并按 EXIF 方向校正 + 限制长边 */
    public static Bitmap decodeOriented(String path, int maxSide) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, bounds);
        int w = bounds.outWidth, h = bounds.outHeight;
        int sample = 1;
        if (w > 0 && h > 0 && maxSide > 0) {
            while (Math.max(w / sample, h / sample) > maxSide * 2) sample *= 2;
        }
        BitmapFactory.Options opt = new BitmapFactory.Options();
        opt.inSampleSize = Math.max(1, sample);
        opt.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bmp = BitmapFactory.decodeFile(path, opt);
        if (bmp == null) throw new IOException("decode failed: " + path);

        int deg = 0;
        boolean flip = false;
        try {
            ExifInterface exif = new ExifInterface(path);
            int o = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            switch (o) {
                case ExifInterface.ORIENTATION_ROTATE_90: deg = 90; break;
                case ExifInterface.ORIENTATION_ROTATE_180: deg = 180; break;
                case ExifInterface.ORIENTATION_ROTATE_270: deg = 270; break;
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL: flip = true; break;
                case ExifInterface.ORIENTATION_FLIP_VERTICAL: flip = true; deg = 180; break;
                case ExifInterface.ORIENTATION_TRANSPOSE: flip = true; deg = 90; break;
                case ExifInterface.ORIENTATION_TRANSVERSE: flip = true; deg = 270; break;
                default: break;
            }
        } catch (Exception ignored) {
        }

        Bitmap result = bmp;
        if (deg != 0 || flip) {
            Matrix m = new Matrix();
            if (flip) m.postScale(-1f, 1f);
            if (deg != 0) m.postRotate(deg);
            try {
                Bitmap t = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
                if (t != bmp) {
                    bmp.recycle();
                    result = t;
                }
            } catch (OutOfMemoryError e) {
                result = bmp;
            }
        }

        if (maxSide > 0 && Math.max(result.getWidth(), result.getHeight()) > maxSide) {
            result = scaleTo(result, maxSide);
        }
        return result;
    }

    public static Bitmap scaleTo(Bitmap src, int maxSide) {
        int w = src.getWidth(), h = src.getHeight();
        float f = maxSide / (float) Math.max(w, h);
        if (f >= 1f) return src;
        Bitmap out = Bitmap.createScaledBitmap(src, Math.max(1, Math.round(w * f)),
                Math.max(1, Math.round(h * f)), true);
        if (out != src) src.recycle();
        return out;
    }

    public static Bitmap mirror(Bitmap src) {
        Matrix m = new Matrix();
        m.postScale(-1f, 1f);
        return Bitmap.createBitmap(src, 0, 0, src.getWidth(), src.getHeight(), m, true);
    }

    /** 保存到系统相册 Pictures/水印打卡，返回 MediaStore Uri；失败返回 null */
    public static Uri saveToGallery(Context ctx, Bitmap bmp, String displayName) {
        String fileName = displayName.endsWith(".jpg") ? displayName : displayName + ".jpg";
        ContentValues cv = new ContentValues();
        cv.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
        cv.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        cv.put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000L);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            cv.put(MediaStore.Images.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_PICTURES + File.separator + ALBUM);
            cv.put(MediaStore.Images.Media.IS_PENDING, 1);
        } else {
            File dir = new File(Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES), ALBUM);
            if (!dir.exists() && !dir.mkdirs()) return null;
            cv.put(MediaStore.Images.Media.DATA, new File(dir, fileName).getAbsolutePath());
        }

        Uri uri = null;
        try {
            uri = ctx.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
            if (uri == null) return null;
            try (OutputStream os = ctx.getContentResolver().openOutputStream(uri)) {
                if (os == null) return null;
                bmp.compress(Bitmap.CompressFormat.JPEG, 95, os);
                os.flush();
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues done = new ContentValues();
                done.put(MediaStore.Images.Media.IS_PENDING, 0);
                ctx.getContentResolver().update(uri, done, null, null);
            }
            return uri;
        } catch (Exception e) {
            if (uri != null) {
                try {
                    ctx.getContentResolver().delete(uri, null, null);
                } catch (Exception ignored) {
                }
            }
            return null;
        }
    }

    /** 把 Uri 指向的图片复制为本地文件（用于导入 logo） */
    public static File copyUriToFile(Context ctx, Uri uri, File dst) throws IOException {
        File parent = dst.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (InputStream in = ctx.getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(dst)) {
            if (in == null) throw new IOException("open uri failed");
            byte[] buf = new byte[32 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.flush();
        }
        return dst;
    }

    /** 按最长边缩放并把透明区域保留为 PNG，用于 logo 归一化 */
    public static Bitmap loadBitmap(String path, int maxSide) {
        BitmapFactory.Options opt = new BitmapFactory.Options();
        opt.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap b = BitmapFactory.decodeFile(path, opt);
        if (b == null) return null;
        if (maxSide > 0 && Math.max(b.getWidth(), b.getHeight()) > maxSide) {
            b = scaleTo(b, maxSide);
        }
        return b;
    }

    /** 在透明背景上居中绘制，用于把任意图片统一成正方形 logo */
    public static Bitmap squarefit(Bitmap src, int size) {
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        float f = Math.min(size / (float) src.getWidth(), size / (float) src.getHeight());
        float w = src.getWidth() * f, h = src.getHeight() * f;
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        p.setFilterBitmap(true);
        c.drawBitmap(src, null,
                new android.graphics.RectF((size - w) / 2f, (size - h) / 2f,
                        (size - w) / 2f + w, (size - h) / 2f + h), p);
        return out;
    }

    public static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(java.util.Locale.US, "%.0f KB", bytes / 1024f);
        return String.format(java.util.Locale.US, "%.1f MB", bytes / 1048576f);
    }

    /** 从 content Uri 解码缩略图 */
    public static Bitmap decodeUri(Context ctx, String uriStr, int maxSide) {
        if (uriStr == null || uriStr.isEmpty()) return null;
        try {
            Uri uri = Uri.parse(uriStr);
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
                if (in == null) return null;
                BitmapFactory.decodeStream(in, null, bounds);
            }
            int sample = 1;
            if (bounds.outWidth > 0 && bounds.outHeight > 0) {
                while (Math.max(bounds.outWidth / sample, bounds.outHeight / sample) > maxSide * 2) {
                    sample *= 2;
                }
            }
            BitmapFactory.Options opt = new BitmapFactory.Options();
            opt.inSampleSize = Math.max(1, sample);
            opt.inPreferredConfig = Bitmap.Config.ARGB_8888;
            try (InputStream in = ctx.getContentResolver().openInputStream(uri)) {
                if (in == null) return null;
                return BitmapFactory.decodeStream(in, null, opt);
            }
        } catch (Exception | OutOfMemoryError e) {
            return null;
        }
    }
}
