package com.ladno.finance;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.Image;
import android.os.Bundle;
import android.util.Size;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.ComponentActivity;
import androidx.annotation.OptIn;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.resolutionselector.ResolutionSelector;
import androidx.camera.core.resolutionselector.ResolutionStrategy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.ladno.finance.core.ContractException;
import com.ladno.finance.core.FiscalQrParser;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Live receipt QR scanner. Frames are analysed on-device by the bundled ML Kit model and
 * dropped immediately; nothing is stored. The camera runs only while this screen is open.
 */
public final class QrScanActivity extends ComponentActivity {
    static final String EXTRA_PAYLOAD = "com.ladno.finance.QR_PAYLOAD";
    static final String EXTRA_DENIED = "com.ladno.finance.CAMERA_DENIED";
    /** The user chose to photograph a receipt without a QR instead. */
    static final int RESULT_PHOTO = RESULT_FIRST_USER + 1;
    private static final int PERMISSION = 51;

    private final AtomicBoolean delivered = new AtomicBoolean();
    private ExecutorService analysis;
    private BarcodeScanner scanner;
    private PreviewView preview;
    private TextView hint;
    private Camera camera;
    private boolean torch;
    private long lastHintChange;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(layout());
        scanner = BarcodeScanning.getClient(new BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE).build());
        analysis = Executors.newSingleThreadExecutor(runnable -> new Thread(runnable, "ladno-qr-scan"));
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera();
        else requestPermissions(new String[]{Manifest.permission.CAMERA}, PERMISSION);
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request != PERMISSION) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startCamera();
        else {
            setResult(RESULT_CANCELED, new Intent().putExtra(EXTRA_DENIED, true));
            finish();
        }
    }

    private View layout() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        preview = new PreviewView(this);
        preview.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);
        root.addView(preview, new FrameLayout.LayoutParams(-1, -1));

        View frame = new View(this);
        GradientDrawable border = new GradientDrawable();
        border.setStroke(dp(3), Color.WHITE);
        border.setCornerRadius(dp(20));
        frame.setBackground(border);
        FrameLayout.LayoutParams frameParams = new FrameLayout.LayoutParams(dp(260), dp(260), Gravity.CENTER);
        root.addView(frame, frameParams);

        hint = new TextView(this);
        hint.setText("Наведите камеру на QR-код чека");
        hint.setTextColor(Color.WHITE);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(dp(24), dp(56), dp(24), dp(16));
        hint.setBackgroundColor(Color.argb(140, 0, 0, 0));
        root.addView(hint, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.VERTICAL);
        buttons.setPadding(dp(20), dp(16), dp(20), dp(36));
        buttons.setBackgroundColor(Color.argb(140, 0, 0, 0));
        Button light = button("Фонарик");
        light.setOnClickListener(v -> toggleTorch());
        Button photo = button("Чек без QR — сфотографировать");
        photo.setOnClickListener(v -> { if (delivered.compareAndSet(false, true)) { setResult(RESULT_PHOTO); finish(); } });
        Button cancel = button("Отмена");
        cancel.setOnClickListener(v -> { if (delivered.compareAndSet(false, true)) { setResult(RESULT_CANCELED); finish(); } });
        buttons.addView(light);
        buttons.addView(photo);
        buttons.addView(cancel);
        root.addView(buttons, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
        return root;
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(8);
        button.setLayoutParams(params);
        return button;
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            if (isFinishing() || isDestroyed()) return;
            try {
                ProcessCameraProvider provider = future.get();
                Preview shown = new Preview.Builder().build();
                shown.setSurfaceProvider(preview.getSurfaceProvider());
                // Small, faint thermal-paper QR codes need more than the default 640x480 frames.
                ResolutionSelector resolution = new ResolutionSelector.Builder()
                    .setResolutionStrategy(new ResolutionStrategy(new Size(1920, 1080),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)).build();
                ImageAnalysis frames = new ImageAnalysis.Builder()
                    .setResolutionSelector(resolution)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();
                frames.setAnalyzer(analysis, this::analyze);
                provider.unbindAll();
                camera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, shown, frames);
            } catch (Exception | LinkageError e) {
                hint.setText("Не удалось открыть камеру. Закройте другие приложения с камерой и попробуйте снова.");
            }
        }, androidx.core.content.ContextCompat.getMainExecutor(this));
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void analyze(ImageProxy frame) {
        Image image = frame.getImage();
        if (image == null || delivered.get()) { frame.close(); return; }
        InputImage input = InputImage.fromMediaImage(image, frame.getImageInfo().getRotationDegrees());
        scanner.process(input)
            .addOnSuccessListener(this::found)
            .addOnCompleteListener(task -> frame.close());
    }

    private void found(List<Barcode> codes) {
        if (codes == null || codes.isEmpty() || delivered.get()) return;
        for (Barcode code : codes) {
            String raw = code.getRawValue();
            if (raw == null) continue;
            try {
                FiscalQrParser.select(Collections.singletonList(raw));
            } catch (ContractException e) {
                showHint("Это не QR кассового чека. Наведите на QR внизу чека.");
                continue;
            }
            if (delivered.compareAndSet(false, true)) {
                setResult(RESULT_OK, new Intent().putExtra(EXTRA_PAYLOAD, raw));
                finish();
            }
            return;
        }
    }

    private void showHint(String text) {
        long now = System.currentTimeMillis();
        if (now - lastHintChange < 1500) return;
        lastHintChange = now;
        hint.setText(text);
    }

    private void toggleTorch() {
        if (camera == null || !camera.getCameraInfo().hasFlashUnit()) return;
        torch = !torch;
        camera.getCameraControl().enableTorch(torch);
    }

    @Override protected void onDestroy() {
        if (scanner != null) scanner.close();
        if (analysis != null) analysis.shutdown();
        super.onDestroy();
    }
}
