package com.huaming.watermark.ui;

import android.Manifest;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.core.resolutionselector.AspectRatioStrategy;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.huaming.watermark.R;
import com.huaming.watermark.data.LogoStore;
import com.huaming.watermark.data.Prefs;
import com.huaming.watermark.data.RecordStore;
import com.huaming.watermark.model.WatermarkData;
import com.huaming.watermark.model.WatermarkStamp;
import com.huaming.watermark.model.WatermarkStyle;
import com.huaming.watermark.render.WatermarkOverlayView;
import com.huaming.watermark.render.WatermarkPainter;
import com.huaming.watermark.util.ImageUtils;
import com.huaming.watermark.util.LocationHelper;
import com.huaming.watermark.util.TimeUtils;
import com.huaming.watermark.util.WeatherHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_PERM = 1001;

    private Prefs prefs;
    private RecordStore records;
    private ExecutorService io;

    private PreviewView previewView;
    private WatermarkOverlayView overlay;
    private GridOverlayView gridView;
    private AspectRatioLayout photoBox;
    private ImageView btnShutter, btnFlash, btnFlip, btnSettings, imgLogo;
    private FrameLayout btnLogo;
    private TextView chipTitle, chipLocation, chipRemark, chipPerson, chipProject;
    private TextView tvTitle, tvSub, tvRecordCount, emptyTip;
    private ProgressBar progress;
    private LinearLayout topBar, bottomPanel, templateRow;

    private ProcessCameraProvider cameraProvider;
    private ImageCapture imageCapture;
    private Camera camera;
    private int lensFacing = CameraSelector.LENS_FACING_BACK;
    private int flashMode = ImageCapture.FLASH_MODE_OFF;

    /** 手机物理握持方向（界面竖屏锁定，仅画面/水印跟随）：ROTATION_0/90/180/270 */
    private OrientationEventListener orientListener;
    private int deviceRot = Surface.ROTATION_0;

    private double lat = 0d, lon = 0d;
    private String address = "";
    private String weather = "";
    private boolean locating = false;
    private long lastShotAt = 0L;

    private String loadedLogoId = null;

    /** 模板缩略图缓存（用绘制引擎实时渲染的真效果小卡） */
    private final Map<Integer, Bitmap> thumbCache = new HashMap<>();

    /** 简单文本回调 */
    private interface OnText {
        void on(String value);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 崩溃现场记录：真机闪退时堆栈自动写入应用外部私有目录，便于定位
        com.huaming.watermark.util.CrashGuard.install(this);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);

        prefs = new Prefs(this);
        records = new RecordStore(this);
        io = Executors.newSingleThreadExecutor();

        bindViews();
        applyWindowInsets();
        // 监听手机物理旋转（界面锁定竖屏，仅画面/水印跟随）
        orientListener = new OrientationEventListener(this, android.hardware.SensorManager.SENSOR_DELAY_NORMAL) {
            @Override
            public void onOrientationChanged(int o) {
                if (o == OrientationEventListener.ORIENTATION_UNKNOWN) return;
                int snapped = ((o + 45) / 90 * 90) % 360;
                if (snapped == deviceRot) return;
                // 距当前档位中心超过 55° 才切换，避免在档位边界来回抖动
                int diff = Math.abs(((o - deviceRot + 540) % 360) - 180);
                if (diff >= 55) {
                    runOnUiThread(() -> {
                        try {
                            onDeviceRotationChanged(snapped);
                        } catch (Throwable t) {
                            // 旋转是连续事件，任何异常都不应让相机闪退
                            android.util.Log.w("MainActivity", "rotation update failed", t);
                        }
                    });
                }
            }
        };
        onDeviceRotationChanged(deviceRot);
        applyStyleFromPrefs();
        reloadLogo();
        migrateLogosAsync();
        setupListeners();
        refreshTemplateRow();
        syncOverlay();
        updateRecordCount();

        if (hasCameraPermission()) {
            startCamera();
            requestLocationIfAllowed();
        } else {
            ActivityCompat.requestPermissions(this, neededPermissions(), REQ_PERM);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (orientListener != null && orientListener.canDetectOrientation()) {
            orientListener.enable();
        }
        onDeviceRotationChanged(deviceRot);
        applyStyleFromPrefs();
        reloadLogo();
        gridView.setGridVisible(prefs.grid());
        syncOverlay();
        updateRecordCount();
        // 从「地点」页返回后，副标题与定位策略要跟着变
        if (prefs.customPlace() && prefs.hasPlaceCoord()) {
            locating = false;
            updateSubForCustomPlace();
        } else if (lat == 0d && lon == 0d) {
            locating = false;
            requestLocationIfAllowed();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (orientListener != null) orientListener.disable();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (io != null) io.shutdownNow();
    }

    /**
     * 「今日水印」式旋转：界面竖屏锁定，只有相机画面与水印方向跟随手机握持方向。
     * 取景框比例、成片方向、画面旋转三件事在这里同步。
     */
    private void onDeviceRotationChanged(int rot) {
        deviceRot = rot;
        boolean landscape = rot == Surface.ROTATION_90 || rot == Surface.ROTATION_270;
        if (photoBox == null) return;
        // 竖拍成片 3:4（高/宽=4/3），横拍成片 4:3（高/宽=3/4），保证所见即所得
        photoBox.setRatio(landscape ? 3f / 4f : 4f / 3f);
        // 相机画面在取景框内反向旋转，横握手机时看到正向画面；水印层不动
        photoBox.setContentRotation((360 - rot) % 360);
        if (imageCapture != null) {
            try {
                // 相机正在重配时改目标旋转在个别机型上会抛异常，防御住不让 App 闪退
                imageCapture.setTargetRotation(rot);
            } catch (Throwable t) {
                android.util.Log.w("MainActivity", "setTargetRotation failed", t);
            }
        }
    }

    // ============================================================
    // 初始化
    // ============================================================
    private void bindViews() {
        previewView = findViewById(R.id.preview);
        overlay = findViewById(R.id.overlay);
        gridView = findViewById(R.id.gridView);
        photoBox = findViewById(R.id.photoBox);
        btnShutter = findViewById(R.id.btnShutter);
        btnFlash = findViewById(R.id.btnFlash);
        btnFlip = findViewById(R.id.btnFlip);
        btnSettings = findViewById(R.id.btnSettings);
        imgLogo = findViewById(R.id.imgLogo);
        btnLogo = findViewById(R.id.btnLogo);
        chipTitle = findViewById(R.id.chipTitle);
        chipLocation = findViewById(R.id.chipLocation);
        chipRemark = findViewById(R.id.chipRemark);
        chipPerson = findViewById(R.id.chipPerson);
        chipProject = findViewById(R.id.chipProject);
        tvTitle = findViewById(R.id.tvTitle);
        tvSub = findViewById(R.id.tvSub);
        tvRecordCount = findViewById(R.id.tvRecordCount);
        emptyTip = findViewById(R.id.emptyTip);
        progress = findViewById(R.id.progress);
        topBar = findViewById(R.id.topBar);
        bottomPanel = findViewById(R.id.bottomPanel);
        templateRow = findViewById(R.id.templateRow);
    }

    private void applyWindowInsets() {
        final int padH = dp(12);
        final View stageView = findViewById(R.id.stage);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root), (v, insets) -> {
            androidx.core.graphics.Insets sb =
                    insets.getInsets(WindowInsetsCompat.Type.systemBars());
            // 横屏时导航栏可能出现在左右两侧，一并让开
            topBar.setPadding(padH + sb.left, sb.top + dp(8), padH + sb.right, dp(14));
            bottomPanel.setPadding(padH + sb.left, dp(8), padH + sb.right, dp(10) + sb.bottom);
            // 让取景画面从顶栏下沿开始，避免顶部水印被顶栏压住
            topBar.post(() -> {
                int h = topBar.getHeight();
                if (stageView.getPaddingTop() != h) {
                    stageView.setPadding(0, h, 0, 0);
                }
            });
            return insets;
        });
    }

    private void setupListeners() {
        btnShutter.setOnClickListener(v -> shoot());
        // 快门按压反馈：按下轻微缩小，抬手回弹
        btnShutter.setOnTouchListener((v, ev) -> {
            switch (ev.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.9f).scaleY(0.9f).setDuration(80).start();
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                    break;
            }
            return false;
        });
        btnFlip.setOnClickListener(v -> toggleCamera());
        btnFlash.setOnClickListener(v -> cycleFlash());
        btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        btnLogo.setOnClickListener(v -> startActivity(new Intent(this, LogoActivity.class)));
        findViewById(R.id.btnRecords).setOnClickListener(v -> startActivity(new Intent(this, RecordsActivity.class)));
        findViewById(R.id.btnCollage).setOnClickListener(v -> startActivity(new Intent(this, CollageActivity.class)));

        // 点取景画面上的水印 → 点哪一行就改哪一项
        overlay.setOnFieldTap(this::onWatermarkFieldTap);
        overlay.setOnClickListener(v -> editWatermarkTime());

        chipTitle.setOnClickListener(v -> editTitle());
        chipLocation.setOnClickListener(v -> startActivity(new Intent(this, PlaceActivity.class)));
        chipRemark.setOnClickListener(v -> editRemark());
        chipPerson.setOnClickListener(v -> editPerson());
        chipProject.setOnClickListener(v -> editProject());
    }

    // ============================================================
    // 水印行级编辑（点水印上对应行 / 点底部快捷条均可）
    // ============================================================
    private void onWatermarkFieldTap(String field) {
        switch (field) {
            case WatermarkPainter.F_TITLE:
                editTitle();
                break;
            case WatermarkPainter.F_TIME:
                editWatermarkTime();
                break;
            case WatermarkPainter.F_LOCATION:
            case WatermarkPainter.F_COORD:
                startActivity(new Intent(this, PlaceActivity.class));
                break;
            case WatermarkPainter.F_WEATHER:
                editWeather();
                break;
            case WatermarkPainter.F_REMARK:
                editRemark();
                break;
            case WatermarkPainter.F_PERSON:
                editPerson();
                break;
            case WatermarkPainter.F_PROJECT:
                editProject();
                break;
            default:
                editWatermarkTime();
                break;
        }
    }

    private void editTitle() {
        prompt("水印标题", prefs.title(), false, value -> {
            prefs.setTitle(value);
            syncOverlay();
        });
    }

    private void editRemark() {
        prompt("备注", prefs.remark(), true, value -> {
            prefs.setRemark(value);
            syncOverlay();
        });
    }

    private void editPerson() {
        prompt("打卡人", prefs.person(), false, value -> {
            prefs.setPerson(value);
            syncOverlay();
        });
    }

    private void editProject() {
        prompt("单位 / 项目", prefs.project(), false, value -> {
            prefs.setProject(value);
            syncOverlay();
        });
    }

    /** 天气行：手动填写优先；清空即恢复自动获取 */
    private void editWeather() {
        prompt("天气（手动填写）", prefs.manualWeather(), false, value -> {
            prefs.setManualWeather(value);
            if (value.isEmpty()) {
                toast("已恢复自动获取天气");
                if (prefs.weatherOnline()) fetchWeather();
            } else {
                toast("已使用手动天气");
            }
            syncOverlay();
        });
    }

    // ============================================================
    // 权限
    // ============================================================
    private String[] neededPermissions() {
        List<String> list = new ArrayList<>();
        list.add(Manifest.permission.CAMERA);
        if (!hasLocationPermission()) {
            list.add(Manifest.permission.ACCESS_FINE_LOCATION);
            list.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            list.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        return list.toArray(new String[0]);
    }

    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_PERM) return;
        if (hasCameraPermission()) {
            startCamera();
            requestLocationIfAllowed();
        } else {
            emptyTip.setVisibility(View.VISIBLE);
            emptyTip.setText("未获得相机权限\n请到「设置 → 应用 → 水印打卡 → 权限」中开启");
        }
    }

    // ============================================================
    // 相机
    // ============================================================
    private void startCamera() {
        final ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                bindCamera();
            } catch (Exception e) {
                emptyTip.setVisibility(View.VISIBLE);
                emptyTip.setText("相机初始化失败\n" + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCamera() {
        if (cameraProvider == null) return;
        cameraProvider.unbindAll();

        ResolutionSelector rs = new ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .build();

        Preview preview = new Preview.Builder()
                .setResolutionSelector(rs)
                .build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setResolutionSelector(rs)
                .setFlashMode(flashMode)
                .build();
        // 成片方向跟随手机握持方向：横握出横片，竖握出竖片
        imageCapture.setTargetRotation(deviceRot);

        CameraSelector selector = new CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build();

        try {
            camera = cameraProvider.bindToLifecycle(this, selector, preview, imageCapture);
            emptyTip.setVisibility(View.GONE);
            updateFlashUi();
        } catch (Exception e) {
            if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                lensFacing = CameraSelector.LENS_FACING_BACK;
                bindCamera();
            } else {
                emptyTip.setVisibility(View.VISIBLE);
                emptyTip.setText("未检测到可用摄像头");
            }
        }
    }

    private void toggleCamera() {
        lensFacing = (lensFacing == CameraSelector.LENS_FACING_BACK)
                ? CameraSelector.LENS_FACING_FRONT
                : CameraSelector.LENS_FACING_BACK;
        flashMode = ImageCapture.FLASH_MODE_OFF;
        bindCamera();
    }

    private void cycleFlash() {
        if (camera == null || !camera.getCameraInfo().hasFlashUnit()) {
            toast("当前摄像头不支持闪光灯");
            return;
        }
        if (flashMode == ImageCapture.FLASH_MODE_OFF) flashMode = ImageCapture.FLASH_MODE_ON;
        else if (flashMode == ImageCapture.FLASH_MODE_ON) flashMode = ImageCapture.FLASH_MODE_AUTO;
        else flashMode = ImageCapture.FLASH_MODE_OFF;
        if (imageCapture != null) imageCapture.setFlashMode(flashMode);
        updateFlashUi();
    }

    private void updateFlashUi() {
        int color;
        String label;
        switch (flashMode) {
            case ImageCapture.FLASH_MODE_ON:
                color = 0xFFFFC400;
                label = "闪光灯：开";
                break;
            case ImageCapture.FLASH_MODE_AUTO:
                color = 0x99FFC400;
                label = "闪光灯：自动";
                break;
            default:
                color = 0xFFFFFFFF;
                label = "闪光灯：关";
                break;
        }
        btnFlash.setColorFilter(color);
        btnFlash.setContentDescription(label);
    }

    // ============================================================
    // 拍照
    // ============================================================
    private void shoot() {
        long now = System.currentTimeMillis();
        if (now - lastShotAt < 700L) return;
        lastShotAt = now;

        if (imageCapture == null) {
            toast("相机尚未就绪");
            return;
        }

        final long shotAt = now;
        File tmp = new File(getCacheDir(), "shot_" + shotAt + ".jpg");
        ImageCapture.OutputFileOptions opt =
                new ImageCapture.OutputFileOptions.Builder(tmp).build();

        imageCapture.takePicture(opt, ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults results) {
                        vibrate();
                        processAndSave(tmp, shotAt);
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        toast("拍摄失败：" + exception.getMessage());
                    }
                });
    }

    private void processAndSave(final File tmp, final long shotAt) {
        progress.setVisibility(View.VISIBLE);

        final WatermarkData snap = overlay.data().copy();
        // 水印上显示的时间：固定模式下用指定时刻，否则用拍照时刻
        final long shownAt = prefs.effectiveTime(shotAt);
        snap.timeText = TimeUtils.timeAt(shownAt, prefs.seconds());
        snap.dateText = TimeUtils.dateWeekAt(shownAt);

        final WatermarkStyle styleSnap = overlay.style().copy();
        final boolean front = (lensFacing == CameraSelector.LENS_FACING_FRONT);
        final boolean doMirror = front && prefs.frontMirror();
        final int maxSide = Math.max(1600, prefs.maxSide());
        final boolean keepOriginal = prefs.saveOriginal();
        final String stamp = TimeUtils.fullStampAt(shotAt);

        io.execute(() -> {
            Uri saved = null;
            String err = null;
            try {
                Bitmap bmp = ImageUtils.decodeOriented(tmp.getAbsolutePath(), maxSide);
                if (doMirror) {
                    Bitmap m = ImageUtils.mirror(bmp);
                    if (m != bmp) {
                        bmp.recycle();
                        bmp = m;
                    }
                }
                if (keepOriginal) {
                    ImageUtils.saveToGallery(MainActivity.this, bmp, "WM_" + stamp + "_原图");
                }

                Bitmap out = Bitmap.createBitmap(bmp.getWidth(), bmp.getHeight(), Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(out);
                canvas.drawBitmap(bmp, 0, 0, null);
                WatermarkPainter.draw(canvas, out.getWidth(), out.getHeight(), snap, styleSnap);
                bmp.recycle();

                saved = ImageUtils.saveToGallery(MainActivity.this, out, "WM_" + stamp);
                out.recycle();

                RecordStore.Record rec = new RecordStore.Record();
                rec.time = shotAt;
                rec.uri = saved != null ? saved.toString() : null;
                rec.title = snap.title;
                rec.location = snap.location;
                rec.remark = snap.remark;
                records.insert(rec);
            } catch (OutOfMemoryError oom) {
                err = "内存不足，请在设置中调低照片尺寸";
            } catch (Throwable t) {
                err = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
            } finally {
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
            }

            final Uri uri = saved;
            final String error = err;
            runOnUiThread(() -> {
                progress.setVisibility(View.GONE);
                if (uri != null) {
                    toast("已保存到相册 · 水印打卡");
                    updateRecordCount();
                } else {
                    toast("保存失败" + (error != null ? "：" + error : ""));
                }
            });
        });
    }

    // ============================================================
    // 水印数据同步
    // ============================================================
    private void applyStyleFromPrefs() {
        WatermarkStyle s = overlay.style();
        s.template = prefs.template();
        s.position = prefs.position();
        s.scale = prefs.scale();
        s.opacity = prefs.opacity();
        s.fullWidth = prefs.fullWidth();
        applyStampFromPrefs();
        if (overlay.isWithSeconds() != prefs.seconds()) {
            overlay.setWithSeconds(prefs.seconds());
        }
        overlay.setTimeMode(prefs.timeMode(), prefs.fixedTime());
        overlay.refresh();
    }

    /** 把淡文字水印的偏好灌进样式对象 */
    private void applyStampFromPrefs() {
        WatermarkStamp st = overlay.style().stamp;
        st.enabled = prefs.stampOn();
        st.text = prefs.stampText();
        st.opacity = prefs.stampOpacity();
        st.sizeRatio = prefs.stampSize();
        st.angle = prefs.stampAngle();
        st.tiled = prefs.stampTiled();
        st.color = prefs.stampColor();
    }

    private void syncOverlay() {
        WatermarkData d = overlay.data();
        d.title = prefs.title();
        d.person = prefs.showPerson() ? prefs.person() : "";
        d.project = prefs.showProject() ? prefs.project() : "";
        d.remark = prefs.showRemark() ? prefs.remark() : "";

        // 自定义地点时，水印上的地点名与经纬度同时取自定义值
        final double wLat = effLat(), wLon = effLon();

        // 若地址没解析出来、地点只能回退成坐标，且坐标行本就要显示，则不再重复画一行
        String loc = currentLocationText();
        boolean coordShown = prefs.showCoord() && (wLat != 0d || wLon != 0d);
        if (coordShown && loc.equals(TimeUtils.coordShort(wLat, wLon))) {
            loc = "";
        }
        d.location = prefs.showLocation() ? loc : "";
        d.coord = prefs.showCoord() ? TimeUtils.coord(wLat, wLon) : "";
        // 手动填写的天气优先于联网结果
        String manualW = prefs.manualWeather();
        d.weather = prefs.showWeather() ? (manualW.isEmpty() ? weather : manualW) : "";
        overlay.refresh();
        updateChips();
    }

    /** 水印实际使用的纬度：自定义地点优先 */
    private double effLat() {
        return prefs.customPlace() && prefs.hasPlaceCoord() ? prefs.placeLat() : lat;
    }

    /** 水印实际使用的经度：自定义地点优先 */
    private double effLon() {
        return prefs.customPlace() && prefs.hasPlaceCoord() ? prefs.placeLon() : lon;
    }

    private String currentLocationText() {
        if (prefs.customPlace()) {
            String manual = prefs.manualLocation();
            if (manual.length() > 0) return manual;
        }
        if (address.length() > 0) return address;
        double wLat = effLat(), wLon = effLon();
        if (wLat != 0d || wLon != 0d) return TimeUtils.coordShort(wLat, wLon);
        return "";
    }

    private void updateChips() {
        chipTitle.setText(prefs.title().isEmpty() ? "标题" : prefs.title());
        String loc = currentLocationText();
        chipLocation.setText(loc.isEmpty() ? "添加地点" : loc);
        chipRemark.setText(prefs.remark().isEmpty() ? "备注" : prefs.remark().replace("\n", " "));
        chipPerson.setText(prefs.person().isEmpty() ? "打卡人" : prefs.person());
        chipProject.setText(prefs.project().isEmpty() ? "单位" : prefs.project());

        tvTitle.setText(prefs.title().isEmpty() ? "现场取证" : prefs.title());
    }

    /**
     * 老版本导入的 logo 是 512×512 的方形画布（四周透明留白），
     * 在按真实宽高比排版的水印里会显得又小又空。启动时在后台做一次性迁移，
     * 把方形留白裁掉，内容本身不动。版本号已是最新时这里等于空跑。
     */
    private void migrateLogosAsync() {
        new Thread(() -> {
            LogoStore.ensureModernLogos(this);
            runOnUiThread(() -> {
                if (!LogoStore.NONE.equals(prefs.logoId())) {
                    loadedLogoId = null;
                    reloadLogo();
                }
            });
        }, "logo-migrate").start();
    }

    private void reloadLogo() {
        String id = prefs.logoId();
        if (id.equals(loadedLogoId) && (LogoStore.NONE.equals(id) || overlay.data().logo != null)) {
            return;
        }
        loadedLogoId = id;
        if (LogoStore.NONE.equals(id) || id.isEmpty()) {
            overlay.data().logo = null;
            imgLogo.setImageResource(R.drawable.ic_logo);
            imgLogo.setColorFilter(0xFFFFC400);
        } else {
            Bitmap b = LogoStore.load(this, id, 512);
            overlay.data().logo = b;
            if (b != null) {
                imgLogo.setColorFilter(null);
                imgLogo.setImageBitmap(b);
            } else {
                imgLogo.setImageResource(R.drawable.ic_logo);
                imgLogo.setColorFilter(0xFFFFC400);
            }
        }
        overlay.refresh();
    }

    private void updateRecordCount() {
        int n = records.list(999).size();
        tvRecordCount.setText(n > 0 ? ("记录 " + n) : "记录");
    }

    // ============================================================
    // 定位
    // ============================================================
    private void requestLocationIfAllowed() {
        // 自定义地点时不再请求 GPS：地点名和坐标都以用户设定为准
        if (prefs.customPlace() && prefs.hasPlaceCoord()) {
            updateSubForCustomPlace();
            return;
        }
        if (!hasLocationPermission()) {
            tvSub.setText("未授权定位 · 可点「地点」手动指定");
            return;
        }
        if (locating) return;
        locating = true;
        tvSub.setText("定位中…");

        LocationHelper.request(this, new LocationHelper.Callback() {
            @Override
            public void onResult(double la, double lo, String addr, String source) {
                locating = false;
                lat = la;
                lon = lo;
                address = (addr == null) ? "" : addr;
                syncOverlay();
                if (prefs.customPlace()) {
                    updateSubForCustomPlace();
                } else {
                    tvSub.setText(address.isEmpty()
                            ? TimeUtils.coordShort(lat, lon)
                            : address);
                }
                if (prefs.weatherOnline()) fetchWeather();
            }

            @Override
            public void onError(String message) {
                locating = false;
                tvSub.setText(message);
                syncOverlay();
            }
        }, 12000L);
    }

    /** 顶栏副标题：自定义地点时显示地点名 + 生效中的坐标 */
    private void updateSubForCustomPlace() {
        String name = prefs.manualLocation();
        tvSub.setText(String.format(java.util.Locale.US, "%s · %.4f, %.4f",
                name.isEmpty() ? "自定义地点" : name, prefs.placeLat(), prefs.placeLon()));
    }

    private void fetchWeather() {
        // 已手动填写天气时不再联网获取
        if (!prefs.manualWeather().isEmpty()) return;
        final double la = effLat(), lo = effLon();
        if (la == 0d && lo == 0d) return;
        io.execute(() -> {
            String w = WeatherHelper.fetch(la, lo);
            if (w != null) {
                runOnUiThread(() -> {
                    weather = w;
                    syncOverlay();
                });
            }
        });
    }

    // ============================================================
    // 水印时间（点水印直接改）
    // ============================================================
    private void editWatermarkTime() {
        TimeEditDialog.show(this, prefs,
                "点取景画面上的水印就能改这里。只改水印上显示的时间，"
                        + "照片文件名和拍照记录里的时间仍是真实拍摄时刻。",
                () -> {
                    overlay.setTimeMode(prefs.timeMode(), prefs.fixedTime());
                    if (prefs.timeMode() == Prefs.TIME_FIXED) {
                        toast("水印时间已固定为 " + TimeUtils.displayDateTime(prefs.fixedTime()));
                    } else {
                        toast("水印时间：跟随拍照时刻");
                    }
                });
    }

    // ============================================================
    // 输入
    // ============================================================
    private void prompt(String title, String initial, boolean multiline, final OnText cb) {
        final EditText et = new EditText(this);
        et.setText(initial);
        et.setSelection(et.getText().length());
        et.setSingleLine(!multiline);
        et.setMinLines(multiline ? 3 : 1);
        et.setMaxLines(multiline ? 5 : 1);
        et.setGravity(multiline ? Gravity.TOP | Gravity.START : Gravity.CENTER_VERTICAL);
        et.setTextColor(0xFFF2F5F9);
        et.setHintTextColor(0xFF5F6B7C);

        FrameLayout wrap = new FrameLayout(this);
        int pad = dp(20);
        wrap.setPadding(pad, dp(6), pad, 0);
        wrap.addView(et, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                multiline ? dp(110) : FrameLayout.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(wrap)
                .setPositiveButton("确定", (d, w) -> cb.on(et.getText().toString().trim()))
                .setNegativeButton("取消", null)
                .setNeutralButton("清空", (d, w) -> cb.on(""))
                .show();
    }

    // ============================================================
    // 主屏模板横滑条（今日水印式：真实效果缩略图摆在取景框下面）
    // ============================================================
    private void refreshTemplateRow() {
        if (templateRow == null) return;
        templateRow.removeAllViews();
        for (int i = 0; i < WatermarkStyle.TEMPLATE_COUNT; i++) {
            templateRow.addView(makeThumbTile(i));
        }
        templateRow.addView(makeGearTile());
    }

    /** 单个模板缩略图块：效果小卡 + 名称 */
    private View makeThumbTile(final int t) {
        boolean sel = overlay.style().template == t;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);

        ImageView iv = new ImageView(this);
        iv.setImageBitmap(makeTemplateThumb(t));
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        iv.setBackgroundResource(sel ? R.drawable.bg_tile_on : R.drawable.bg_tile);
        iv.setPadding(dp(3), dp(3), dp(3), dp(3));
        iv.setClickable(true);
        iv.setFocusable(true);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(72), dp(96));
        ip.rightMargin = dp(8);
        iv.setLayoutParams(ip);
        iv.setContentDescription(WatermarkStyle.templateName(t));
        iv.setOnClickListener(v -> {
            overlay.style().applyTemplate(t);
            prefs.setTemplate(t);
            prefs.setPosition(overlay.style().position);
            prefs.setFullWidth(overlay.style().fullWidth);
            overlay.refresh();
            refreshTemplateRow();
        });

        TextView name = new TextView(this);
        name.setText(WatermarkStyle.templateName(t));
        name.setTextSize(10f);
        name.setGravity(Gravity.CENTER);
        name.setTextColor(sel ? 0xFFFFC400 : 0xFF98A4B5);
        name.setPadding(dp(2), dp(3), dp(2), 0);

        box.addView(iv);
        box.addView(name);
        return box;
    }

    /** 末尾「样式」入口：位置 / 缩放 / 透明度精调 */
    private View makeGearTile() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);

        ImageView iv = new ImageView(this);
        iv.setImageResource(R.drawable.ic_settings);
        iv.setScaleType(ImageView.ScaleType.CENTER);
        iv.setBackgroundResource(R.drawable.bg_tile);
        iv.setPadding(dp(3), dp(3), dp(3), dp(3));
        iv.setColorFilter(0xFF98A4B5);
        iv.setClickable(true);
        iv.setFocusable(true);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(72), dp(96));
        ip.rightMargin = dp(8);
        iv.setLayoutParams(ip);
        iv.setContentDescription("水印样式设置");
        iv.setOnClickListener(v -> showStyleSheet());

        TextView name = new TextView(this);
        name.setText("样式");
        name.setTextSize(10f);
        name.setGravity(Gravity.CENTER);
        name.setTextColor(0xFF98A4B5);
        name.setPadding(dp(2), dp(3), dp(2), 0);

        box.addView(iv);
        box.addView(name);
        return box;
    }

    /**
     * 渲染某个模板的效果缩略图：
     * 深灰渐变模拟照片背景，内部用与预览/成片完全相同的 WatermarkPainter 画示例水印。
     */
    private Bitmap makeTemplateThumb(int t) {
        Bitmap cached = thumbCache.get(t);
        if (cached != null) return cached;

        final int w = 360, h = 480;
        Bitmap raw = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(raw);
        Paint bgP = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgP.setShader(new LinearGradient(0, 0, w, h,
                0xFF39414D, 0xFF161B23, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, bgP);

        WatermarkData d = new WatermarkData();
        d.title = "现场取证";
        d.timeText = "09:41";
        d.dateText = "2026-11-13 周五";
        d.location = "示例工地 3 号楼";
        d.person = "张三";

        WatermarkStyle s = WatermarkStyle.of(t);
        WatermarkPainter.draw(c, w, h, d, s);

        // 圆角裁切
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas oc = new Canvas(out);
        Path p = new Path();
        float rx = dp(11);
        p.addRoundRect(0, 0, w, h, rx, rx, Path.Direction.CW);
        oc.clipPath(p);
        oc.drawBitmap(raw, 0, 0, null);
        raw.recycle();

        thumbCache.put(t, out);
        return out;
    }

    // ============================================================
    // 样式面板
    // ============================================================
    private void showStyleSheet() {
        final Dialog dlg = new Dialog(this);
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dlg.setContentView(R.layout.dialog_style);

        Window w = dlg.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
            w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams lp = w.getAttributes();
            lp.dimAmount = 0.6f;
            w.setAttributes(lp);
        }

        final LinearLayout templateRow = dlg.findViewById(R.id.templateRow);
        final LinearLayout positionRow = dlg.findViewById(R.id.positionRow);
        final SeekBar seekScale = dlg.findViewById(R.id.seekScale);
        final SeekBar seekOpacity = dlg.findViewById(R.id.seekOpacity);
        final TextView tvScaleVal = dlg.findViewById(R.id.tvScaleVal);
        final TextView tvOpacityVal = dlg.findViewById(R.id.tvOpacityVal);

        fillTemplates(templateRow, positionRow);
        fillPositions(positionRow, templateRow);

        seekScale.setMax(150);
        seekScale.setProgress(Math.round((overlay.style().scale - 0.5f) * 100f));
        tvScaleVal.setText(Math.round(overlay.style().scale * 100) + "%");
        seekScale.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                float v = (50 + p) / 100f;
                overlay.style().scale = v;
                prefs.setScale(v);
                tvScaleVal.setText(Math.round(v * 100) + "%");
                overlay.refresh();
            }
        });

        seekOpacity.setMax(65);
        seekOpacity.setProgress(Math.round((overlay.style().opacity - 0.35f) * 100f));
        tvOpacityVal.setText(Math.round(overlay.style().opacity * 100) + "%");
        seekOpacity.setOnSeekBarChangeListener(new SimpleSeekListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                float v = (35 + p) / 100f;
                overlay.style().opacity = v;
                prefs.setOpacity(v);
                tvOpacityVal.setText(Math.round(v * 100) + "%");
                overlay.refresh();
            }
        });

        dlg.findViewById(R.id.btnDone).setOnClickListener(v -> {
            prefs.setTemplate(overlay.style().template);
            prefs.setPosition(overlay.style().position);
            prefs.setFullWidth(overlay.style().fullWidth);
            updateChips();
            dlg.dismiss();
        });

        dlg.show();
    }

    private void fillTemplates(final LinearLayout row, final LinearLayout posRow) {
        row.removeAllViews();
        for (int i = 0; i < 6; i++) {
            final int t = i;
            TextView tv = makeOption(WatermarkStyle.templateName(i),
                    overlay.style().template == i);
            tv.setOnClickListener(v -> {
                overlay.style().applyTemplate(t);
                prefs.setTemplate(t);
                overlay.refresh();
                fillTemplates(row, posRow);
                fillPositions(posRow, row);
                refreshTemplateRow();
            });
            row.addView(tv);
        }
    }

    private void fillPositions(final LinearLayout row, final LinearLayout templateRow) {
        row.removeAllViews();
        String[] names = {"自动", "左下", "右下", "左上", "右上"};
        int[] values = {
                WatermarkStyle.P_AUTO,
                WatermarkStyle.P_BOTTOM_LEFT,
                WatermarkStyle.P_BOTTOM_RIGHT,
                WatermarkStyle.P_TOP_LEFT,
                WatermarkStyle.P_TOP_RIGHT
        };
        for (int i = 0; i < names.length; i++) {
            final int pos = values[i];
            TextView tv = makeOption(names[i], overlay.style().position == pos);
            tv.setOnClickListener(v -> {
                overlay.style().position = pos;
                overlay.refresh();
                fillPositions(row, templateRow);
                fillTemplates(templateRow, row);
            });
            row.addView(tv);
        }
    }

    private TextView makeOption(String text, boolean selected) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(12f);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(selected ? 0xFFFFC400 : 0xFFD6DEEA);
        t.setBackgroundResource(selected ? R.drawable.bg_chip_on : R.drawable.bg_chip);
        t.setPadding(dp(13), dp(7), dp(13), dp(7));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(8);
        t.setLayoutParams(lp);
        t.setClickable(true);
        t.setFocusable(true);
        return t;
    }

    // ============================================================
    // 工具
    // ============================================================
    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private void vibrate() {
        try {
            Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (v == null) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(28, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate(28);
            }
        } catch (Exception ignored) {
        }
    }

    /** SeekBar 只关心进度变化 */
    private abstract static class SimpleSeekListener implements SeekBar.OnSeekBarChangeListener {
        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    }
}
