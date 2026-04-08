package com.sookmyung.medicell;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

public class SquareCamera extends AppCompatActivity {

    private static final String TAG               = "SquareCameraROI";
    private static final int    ANALYSIS_SIZE     = 224;
    private static final int    GUIDE_R           = 30;
    private static final int    FINAL_INPUT_SIZE  = 224;
    private static final float  GUIDE_MARGIN_RATIO = 0.10f;
    private static final float  MIN_CROP_RATIO     = 0.16f;
    private static final Size   CAPTURE_SIZE       = new Size(1280, 1280);

    private PreviewView          previewView;
    private GuideOverlayView     guideOverlay;
    private ImageCapture         imageCapture;
    private Executor             mainExecutor;
    private ProcessCameraProvider cameraProvider;  // ← 필드로 저장

    private long    okSinceMs   = 0L;
    private boolean isCapturing = false;

    private final float[] metrics = new float[3];

    private static final int WIN   = 6;
    private static final int ON_K  = 4;
    private static final int OFF_K = 2;

    private final boolean[] okWin = new boolean[WIN];
    private int     okPos    = 0;
    private int     okCount  = 0;
    private boolean stableOk = false;

    private final ActivityResultLauncher<String> reqCam =
            registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        if (granted) startCamera();
                        else Toast.makeText(this,
                                "카메라 권한이 필요합니다.",
                                Toast.LENGTH_SHORT).show();
                    });


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_square_camera);

        ViewCompat.setOnApplyWindowInsetsListener(
                findViewById(R.id.main), (v, insets) -> {
                    Insets sb = insets.getInsets(
                            WindowInsetsCompat.Type.systemBars());
                    v.setPadding(sb.left, sb.top, sb.right, sb.bottom);
                    return insets;
                });

        previewView  = findViewById(R.id.previewView);
        guideOverlay = findViewById(R.id.guideOverlay);
        mainExecutor = ContextCompat.getMainExecutor(this);

        float density      = getResources().getDisplayMetrics().density;
        int   previewBoxPx = (int) (300f * density);
        int   displayR     = (int) Math.round(
                (GUIDE_R / (double) ANALYSIS_SIZE) * previewBoxPx);

        guideOverlay.setRadiusPx(displayR);
        guideOverlay.setOk(false);

        Log.d(TAG, "GUIDE_R=" + GUIDE_R + "  displayR=" + displayR);
        reqCam.launch(Manifest.permission.CAMERA);
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Activity 종료 시 카메라 해제
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
            cameraProvider = null;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        isCapturing = false;
        okSinceMs   = 0L;
        okCount     = 0;
        okPos       = 0;
        stableOk    = false;
        for (int i = 0; i < WIN; i++) okWin[i] = false;
        guideOverlay.setOk(false);
        startCamera();
    }


    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future =
                ProcessCameraProvider.getInstance(this);

        future.addListener(() -> {
            try {
                cameraProvider = future.get();  // ← 필드에 저장

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(
                                ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setTargetResolution(CAPTURE_SIZE)
                        .build();

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setTargetResolution(
                                new Size(ANALYSIS_SIZE, ANALYSIS_SIZE))
                        .setBackpressureStrategy(
                                ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(
                                ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build();

                imageAnalysis.setAnalyzer(mainExecutor, image -> {
                    try {
                        if (isCapturing) { image.close(); return; }
                        byte[] rgba = toRgbaByteArray(image);
                        int okInt = NativeBridge.analyzeRoi(
                                rgba, image.getWidth(), image.getHeight(),
                                GUIDE_R, metrics);
                        onAlignedResult(okInt == 1);
                    } catch (Exception e) {
                        Log.e(TAG, "Analyzer error", e);
                        onAlignedResult(false);
                    } finally {
                        image.close();
                    }
                });

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview, imageCapture, imageAnalysis);

            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "카메라 시작 실패", e);
                Toast.makeText(this, "카메라 시작 실패",
                        Toast.LENGTH_SHORT).show();
            }
        }, mainExecutor);
    }


    private void onAlignedResult(boolean alignedRaw) {
        boolean old = okWin[okPos];
        if (old) okCount--;
        okWin[okPos] = alignedRaw;
        if (alignedRaw) okCount++;
        okPos = (okPos + 1) % WIN;

        boolean newStable = stableOk;
        if (!stableOk) { if (okCount >= ON_K)  newStable = true;  }
        else           { if (okCount <= OFF_K) newStable = false; }
        stableOk = newStable;

        runOnUiThread(() -> {
            guideOverlay.setOk(stableOk);
            long now = System.currentTimeMillis();
            if (!stableOk) { okSinceMs = 0L; return; }
            if (isCapturing) return;
            if (okSinceMs == 0L) okSinceMs = now;

            // 0.3초 → 0.1초로 변경
            if (now - okSinceMs >= 100L) {
                isCapturing = true;
                okSinceMs   = 0L;
                captureAndSendFinal224();
            }
        });
    }


    private void captureAndSendFinal224() {
        if (imageCapture == null) {
            isCapturing = false;
            return;
        }

        File orig = new File(getCacheDir(),
                "cap_" + System.currentTimeMillis() + ".jpg");
        ImageCapture.OutputFileOptions opts =
                new ImageCapture.OutputFileOptions.Builder(orig).build();

        imageCapture.takePicture(opts, mainExecutor,
                new ImageCapture.OnImageSavedCallback() {

                    @Override
                    public void onImageSaved(
                            @NonNull ImageCapture.OutputFileResults results) {
                        try {
                            Bitmap captured = loadBitmap(orig);
                            if (captured == null)
                                throw new IOException("decode failed");

                            Log.d(TAG, "captured: "
                                    + captured.getWidth() + "x"
                                    + captured.getHeight());

                            Bitmap square    = cropCenterSquare(captured);
                            Bitmap guideCrop = cropByGuideRegionHighRes(square);
                            Bitmap final224  = resizeBitmap(
                                    guideCrop, FINAL_INPUT_SIZE, FINAL_INPUT_SIZE);
                            final224 = final224.copy(
                                    Bitmap.Config.ARGB_8888, false);

                            Log.d(TAG, "final224: "
                                    + final224.getWidth() + "x"
                                    + final224.getHeight());

                            // 갤러리 저장
                            try {
                                saveToPictures(final224, "SquareCamCrop224");
                                Log.d(TAG, "갤러리 저장 완료");
                            } catch (Exception e) {
                                Log.w(TAG, "갤러리 저장 실패: " + e.getMessage());
                            }

                            // 메모리로 전달
                            PillStorage.setPendingBitmap(final224);

                            // ── 카메라 해제 후 이동 ────────────────────
                            if (cameraProvider != null) {
                                cameraProvider.unbindAll();
                            }

                            Intent i = new Intent(
                                    SquareCamera.this, CameraView.class);
                            i.putExtra("is_auto_crop", true);
                            startActivity(i);

                        } catch (Exception e) {
                            Log.e(TAG, "촬영 처리 실패", e);
                            // Toast 제거 → 로그만 남김
                        } finally {
                            isCapturing = false;
                        }
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException e) {
                        Log.e(TAG, "촬영 실패", e);
                        // Toast 제거 → 로그만 남김
                        isCapturing = false;
                    }
                });
    }


    private Bitmap cropByGuideRegionHighRes(Bitmap square) {
        int   w                  = square.getWidth();
        int   h                  = square.getHeight();
        int   cx                 = w / 2;
        int   cy                 = h / 2;
        float guideDiameterRatio = (2f * GUIDE_R) / (float) ANALYSIS_SIZE;
        float baseCropSize       = w * guideDiameterRatio;
        int   cropSize           = Math.round(
                baseCropSize * (1.0f + GUIDE_MARGIN_RATIO));

        int minCropSize = Math.round(w * MIN_CROP_RATIO);
        cropSize = Math.max(cropSize, minCropSize);
        cropSize = Math.min(cropSize, Math.min(w, h));

        int left = Math.max(0, cx - cropSize / 2);
        int top  = Math.max(0, cy - cropSize / 2);
        if (left + cropSize > w) left = w - cropSize;
        if (top  + cropSize > h) top  = h - cropSize;

        Log.d(TAG, "cropHighRes: w=" + w
                + " cropSize=" + cropSize
                + " left=" + left + " top=" + top);

        return Bitmap.createBitmap(square, left, top, cropSize, cropSize);
    }


    private byte[] toRgbaByteArray(ImageProxy image) {
        ImageProxy.PlaneProxy plane      = image.getPlanes()[0];
        ByteBuffer             buf       = plane.getBuffer();
        int                    width     = image.getWidth();
        int                    height    = image.getHeight();
        int                    rowStride = plane.getRowStride();
        int                    pixStride = plane.getPixelStride();

        byte[] out    = new byte[width * height * 4];
        byte[] row    = new byte[rowStride];
        int    outIdx = 0, pos = 0;

        for (int y = 0; y < height; y++) {
            buf.position(pos);
            buf.get(row, 0, rowStride);
            int rowIdx = 0;
            for (int x = 0; x < width; x++) {
                out[outIdx++] = row[rowIdx];
                out[outIdx++] = row[rowIdx + 1];
                out[outIdx++] = row[rowIdx + 2];
                out[outIdx++] = row[rowIdx + 3];
                rowIdx += pixStride;
            }
            pos += rowStride;
        }
        return out;
    }

    private Bitmap loadBitmap(File f) throws IOException {
        Uri uri = Uri.fromFile(f);
        if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.Source src =
                    ImageDecoder.createSource(getContentResolver(), uri);
            return ImageDecoder.decodeBitmap(src,
                    (decoder, info, source) -> decoder.setAllocator(
                            ImageDecoder.ALLOCATOR_SOFTWARE));
        } else {
            return MediaStore.Images.Media.getBitmap(
                    getContentResolver(), uri);
        }
    }

    private Bitmap cropCenterSquare(Bitmap src) {
        int w    = src.getWidth();
        int h    = src.getHeight();
        int size = Math.min(w, h);
        int x    = (w - size) / 2;
        int y    = (h - size) / 2;
        return Bitmap.createBitmap(src, x, y, size, size);
    }

    private Bitmap resizeBitmap(Bitmap src, int dstW, int dstH) {
        return Bitmap.createScaledBitmap(src, dstW, dstH, true);
    }

    private Uri saveToPictures(Bitmap bmp, String subDir) throws IOException {
        String          name = "SQ_" + System.currentTimeMillis() + ".jpg";
        ContentResolver cr   = getContentResolver();

        ContentValues cv = new ContentValues();
        cv.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        cv.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            cv.put(MediaStore.Images.Media.RELATIVE_PATH,
                    "Pictures/" + subDir + "/");
        }

        Uri uri = cr.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
        if (uri == null) throw new IOException("insert failed");

        try (OutputStream os = cr.openOutputStream(uri)) {
            if (os == null)
                throw new IOException("openOutputStream null");
            if (!bmp.compress(Bitmap.CompressFormat.JPEG, 95, os))
                throw new IOException("compress failed");
            os.flush();
        }
        return uri;
    }
}