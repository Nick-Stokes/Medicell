package com.sookmyung.medicell;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.io.IOException;
import java.util.List;

public class PillResult extends AppCompatActivity {

    private ImageView pillPhotoView;
    private TextView pillLabelView;
    private TextView pillContentView;
    private TextView pillTop5View;
    private Button btnVoicePill;
    private Button btnNextList;

    private Uri photoUri;
    private Bitmap originalBitmap;
    private PillClassifier classifier;

    @SuppressLint("WrongThread")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_pill_result);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        pillPhotoView = findViewById(R.id.pillphoto);
        pillLabelView = findViewById(R.id.pilllabel);
        pillContentView = findViewById(R.id.pill_content);
        pillTop5View = findViewById(R.id.pill_top5);
        btnVoicePill = findViewById(R.id.voice_pill);
        btnNextList = findViewById(R.id.next_list);

        String uriStr = getIntent().getStringExtra("photo_uri");
        if (uriStr == null || uriStr.isEmpty()){
            Toast.makeText(this, "사진 정보가 없습니다.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        photoUri = Uri.parse(uriStr);

        try {
            if (Build.VERSION.SDK_INT >= 28){
                ImageDecoder.Source src = ImageDecoder.createSource(getContentResolver(), photoUri);
                originalBitmap = ImageDecoder.decodeBitmap(src);
            }else {
                originalBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), photoUri);
            }
        } catch (IOException e){
            e.printStackTrace();
            Toast.makeText(this, "이미지 로드 실패", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (originalBitmap != null){
            Bitmap displayBitmap = Bitmap.createScaledBitmap(originalBitmap, 100,100, true);
            pillPhotoView.setImageBitmap(displayBitmap);
        }

        try {
            classifier = new PillClassifier(this);

        }catch (IOException e){
            e.printStackTrace();
            Toast.makeText(this, "모델 로드 실패", Toast.LENGTH_SHORT).show();
            return;
        }

        runClassification();

    }

    private void runClassification(){
        if (classifier == null || originalBitmap == null){
            Toast.makeText(this, "분류기를 사용할 준비가 안 됐어요.", Toast.LENGTH_SHORT).show();
            return;
        }

        List<PillClassifier.Prediction> top5 = classifier.clssify(originalBitmap);
        if (top5 == null || top5.isEmpty()){
            Toast.makeText(this, "예측 실패", Toast.LENGTH_SHORT).show();
            return;
        }

        PillClassifier.Prediction best = top5.get(0);
        pillLabelView.setText(best.label);

        StringBuilder sb = new StringBuilder();
        sb.append("2위~5위 예측\n");

        for (int i = 1; i < top5.size() && i <5 ; i++){
            PillClassifier.Prediction p = top5.get(i);
            int rank = i + 1;
            sb.append(rank).append("위: ").append(p.label).append("\n");

        }
        pillTop5View.setText(sb.toString());
    }

    @Override
    protected  void onDestroy(){
        super.onDestroy();
        if (classifier != null){
            classifier.close();
            classifier = null;
        }
    }
}