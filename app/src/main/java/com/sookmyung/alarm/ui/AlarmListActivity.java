package com.sookmyung.alarm.ui;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.widget.ImageButton;
import com.sookmyung.medicell.threeButton;
import android.widget.LinearLayout;
import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.sookmyung.alarm.Alarm;
import com.sookmyung.alarm.AlarmScheduler;
import com.sookmyung.alarm.AlarmStorage;
import com.sookmyung.medicell.R;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AlarmListActivity extends AppCompatActivity {

    private AlarmListAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alarm_list);

        LinearLayout btnTodayMedicine = findViewById(R.id.btnTodayMedicine);
        btnTodayMedicine.setOnClickListener(v ->
                startActivity(new Intent(this, TodayMedicineActivity.class)));

        RecyclerView recycler = findViewById(R.id.recycler);
        recycler.setLayoutManager(new LinearLayoutManager(this));

        adapter = new AlarmListAdapter(new AlarmListAdapter.Listener() {
            @Override
            public void onEdit(AlarmListAdapter.AlarmGroupItem item) {
                Intent intent = new Intent(AlarmListActivity.this, AddAlarmActivity.class);
                intent.putExtra("groupId", item.groupId);
                startActivity(intent);
            }

            @Override
            public void onDelete(AlarmListAdapter.AlarmGroupItem item) {
                showDeleteDialog(item);
            }
        });
        recycler.setAdapter(adapter);

        findViewById(R.id.btnBackCircle).setOnClickListener(v -> {
            Intent intent = new Intent(this, threeButton.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

        Button btnAdd = findViewById(R.id.btnAdd);
        btnAdd.setOnClickListener(v -> startActivity(new Intent(this, AddAlarmActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        List<AlarmListAdapter.AlarmGroupItem> grouped = groupAlarms(AlarmStorage.load(this));
        adapter.submit(grouped);
    }

    private List<AlarmListAdapter.AlarmGroupItem> groupAlarms(List<Alarm> alarms) {
        Map<String, AlarmListAdapter.AlarmGroupItem> map = new LinkedHashMap<>();
        for (Alarm alarm : alarms) {
            String key = alarm.groupId != null ? alarm.groupId : alarm.id;
            AlarmListAdapter.AlarmGroupItem item = map.get(key);
            if (item == null) {
                item = new AlarmListAdapter.AlarmGroupItem();
                item.groupId = key;
                item.pillName = alarm.pillName;
                item.startDateMillis = alarm.startDateMillis;
                item.endDateMillis = alarm.endDateMillis;
                item.everyDay = alarm.everyDay;
                if (alarm.daysOfWeek != null) item.daysOfWeek.addAll(alarm.daysOfWeek);
                map.put(key, item);
            }
            item.alarms.add(alarm);
        }
        return new ArrayList<>(map.values());
    }

    private void showDeleteDialog(AlarmListAdapter.AlarmGroupItem item) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_add_pill_confirm);

        TextView tvMessage = dialog.findViewById(R.id.tvMessage);
        Button btnNo = dialog.findViewById(R.id.btnNo);
        Button btnYes = dialog.findViewById(R.id.btnYes);

        tvMessage.setText("알림을\n삭제하시겠습니까?");
        btnNo.setText("취소");
        btnYes.setText("삭제");

        btnNo.setOnClickListener(v -> dialog.dismiss());

        btnYes.setOnClickListener(v -> {
            AlarmScheduler.cancelGroup(this, item.groupId);
            AlarmStorage.removeByGroup(this, item.groupId);
            refresh();
            dialog.dismiss();
        });

        dialog.show();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.90f);
            dialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setGravity(Gravity.CENTER);
        }
    }
}