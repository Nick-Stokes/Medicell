package com.sookmyung.medicell;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.IOException;

public class CameraView extends AppCompatActivity {

    private static final String TAG = "CameraView";

    private Uri       photoUri;
    private Bitmap    displayBitmap;
    private Handler   mainHandler;

    private EditText    etPrint;
    private CheckBox    cbApplyPrint;
    private ImageButton btnClearPrint;

    private static final String[] SKIP_KEYWORDS = {
            "분할선", "마크", "없음"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_view);

        mainHandler   = new Handler(Looper.getMainLooper());

        ImageView imagePreview = findViewById(R.id.iv_camera);
        Button    btnRetake    = findViewById(R.id.camera_retry);
        Button    btnNext      = findViewById(R.id.camera_next);
        etPrint       = findViewById(R.id.etPrint);
        cbApplyPrint  = findViewById(R.id.cbApplyPrint);
        btnClearPrint = findViewById(R.id.btnClearPrint);

        // 지우기 버튼
        btnClearPrint.setOnClickListener(v -> {
            etPrint.setText("");
            cbApplyPrint.setChecked(false);
        });

        // ── 비트맵 로드 ────────────────────────────────────
        Bitmap pending = PillStorage.getPendingBitmap();

        if (pending != null) {
            displayBitmap = pending;
            imagePreview.setImageBitmap(displayBitmap);
            PillStorage.setPendingBitmap(displayBitmap);
            runOcr(displayBitmap);

        } else {
            String uriStr = getIntent().getStringExtra("photo_uri");
            if (uriStr != null && !uriStr.isEmpty()) {
                photoUri = Uri.parse(uriStr);
                new Thread(() -> {
                    try {
                        Bitmap bmp;
                        if (Build.VERSION.SDK_INT
                                >= Build.VERSION_CODES.P) {
                            ImageDecoder.Source src =
                                    ImageDecoder.createSource(
                                            getContentResolver(),
                                            photoUri);
                            bmp = ImageDecoder.decodeBitmap(src,
                                    (decoder, info, source) ->
                                            decoder.setAllocator(
                                                    ImageDecoder
                                                            .ALLOCATOR_SOFTWARE));
                        } else {
                            bmp = MediaStore.Images.Media.getBitmap(
                                    getContentResolver(), photoUri);
                        }
                        Bitmap finalBmp = bmp;
                        mainHandler.post(() -> {
                            displayBitmap = finalBmp;
                            imagePreview.setImageBitmap(
                                    displayBitmap);
                            runOcr(displayBitmap);
                        });
                    } catch (IOException e) {
                        e.printStackTrace();
                        mainHandler.post(() ->
                                Toast.makeText(this,
                                        "이미지 로드 실패",
                                        Toast.LENGTH_SHORT).show());
                    }
                }).start();
            } else {
                Toast.makeText(this, "사진이 없습니다.",
                        Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
        }

        // 다시 찍기
        btnRetake.setOnClickListener(v -> {
            PillStorage.setPendingBitmap(null);  // ← 추가
            finish();
        });

        // 인식하기 → PillResultConfirm으로 이동
        btnNext.setOnClickListener(v -> {
            if (displayBitmap == null) {
                Toast.makeText(this, "사진이 없습니다.",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            // 각인 필터 적용 여부
            String userPrint = "";
            if (cbApplyPrint.isChecked()) {
                userPrint = etPrint.getText()
                        .toString().trim();
            }

            // PillStorage에 비트맵 저장
            PillStorage.setPendingBitmap(displayBitmap);

            Intent i = new Intent(CameraView.this,
                    PillResultConfirm.class);
            if (photoUri != null)
                i.putExtra("photo_uri", photoUri.toString());
            i.putExtra("user_print", userPrint);
            startActivity(i);
        });
    }


    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // OCR
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private void runOcr(Bitmap bitmap) {
        if (bitmap == null) return;

        InputImage image = InputImage.fromBitmap(bitmap, 0);
        TextRecognizer recognizer = TextRecognition.getClient(
                TextRecognizerOptions.DEFAULT_OPTIONS);

        recognizer.process(image)
                .addOnSuccessListener(result -> {
                    String text = result.getText().trim();
                    if (!text.isEmpty()) {
                        text = cleanOcrText(text);
                        if (!text.isEmpty()) {
                            etPrint.setText(text);
                            cbApplyPrint.setChecked(true);
                        }
                    }
                })
                .addOnFailureListener(e ->
                        android.util.Log.w(TAG,
                                "OCR 실패: " + e.getMessage()));
    }

    private String cleanOcrText(String raw) {
        for (String kw : SKIP_KEYWORDS) {
            raw = raw.replace(kw, "");
        }
        raw = raw.replace("\n", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return raw;
    }
}