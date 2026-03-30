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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;

public class CameraView extends AppCompatActivity {

    private Uri    photoUri;
    private Bitmap displayBitmap;
    private Handler mainHandler;

    private String selectedColor1 = "전체";
    private String selectedColor2 = "전체";

    private static final String[] COLOR_LIST = {
            "전체",
            "하양", "노랑", "주황", "분홍", "빨강",
            "갈색", "연두", "초록", "청록", "파랑",
            "남색", "자주", "보라", "회색", "검정", "투명"
    };

    private static final String[] COLOR_LIST2 = {
            "전체", "없음",
            "하양", "노랑", "주황", "분홍", "빨강",
            "갈색", "연두", "초록", "청록", "파랑",
            "남색", "자주", "보라", "회색", "검정", "투명"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_view);

        mainHandler = new Handler(Looper.getMainLooper());

        ImageView imagePreview = findViewById(R.id.iv_camera);
        Button    btnRetake    = findViewById(R.id.camera_retry);
        Button    btnNext      = findViewById(R.id.camera_next);
        Spinner   spinner1     = findViewById(R.id.spinner_color1);
        Spinner   spinner2     = findViewById(R.id.spinner_color2);

        // ── Spinner 설정 ───────────────────────────────────
        ArrayAdapter<String> adapter1 = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, COLOR_LIST);
        adapter1.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        spinner1.setAdapter(adapter1);
        spinner1.setSelection(0);

        ArrayAdapter<String> adapter2 = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, COLOR_LIST2);
        adapter2.setDropDownViewResource(
                android.R.layout.simple_spinner_dropdown_item);
        spinner2.setAdapter(adapter2);
        spinner2.setSelection(0);

        spinner1.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent,
                                               android.view.View view, int position, long id) {
                        selectedColor1 = COLOR_LIST[position];
                    }
                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {}
                });

        spinner2.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent,
                                               android.view.View view, int position, long id) {
                        selectedColor2 = COLOR_LIST2[position];
                    }
                    @Override
                    public void onNothingSelected(AdapterView<?> parent) {}
                });

        // ── 비트맵 로드 ────────────────────────────────────
        Bitmap pending = PillStorage.getPendingBitmap();

        if (pending != null) {
            // 메모리 비트맵 → 바로 표시
            displayBitmap = pending;
            imagePreview.setImageBitmap(displayBitmap);
            PillStorage.setPendingBitmap(displayBitmap);

        } else {
            // URI → 백그라운드에서 로드
            String uriStr = getIntent().getStringExtra("photo_uri");
            if (uriStr != null && !uriStr.isEmpty()) {
                photoUri = Uri.parse(uriStr);
                new Thread(() -> {
                    try {
                        Bitmap bmp;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            ImageDecoder.Source src =
                                    ImageDecoder.createSource(
                                            getContentResolver(), photoUri);
                            bmp = ImageDecoder.decodeBitmap(src,
                                    (decoder, info, source) ->
                                            decoder.setAllocator(
                                                    ImageDecoder.ALLOCATOR_SOFTWARE));
                        } else {
                            bmp = MediaStore.Images.Media.getBitmap(
                                    getContentResolver(), photoUri);
                        }
                        Bitmap finalBmp = bmp;
                        mainHandler.post(() -> {
                            displayBitmap = finalBmp;
                            imagePreview.setImageBitmap(displayBitmap);
                        });
                    } catch (IOException e) {
                        e.printStackTrace();
                        mainHandler.post(() ->
                                Toast.makeText(this, "이미지 로드 실패",
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

        btnRetake.setOnClickListener(v -> finish());

        btnNext.setOnClickListener(v -> {
            Intent i = new Intent(CameraView.this, PillResult.class);
            if (photoUri != null)
                i.putExtra("photo_uri", photoUri.toString());
            i.putExtra("color1", selectedColor1);
            i.putExtra("color2", selectedColor2);
            startActivity(i);
        });
    }
}