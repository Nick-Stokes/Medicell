package com.sookmyung.alarm.ui;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TimePicker;
import androidx.appcompat.app.AppCompatActivity;
import com.sookmyung.alarm.Alarm;
import com.sookmyung.alarm.AlarmScheduler;
import com.sookmyung.alarm.AlarmStorage;
import com.sookmyung.list.Pill;
import com.sookmyung.list.PillStorage;
import com.sookmyung.medicell.R;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AddAlarmActivity extends AppCompatActivity {

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_add_alarm);

        // 약 이름 목록 준비 (사용자가 추가했던 약)
        List<Pill> pills = PillStorage.load(this);
        List<String> names = new ArrayList<>();
        for (Pill p : pills) names.add(p.itemName);

        Spinner sp = findViewById(R.id.spPill);
        sp.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names));

        TimePicker tp = findViewById(R.id.timePicker);
        tp.setIs24HourView(true);

        Button btnAdd = findViewById(R.id.btnAdd);
        btnAdd.setOnClickListener(v -> {
            if (names.isEmpty()) { finish(); return; }
            String pillName = (String) sp.getSelectedItem();
            int hour = tp.getCurrentHour();
            int minute = tp.getCurrentMinute();

            Alarm a = new Alarm(UUID.randomUUID().toString(), pillName, hour, minute);
            AlarmStorage.add(this, a);
            AlarmScheduler.scheduleDaily(this, a);
            finish();
        });
    }
}
