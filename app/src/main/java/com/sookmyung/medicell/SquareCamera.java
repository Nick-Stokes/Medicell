package com.sookmyung.medicell;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Size;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

public class SquareCamera extends AppCompatActivity {

    private PreviewView previewView;
    private ImageButton btnCapture;
    private ImageCapture imageCapture;
    private Executor mainExecutor;

    private final ActivityResultLauncher<String> reqCam = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {if (granted) startCamera(); else
        Toast.makeText(this, "카메라 권한이 필요합니다.",Toast.LENGTH_SHORT).show();
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
        btnCapture = findViewById(R.id.btnCapture);
        mainExecutor = ContextCompat.getMainExecutor(this);

        reqCam.launch(Manifest.permission.CAMERA);
        btnCapture.setOnClickListener(v -> captureSquareAndSave());
    }

    private void startCamera(){
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try{
                ProcessCameraProvider provider = future.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).setTargetResolution(new Size(1280,1280)).build();
                provider.unbindAll();
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture);

            }catch (ExecutionException | InterruptedException e){
                e.printStackTrace();
                Toast.makeText(this, "카메라 시작 실패", Toast.LENGTH_SHORT).show();
            }
        }, mainExecutor);
    }

    private  void captureSquareAndSave(){
        if (imageCapture == null){
            Toast.makeText(this, "카메라 준비 중...", Toast.LENGTH_SHORT).show();
            return;
        }
        File orig = new File(getCacheDir(), "cap_" + System.currentTimeMillis() + ".jpg");
        ImageCapture.OutputFileOptions opts = new ImageCapture.OutputFileOptions.Builder(orig).build();
        imageCapture.takePicture(opts, mainExecutor, new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                try{
                    Bitmap bmp = loadBitmap(orig);
                    if (bmp == null) throw new IOException("decode failed");
                    Bitmap square = cropCenterSquare(bmp);

                    Uri saved = saveToPictures(square, "SquareCam");

                    Intent i = new Intent(SquareCamera.this, CameraView.class);
                    i.putExtra("photo_uri", saved.toString());
                    startActivity(i);
                }catch (Exception e){
                    e.printStackTrace();
                    Toast.makeText(SquareCamera.this, "촬영 처리 실패", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                exception.printStackTrace();
                Toast.makeText(SquareCamera.this,"촬영 실패", Toast.LENGTH_SHORT).show();

            }
        });
    }

    private Bitmap loadBitmap(File f) throws IOException {
        Uri uri = Uri.fromFile(f);
        Bitmap bmp;
        if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.Source src = ImageDecoder.createSource(getContentResolver(), uri);
            return ImageDecoder.decodeBitmap(src);

        } else {
            return MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
        }

    }



    private Bitmap cropCenterSquare(Bitmap src){
        int w = src.getWidth(), h = src.getHeight();
        int size = Math.min(w, h);
        int x = (w - size) / 2;
        int y = (h - size) / 2;
        return Bitmap.createBitmap(src, x, y, size, size);
    }

    private Uri saveToPictures(Bitmap bmp, String subDir) throws IOException{
        String name = "SQ_" + System.currentTimeMillis() + ".jpg";
        ContentResolver cr = getContentResolver();
        ContentValues cv = new ContentValues();
        cv.put(MediaStore.Images.Media.DISPLAY_NAME, name);
        cv.put(MediaStore.Images.Media.MIME_TYPE,"image/jpeg");
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            cv.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/" + subDir + "/");

        }
        Uri uri= cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,cv);
        if (uri == null) throw new IOException("insert failed");
        try(FileOutputStream fos = (FileOutputStream) cr.openOutputStream(uri)){
            bmp.compress(Bitmap.CompressFormat.JPEG,95,fos);
        }
        return uri;
    }
}