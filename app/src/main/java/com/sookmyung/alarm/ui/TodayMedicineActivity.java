package com.sookmyung.alarm.ui;

import android.os.Handler;
import java.util.Calendar;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.content.Intent;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

import com.sookmyung.alarm.Alarm;
import com.sookmyung.alarm.AlarmStorage;
import com.sookmyung.medicell.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TodayMedicineActivity extends AppCompatActivity {

    private TextView tvTodayDate;
    private TextView tvEmptyTodayMedicine;
    private LinearLayout layoutTodayMedicineContainer;
    private Button btnAddAlarm;

    private final Handler handler = new Handler();

    private final Runnable midnightRunnable = new Runnable() {
        @Override
        public void run() {
            setTodayDate();
            renderTodayMedicines();
            scheduleNextMidnight();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_today_medicine);

        tvTodayDate = findViewById(R.id.tvTodayDate);
        tvEmptyTodayMedicine = findViewById(R.id.tvEmptyTodayMedicine);
        layoutTodayMedicineContainer = findViewById(R.id.layoutTodayMedicineContainer);
        btnAddAlarm = findViewById(R.id.btnAddAlarm);

        findViewById(R.id.btnBackCircle).setOnClickListener(v -> {
            Intent intent = new Intent(this, AlarmListActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        btnAddAlarm.setOnClickListener(v ->
                startActivity(new Intent(this, AddAlarmActivity.class)));

        setTodayDate();
        renderTodayMedicines();
        scheduleNextMidnight();
    }

    private void setTodayDate() {
        Calendar today = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy년 M월 d일 EEEE", Locale.KOREAN);
        tvTodayDate.setText(sdf.format(today.getTime()));
    }

    private void scheduleNextMidnight() {
        handler.removeCallbacks(midnightRunnable);

        Calendar now = Calendar.getInstance();
        Calendar nextMidnight = Calendar.getInstance();

        nextMidnight.add(Calendar.DAY_OF_YEAR, 1);
        nextMidnight.set(Calendar.HOUR_OF_DAY, 0);
        nextMidnight.set(Calendar.MINUTE, 0);
        nextMidnight.set(Calendar.SECOND, 0);
        nextMidnight.set(Calendar.MILLISECOND, 0);

        long delayMillis = nextMidnight.getTimeInMillis() - now.getTimeInMillis();
        handler.postDelayed(midnightRunnable, delayMillis);
    }

    private void renderTodayMedicines() {
        layoutTodayMedicineContainer.removeAllViews();

        List<Alarm> alarms = AlarmStorage.load(this);
        List<Alarm> todayAlarms = filterTodayAlarms(alarms);

        if (todayAlarms.isEmpty()) {
            layoutTodayMedicineContainer.setVisibility(View.GONE);
            tvEmptyTodayMedicine.setVisibility(View.VISIBLE);
            return;
        } else {
            layoutTodayMedicineContainer.setVisibility(View.VISIBLE);
            tvEmptyTodayMedicine.setVisibility(View.GONE);
        }

        Collections.sort(todayAlarms, Comparator
                .comparingInt((Alarm a) -> a.hour)
                .thenComparingInt(a -> a.minute)
                .thenComparing(a -> a.pillName));

        Map<String, List<Alarm>> groupedMap = new LinkedHashMap<>();
        for (Alarm alarm : todayAlarms) {
            String key = String.format(Locale.getDefault(), "%02d:%02d", alarm.hour, alarm.minute);
            if (!groupedMap.containsKey(key)) {
                groupedMap.put(key, new ArrayList<>());
            }
            groupedMap.get(key).add(alarm);
        }

        for (Map.Entry<String, List<Alarm>> entry : groupedMap.entrySet()) {
            List<Alarm> sameTimeAlarms = entry.getValue();
            if (sameTimeAlarms.isEmpty()) continue;

            Alarm firstAlarm = sameTimeAlarms.get(0);
            addTimeHeader(formatTime(firstAlarm.hour, firstAlarm.minute));

            for (Alarm alarm : sameTimeAlarms) {
                addPillCard(alarm.pillName);
            }
        }
    }

    private List<Alarm> filterTodayAlarms(List<Alarm> alarms) {
        List<Alarm> result = new ArrayList<>();
        Calendar today = Calendar.getInstance();
        int todayDayOfWeek = today.get(Calendar.DAY_OF_WEEK);

        for (Alarm alarm : alarms) {
            if (alarm.everyDay) {
                result.add(alarm);
            } else if (alarm.daysOfWeek != null && alarm.daysOfWeek.contains(todayDayOfWeek)) {
                result.add(alarm);
            }
        }
        return result;
    }

    private void addTimeHeader(String timeText) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        rowParams.setMargins(dp(20), dp(22), dp(20), dp(14));
        row.setLayoutParams(rowParams);

        TextView tvTime = new TextView(this);
        tvTime.setText(timeText);
        tvTime.setTextColor(Color.parseColor("#222222"));
        tvTime.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        tvTime.setTypeface(Typeface.DEFAULT);

        View line = new View(this);
        LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(
                0,
                dp(1),
                1f
        );
        lineParams.setMargins(dp(14), dp(4), 0, 0);
        line.setLayoutParams(lineParams);
        line.setBackgroundColor(Color.parseColor("#CFCFCF"));

        row.addView(tvTime);
        row.addView(line);

        layoutTodayMedicineContainer.addView(row);
    }

    private void addPillCard(String pillName) {
        TextView tvPill = new TextView(this);
        tvPill.setText(pillName);
        tvPill.setTextColor(Color.parseColor("#222222"));
        tvPill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        tvPill.setTypeface(Typeface.DEFAULT_BOLD);
        tvPill.setPadding(dp(20), dp(20), dp(20), dp(20));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), Color.parseColor("#DDD7CC"));
        tvPill.setBackground(bg);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(dp(20), 0, dp(20), dp(12));
        tvPill.setLayoutParams(params);

        layoutTodayMedicineContainer.addView(tvPill);
    }

    private String formatTime(int hour24, int minute) {
        String amPm = hour24 < 12 ? "오전" : "오후";
        int hour12 = hour24 % 12;
        if (hour12 == 0) hour12 = 12;
        return amPm + " " + hour12 + ":" + String.format(Locale.getDefault(), "%02d", minute);
    }

    private int dp(int value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                getResources().getDisplayMetrics()
        );
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(midnightRunnable);
    }
}