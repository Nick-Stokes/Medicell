package com.sookmyung.alarm.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AlertDialog;
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

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_alarm_list);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    10
            );
        }

        RecyclerView rv = findViewById(R.id.recycler);
        rv.setLayoutManager(new LinearLayoutManager(this));

        adapter = new AlarmListAdapter(this::showDeleteDialog);
        rv.setAdapter(adapter);

        Button btnAdd = findViewById(R.id.btnAdd);
        btnAdd.setOnClickListener(this::openAddAlarm);

        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void openAddAlarm(View view) {
        startActivity(new Intent(this, AddAlarmActivity.class));
    }

    private void refresh() {
        List<Alarm> list = AlarmStorage.load(this);
        adapter.submit(list);
    }

    private void showDeleteDialog(Alarm alarm) {
        new AlertDialog.Builder(this)
                .setTitle("알람 삭제")
                .setMessage("이 알람을 삭제하시겠습니까?")
                .setPositiveButton("삭제", (dialog, which) -> {
                    AlarmScheduler.cancel(this, alarm);
                    AlarmStorage.remove(this, alarm);
                    refresh();
                })
                .setNegativeButton("취소", null)
                .show();
    }
}