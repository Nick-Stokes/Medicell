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
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

public class SquareCamera extends AppCompatActivity {

    private static final String TAG = "SquareCameraROI";

    // 분석 해상도(가볍게) + 가이드 반지름(224 기준)
    private static final int ANALYSIS_SIZE = 224;
    private static final int GUIDE_R = 40;

    // 촬영 해상도(저장은 기존대로)
    private static final Size CAPTURE_SIZE = new Size(1280, 1280);

    private PreviewView previewView;
    private GuideOverlayView guideOverlay;

    private ImageCapture imageCapture;
    private Executor mainExecutor;

    // 1초 유지 자동촬영용
    private long okSinceMs = 0L;
    private boolean isCapturing = false;

    // (선택) 디버그/튜닝용 지표 받기
    private final float[] metrics = new float[3]; // areaRatio, centerOffsetNorm, edgeDensity

    private static final int WIN = 6;     // 최근 10프레임
    private static final int ON_K = 4;     // 7개 이상 OK면 초록
    private static final int OFF_K = 2;    // 4개 이하 OK면 빨강 (히스테리시스)

    private final boolean[] okWin = new boolean[WIN];
    private int okPos = 0;
    private int okCount = 0;
    private boolean stableOk = false;


    private final ActivityResultLauncher<String> reqCam =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) startCamera();
                else Toast.makeText(this, "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_square_camera);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        previewView = findViewById(R.id.previewView);
        guideOverlay = findViewById(R.id.guideOverlay);

        mainExecutor = ContextCompat.getMainExecutor(this);

        // 오버레이 표시용 반지름: 프리뷰(300dp 정사각) 안에 적당히 보이게 스케일
        // 분석 기준 r=80(224 기준)을 화면 표시 크기에 맞춰 비율로 환산
        int displayR = (int) Math.round((GUIDE_R / (double) ANALYSIS_SIZE) * 300.0); // 300dp를 px로 안 바꾸는 "대략치"
        // ※ dp->px를 정확히 하려면 getResources().getDisplayMetrics().density 곱해서 계산하면 됨.
        // 지금은 처음 세팅이라 "대충" 보여주기만 하면 OK.
        guideOverlay.setRadiusPx(displayR);
        guideOverlay.setOk(false);

        // (선택) ABI 확인 로그
        Log.d(TAG, "SUPPORTED_ABIS = " + NativeBridge.abis());

        reqCam.launch(Manifest.permission.CAMERA);
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setTargetResolution(CAPTURE_SIZE)
                        .build();

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(ANALYSIS_SIZE, ANALYSIS_SIZE))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build();

                imageAnalysis.setAnalyzer(mainExecutor, image -> {
                    try {
                        if (isCapturing) {
                            image.close();
                            return;
                        }

                        // RGBA 연속 배열로 변환 (rowStride 정리)
                        byte[] rgba = toRgbaByteArray(image);

                        int okInt = NativeBridge.analyzeRoi(
                                rgba,
                                image.getWidth(),
                                image.getHeight(),
                                GUIDE_R,
                                metrics
                        );

                        boolean aligned = (okInt == 1);

                        // 튜닝할 때만 주석 해제
                        Log.d(TAG, "ok=" + aligned + " area=" + metrics[0] + " center=" + metrics[1] + " edge=" + metrics[2]);

                        onAlignedResult(aligned);

                    } catch (Exception e) {
                        onAlignedResult(false);
                    } finally {
                        image.close();
                    }
                });

                provider.unbindAll();
                provider.bindToLifecycle(
                        this,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture,
                        imageAnalysis
                );

            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
                Toast.makeText(this, "카메라 시작 실패", Toast.LENGTH_SHORT).show();
            }
        }, mainExecutor);
    }

    /** aligned 결과로 오버레이 색 변경 + 1초 유지되면 자동 촬영 */
    private void onAlignedResult(boolean alignedRaw) {
        // sliding window 업데이트
        boolean old = okWin[okPos];
        if (old) okCount--;
        okWin[okPos] = alignedRaw;
        if (alignedRaw) okCount++;
        okPos = (okPos + 1) % WIN;

        // 히스테리시스: 켜질 때/꺼질 때 기준을 다르게
        boolean newStable = stableOk;
        if (!stableOk) {
            if (okCount >= ON_K) newStable = true;
        } else {
            if (okCount <= OFF_K) newStable = false;
        }

        stableOk = newStable;

        runOnUiThread(() -> {
            guideOverlay.setOk(stableOk);

            long now = System.currentTimeMillis();
            if (!stableOk) {
                okSinceMs = 0L;
                return;
            }
            if (isCapturing) return;
            if (okSinceMs == 0L) okSinceMs = now;

            if (now - okSinceMs >= 300L) {
                isCapturing = true;
                okSinceMs = 0L;
                captureSquareAndSave();
            }
        });
    }

    /** RGBA_8888 ImageProxy → width*height*4 연속 byte[] */
    private byte[] toRgbaByteArray(ImageProxy image) {
        ImageProxy.PlaneProxy plane = image.getPlanes()[0];
        ByteBuffer buf = plane.getBuffer();

        int width = image.getWidth();
        int height = image.getHeight();

        int rowStride = plane.getRowStride();
        int pixelStride = plane.getPixelStride(); // 보통 4

        byte[] out = new byte[width * height * 4];

        // rowStride가 width*4보다 클 수 있어서 행 단위로 풀어서 복사
        byte[] row = new byte[rowStride];
        int outIndex = 0;
        int pos = 0;

        for (int y = 0; y < height; y++) {
            buf.position(pos);
            buf.get(row, 0, rowStride);

            int rowIndex = 0;
            for (int x = 0; x < width; x++) {
                int base = rowIndex;

                out[outIndex++] = row[base];       // R
                out[outIndex++] = row[base + 1];   // G
                out[outIndex++] = row[base + 2];   // B
                out[outIndex++] = row[base + 3];   // A

                rowIndex += pixelStride;
            }
            pos += rowStride;
        }
        return out;
    }

    /** 자동 촬영: 기존 로직 유지 (1280 캡처 → 중앙 crop → 저장 → CameraView로 이동) */
    private void captureSquareAndSave() {
        if (imageCapture == null) {
            Toast.makeText(this, "카메라 준비 중...", Toast.LENGTH_SHORT).show();
            isCapturing = false;
            return;
        }

        File orig = new File(getCacheDir(), "cap_" + System.currentTimeMillis() + ".jpg");
        ImageCapture.OutputFileOptions opts = new ImageCapture.OutputFileOptions.Builder(orig).build();

        imageCapture.takePicture(opts, mainExecutor, new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                try {
                    Bitmap bmp = loadBitmap(orig);
                    if (bmp == null) throw new IOException("decode failed");

                    Bitmap square = cropCenterSquare(bmp);
                    Uri saved = saveToPictures(square, "SquareCam");

                    Intent i = new Intent(SquareCamera.this, CameraView.class);
                    i.putExtra("photo_uri", saved.toString());
                    startActivity(i);

                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(SquareCamera.this, "촬영 처리 실패", Toast.LENGTH_SHORT).show();
                } finally {
                    // 다음 촬영 가능
                    isCapturing = false;
                }
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                exception.printStackTrace();
                Toast.makeText(SquareCamera.this, "촬영 실패", Toast.LENGTH_SHORT).show();
                isCapturing = false;
            }
        });
    }

    private Bitmap loadBitmap(File f) throws IOException {
        Uri uri = Uri.fromFile(f);
        if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.Source src = ImageDecoder.createSource(getContentResolver(), uri);
            return ImageDecoder.decodeBitmap(src);
        } else {
            return MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
        }
    }

    private Bitmap cropCenterSquare(Bitmap src) {
        int w = src.getWidth(), h = src.getHeight();
        int size = Math.min(w, h);
        int x = (w - size) / 2;
        int y = (h - size) / 2;
        return Bitmap.createBitmap(src, x, y, size, size);
    }

    private Uri saveToPictures(Bitmap bmp, String subDir) throws IOException {
        String name = "SQ_" + System.currentTimeMillis() + ".jpg";
        ContentResolver cr = getContentResolver();
        ContentValues cv = new ContentValues();
        cv.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        cv.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            cv.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/" + subDir + "/");
        }
        Uri uri = cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
        if (uri == null) throw new IOException("insert failed");
        try (FileOutputStream fos = (FileOutputStream) cr.openOutputStream(uri)) {
            bmp.compress(Bitmap.CompressFormat.JPEG, 95, fos);
        }
        return uri;
    }
}
