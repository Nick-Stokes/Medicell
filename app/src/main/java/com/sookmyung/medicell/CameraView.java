package com.sookmyung.medicell;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;

public class CameraView extends AppCompatActivity {

    private Uri    photoUri;
    private Bitmap displayBitmap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_view);

        ImageView imagePreview = findViewById(R.id.iv_camera);
        Button    btnRetake    = findViewById(R.id.camera_retry);
        Button    btnNext      = findViewById(R.id.camera_next);

        String  uriStr     = getIntent().getStringExtra("photo_uri");
        Bitmap  pending    = PillStorage.getPendingBitmap();

        if (pending != null) {
            // 메모리 비트맵 우선
            displayBitmap = pending;
            imagePreview.setImageBitmap(displayBitmap);
            // PillResult까지 전달하기 위해 다시 저장
            PillStorage.setPendingBitmap(displayBitmap);

        } else if (uriStr != null && !uriStr.isEmpty()) {
            // URI로 로드
            photoUri = Uri.parse(uriStr);
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.Source src = ImageDecoder.createSource(
                            getContentResolver(), photoUri);
                    displayBitmap = ImageDecoder.decodeBitmap(src,
                            (decoder, info, source) ->
                                    decoder.setAllocator(
                                            ImageDecoder.ALLOCATOR_SOFTWARE));
                } else {
                    displayBitmap = MediaStore.Images.Media.getBitmap(
                            getContentResolver(), photoUri);
                }
                imagePreview.setImageBitmap(displayBitmap);
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "이미지 로드 실패",
                        Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "사진이 없습니다.",
                    Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        btnRetake.setOnClickListener(v -> finish());

        btnNext.setOnClickListener(v -> {
            Intent i = new Intent(CameraView.this, PillResult.class);
            if (photoUri != null)
                i.putExtra("photo_uri", photoUri.toString());
            startActivity(i);
        });
    }
}