package com.sookmyung.medicell;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.compose.ActivityResultLauncherHolder;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import androidx.core.app.ActivityOptionsCompat;

// 추가한 부분
import com.sookmyung.list.ui.PillListActivity;

public class threeButton extends AppCompatActivity {

    private static final int REQ_PERM = 2001;
    private static final int REQ_CAMERA = 1001;
    private static final String KEY_PENDING_URI = "KEY_PENDING_URI";

    private Uri pendingPhotoUri = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_three_button);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        Button BtnList = findViewById(R.id.btn_list);
        Button BtnCamera = findViewById(R.id.btn_search);
        Button BtnAlarm = findViewById(R.id.btn_alarm);

        //추가한 부분
        BtnList.setOnClickListener(v -> {
            Intent intent = new Intent(this, PillListActivity.class);
            startActivity(intent);
        });
        
        BtnCamera.setOnClickListener(v ->{
            Intent i = new Intent(threeButton.this, SquareCamera.class);
            startActivity(i);
        });

    }
    
}

