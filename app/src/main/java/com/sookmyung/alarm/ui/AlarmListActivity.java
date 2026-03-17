package com.sookmyung.alarm.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.sookmyung.alarm.Alarm;
import com.sookmyung.alarm.AlarmScheduler;
import com.sookmyung.alarm.AlarmStorage;
import com.sookmyung.medicell.R;
import java.util.List;

public class AlarmListActivity extends AppCompatActivity {
    private AlarmListAdapter adapter;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_alarm_list);

        // Android 13+ 알림권한
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
        }

        RecyclerView rv = findViewById(R.id.recycler);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AlarmListAdapter(a -> {
            // 항목 삭제
            AlarmScheduler.cancel(this, a);
            AlarmStorage.remove(this, a);
            refresh();
        });
        rv.setAdapter(adapter);

        Button btnAdd = findViewById(R.id.btnAdd);
        btnAdd.setOnClickListener(v ->
            startActivity(new Intent(this, AddAlarmActivity.class)));

        refresh();
    }

    @Override protected void onResume() { super.onResume(); refresh(); }

    private void refresh() {
        List<Alarm> list = AlarmStorage.load(this);
        adapter.submit(list);
    }
}
