package com.sookmyung.alarm.ui;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.sookmyung.alarm.Alarm;
import com.sookmyung.alarm.AlarmStorage;
import com.sookmyung.medicell.R;

import java.util.List;

public class AlarmListActivity extends AppCompatActivity {

    private AlarmListAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alarm_list);

        RecyclerView rv = findViewById(R.id.recycler);
        rv.setLayoutManager(new LinearLayoutManager(this));

        adapter = new AlarmListAdapter(this::showDeleteDialog);
        rv.setAdapter(adapter);

        Button btnAdd = findViewById(R.id.btnAdd);
        btnAdd.setOnClickListener(v ->
                startActivity(new Intent(this, AddAlarmActivity.class)));

        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        List<Alarm> list = AlarmStorage.load(this);
        adapter.submit(list);
    }

    private void showDeleteDialog(Alarm alarm) {

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_add_pill_confirm);

        TextView tvMessage = dialog.findViewById(R.id.tvMessage);
        TextView btnNo = dialog.findViewById(R.id.btnNo);
        TextView btnYes = dialog.findViewById(R.id.btnYes);

        tvMessage.setText("이 알람을 삭제하시겠습니까?");
        btnNo.setText("취소");
        btnYes.setText("삭제");

        btnNo.setOnClickListener(v -> dialog.dismiss());

        btnYes.setOnClickListener(v -> {
            AlarmStorage.remove(this, alarm);
            refresh();
            dialog.dismiss();
        });

        dialog.show();

        if (dialog.getWindow() != null) {
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.92f);
            dialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }
}