package com.sookmyung.alarm.ui;

import android.Manifest;
import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.NumberPicker;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.sookmyung.alarm.Alarm;
import com.sookmyung.alarm.AlarmScheduler;
import com.sookmyung.alarm.AlarmStorage;
import com.sookmyung.list.Pill;
import com.sookmyung.list.PillStorage;
import com.sookmyung.medicell.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class AddAlarmActivity extends AppCompatActivity {

    private static final String TAG = "MEDICELL_ALARM";
    private static final int REQ_POST_NOTIFICATIONS = 1001;

    private Spinner sp;
    private NumberPicker npAmPm;
    private NumberPicker npHour;
    private NumberPicker npMinute;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_add_alarm);

        sp = findViewById(R.id.spPill);
        npAmPm = findViewById(R.id.npAmPm);
        npHour = findViewById(R.id.npHour);
        npMinute = findViewById(R.id.npMinute);

        Button btnAdd = findViewById(R.id.btnAdd);

        requestNotificationPermissionIfNeeded();

        List<Pill> pills = PillStorage.load(this);
        List<String> names = new ArrayList<>();

        for (Pill p : pills) {
            if (p != null && p.itemName != null && !p.itemName.trim().isEmpty()) {
                names.add(p.itemName);
            }
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                names
        ) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                setSpinnerTextStyle(view);
                return view;
            }

            @NonNull
            @Override
            public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                setSpinnerTextStyle(view);
                return view;
            }

            private void setSpinnerTextStyle(View view) {
                if (view instanceof TextView) {
                    TextView tv = (TextView) view;
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
                    tv.setTextColor(Color.BLACK);
                    tv.setTypeface(Typeface.DEFAULT_BOLD);
                    tv.setPadding(20, 20, 20, 20);
                }
            }
        };

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(adapter);

        if (names.isEmpty()) {
            Toast.makeText(this, "복용 알약 리스트에 먼저 약을 추가해주세요.", Toast.LENGTH_SHORT).show();
        }

        setupAmPmPicker();
        setupHourPicker();
        setupMinutePicker();

        stylePickerInput(npAmPm);
        stylePickerInput(npHour);
        stylePickerInput(npMinute);

        btnAdd.setOnClickListener(v -> {
            if (names.isEmpty()) {
                Toast.makeText(this, "복용 알약 리스트에 등록된 약이 없습니다.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!checkAndRequestAlarmPermissions()) {
                return;
            }

            String pillName = (String) sp.getSelectedItem();
            if (pillName == null || pillName.trim().isEmpty()) {
                Toast.makeText(this, "약을 선택해주세요.", Toast.LENGTH_SHORT).show();
                return;
            }

            int amPm = npAmPm.getValue();
            int hour12 = npHour.getValue();
            int minute = npMinute.getValue();

            int hour24;
            if (amPm == 0) {
                hour24 = (hour12 == 12) ? 0 : hour12;
            } else {
                hour24 = (hour12 == 12) ? 12 : hour12 + 12;
            }

            Log.d(TAG, "사용자 입력 시간 / amPm=" + amPm
                    + " / hour12=" + hour12
                    + " / minute=" + minute
                    + " / hour24=" + hour24);

            Alarm a = new Alarm(UUID.randomUUID().toString(), pillName, hour24, minute);
            AlarmStorage.add(this, a);
            AlarmScheduler.scheduleDaily(this, a);

            Toast.makeText(this, "알람이 추가되었습니다.", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private boolean checkAndRequestAlarmPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            boolean notificationGranted =
                    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                            == PackageManager.PERMISSION_GRANTED;

            if (!notificationGranted) {
                Toast.makeText(this, "알림 권한을 먼저 허용해주세요.", Toast.LENGTH_SHORT).show();
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQ_POST_NOTIFICATIONS
                );
                return false;
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

            if (am != null) {
                boolean canExact = am.canScheduleExactAlarms();
                Log.d(TAG, "canScheduleExactAlarms=" + canExact);

                if (!canExact) {
                    Toast.makeText(this, "정확한 알람 권한을 허용해주세요.", Toast.LENGTH_LONG).show();
                    Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                    startActivity(intent);
                    return false;
                }
            }
        }

        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            Toast.makeText(this, "앱 알림이 꺼져 있습니다. 설정에서 켜주세요.", Toast.LENGTH_LONG).show();
        }

        return true;
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQ_POST_NOTIFICATIONS
                );
            }
        }
    }

    private void setupAmPmPicker() {
        npAmPm.setMinValue(0);
        npAmPm.setMaxValue(1);
        npAmPm.setDisplayedValues(new String[]{"오전", "오후"});
        npAmPm.setWrapSelectorWheel(false);
        npAmPm.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        npAmPm.setValue(0);

        npAmPm.setOnValueChangedListener((picker, oldVal, newVal) ->
                picker.post(() -> stylePickerInput(picker)));
    }

    private void setupHourPicker() {
        npHour.setMinValue(1);
        npHour.setMaxValue(12);
        npHour.setWrapSelectorWheel(true);
        npHour.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        npHour.setValue(8);

        npHour.setOnValueChangedListener((picker, oldVal, newVal) ->
                picker.post(() -> stylePickerInput(picker)));
    }

    private void setupMinutePicker() {
        npMinute.setMinValue(0);
        npMinute.setMaxValue(59);
        npMinute.setFormatter(value -> String.format(Locale.getDefault(), "%02d", value));
        npMinute.setWrapSelectorWheel(true);
        npMinute.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        npMinute.setValue(0);

        npMinute.setOnValueChangedListener((picker, oldVal, newVal) ->
                picker.post(() -> stylePickerInput(picker)));
    }

    private void stylePickerInput(NumberPicker picker) {
        int childCount = picker.getChildCount();

        for (int i = 0; i < childCount; i++) {
            View child = picker.getChildAt(i);

            if (child instanceof EditText) {
                EditText editText = (EditText) child;
                editText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
                editText.setTextColor(Color.BLACK);
                editText.setTypeface(Typeface.DEFAULT_BOLD);
                editText.setGravity(Gravity.CENTER);
                editText.setFocusable(false);
                editText.setClickable(false);
                editText.setLongClickable(false);
                editText.setCursorVisible(false);
            }
        }

        picker.invalidate();
    }
}