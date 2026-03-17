package com.sookmyung.alarm.ui;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListPopupWindow;
import android.widget.NumberPicker;
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
import com.sookmyung.list.ui.PillListActivity;
import com.sookmyung.medicell.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class AddAlarmActivity extends AppCompatActivity {

    private static final int REQ_POST_NOTIFICATIONS = 1001;

    private LinearLayout selectPillBox;
    private TextView tvSelectedPill;
    private NumberPicker npAmPm;
    private NumberPicker npHour;
    private NumberPicker npMinute;

    private final List<String> pillNames = new ArrayList<>();
    private String selectedPillName = null;
    private ListPopupWindow pillPopupWindow;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_add_alarm);

        selectPillBox = findViewById(R.id.selectPillBox);
        tvSelectedPill = findViewById(R.id.tvSelectedPill);
        npAmPm = findViewById(R.id.npAmPm);
        npHour = findViewById(R.id.npHour);
        npMinute = findViewById(R.id.npMinute);

        Button btnAdd = findViewById(R.id.btnAdd);

        requestNotificationPermissionIfNeeded();
        loadPillNames();

        setupAmPmPicker();
        setupHourPicker();
        setupMinutePicker();

        stylePickerInput(npAmPm);
        stylePickerInput(npHour);
        stylePickerInput(npMinute);

        if (pillNames.isEmpty()) {
            showNoPillDialog();
        } else {
            selectedPillName = pillNames.get(0);
            tvSelectedPill.setText(selectedPillName);
        }

        selectPillBox.setOnClickListener(v -> {
            if (pillNames.isEmpty()) {
                showNoPillDialog();
                return;
            }
            showPillDropdown();
        });

        btnAdd.setOnClickListener(v -> {
            if (pillNames.isEmpty()) {
                showNoPillDialog();
                return;
            }

            if (!checkAndRequestAlarmPermissions()) {
                return;
            }

            if (selectedPillName == null || selectedPillName.trim().isEmpty()) {
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

            Alarm a = new Alarm(UUID.randomUUID().toString(), selectedPillName, hour24, minute);
            AlarmStorage.add(this, a);
            AlarmScheduler.scheduleDaily(this, a);

            Toast.makeText(this, "알람이 추가되었습니다.", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private void loadPillNames() {
        pillNames.clear();

        List<Pill> pills = PillStorage.load(this);
        for (Pill p : pills) {
            if (p != null && p.itemName != null && !p.itemName.trim().isEmpty()) {
                pillNames.add(p.itemName);
            }
        }
    }

    private void showPillDropdown() {
        if (pillPopupWindow != null && pillPopupWindow.isShowing()) {
            pillPopupWindow.dismiss();
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this,
                android.R.layout.simple_list_item_1,
                pillNames
        ) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);

                if (view instanceof TextView) {
                    TextView tv = (TextView) view;
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
                    tv.setTypeface(Typeface.DEFAULT_BOLD);
                    tv.setTextColor(Color.parseColor("#222222"));
                    tv.setPadding(24, 24, 24, 24);
                    tv.setMaxLines(2);
                }

                return view;
            }
        };

        pillPopupWindow = new ListPopupWindow(this);
        pillPopupWindow.setAnchorView(selectPillBox);
        pillPopupWindow.setAdapter(adapter);
        pillPopupWindow.setModal(true);
        pillPopupWindow.setBackgroundDrawable(new ColorDrawable(Color.WHITE));
        pillPopupWindow.setHorizontalOffset(0);
        pillPopupWindow.setVerticalOffset(8);
        pillPopupWindow.setWidth(selectPillBox.getWidth());
        pillPopupWindow.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);

        pillPopupWindow.setOnItemClickListener((parent, view, position, id) -> {
            selectedPillName = pillNames.get(position);
            tvSelectedPill.setText(selectedPillName);
            pillPopupWindow.dismiss();
        });

        selectPillBox.post(() -> {
            pillPopupWindow.setWidth(selectPillBox.getWidth());
            pillPopupWindow.show();
        });
    }

    private void showNoPillDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_add_pill_confirm);
        dialog.setCancelable(false);

        TextView tvMessage = dialog.findViewById(R.id.tvMessage);
        Button btnNo = dialog.findViewById(R.id.btnNo);
        Button btnYes = dialog.findViewById(R.id.btnYes);
        LinearLayout buttonContainer = dialog.findViewById(R.id.buttonContainer);

        tvMessage.setText("복용 알약 리스트에\n먼저 약을 추가해주세요.");
        btnNo.setVisibility(View.GONE);
        btnYes.setText("확인");
        buttonContainer.setGravity(Gravity.CENTER);

        btnYes.setOnClickListener(v -> {
            dialog.dismiss();
            startActivity(new Intent(this, PillListActivity.class));
            finish();
        });

        dialog.show();

        if (dialog.getWindow() != null) {
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.92f);
            dialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
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

            if (am != null && !am.canScheduleExactAlarms()) {
                Toast.makeText(this, "정확한 알람 권한을 허용해주세요.", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                startActivity(intent);
                return false;
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