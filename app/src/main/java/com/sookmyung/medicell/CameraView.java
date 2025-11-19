package com.sookmyung.medicell;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.IOException;
import java.io.InputStream;

public class CameraView extends AppCompatActivity {

    private Uri photoUri;

    @RequiresApi(api = Build.VERSION_CODES.N)
    @SuppressLint("WrongThread")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_camera_view);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        ImageView imagePreview = findViewById(R.id.iv_camera);
        Button btnRetake = findViewById(R.id.camera_retry);
        Button btnNext = findViewById(R.id.camera_next);

        String uriStr = getIntent().getStringExtra("photo_uri");
        if (uriStr == null || uriStr.isEmpty()){
            Toast.makeText(this,"사진 Uri가 없어요.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        photoUri = Uri.parse(uriStr);

        try{
            Bitmap bmp;
            if (Build.VERSION.SDK_INT >= 28){
                ImageDecoder.Source src = ImageDecoder.createSource(getContentResolver(), photoUri);
                bmp = ImageDecoder.decodeBitmap(src);
            }else {
                bmp = MediaStore.Images.Media.getBitmap(getContentResolver(), photoUri);

            }
            imagePreview.setImageBitmap(bmp);
        }catch (IOException e){
            e.printStackTrace();
            Toast.makeText(this, "이미지 로드 실패", Toast.LENGTH_SHORT).show();
        }

        btnRetake.setOnClickListener(v -> { finish();});


        btnNext.setOnClickListener(v -> {
            Intent i = new Intent(CameraView.this, PillResult.class);
            i.putExtra("photo_uri",photoUri.toString());
            startActivity(i);
        });

    }


}