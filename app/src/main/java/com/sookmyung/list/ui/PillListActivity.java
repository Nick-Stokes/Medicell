package com.sookmyung.list.ui;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.sookmyung.list.Pill;
import com.sookmyung.list.PillStorage;
import com.sookmyung.medicell.R;

import java.util.List;

/** 내 약 리스트 화면: 조회 / 상세정보 / 삭제 / 추가 */
public class PillListActivity extends AppCompatActivity {
    private PillAdapter adapter;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_pill_list);

        RecyclerView rv = findViewById(R.id.recycler);
        rv.setLayoutManager(new LinearLayoutManager(this));

        adapter = new PillAdapter(
                this::showPillDetailDialog,
                this::showDeleteDialog
        );

        rv.setAdapter(adapter);

        Button btnAdd = findViewById(R.id.btnAdd);
        btnAdd.setOnClickListener(v ->
                startActivity(new Intent(this, AddPillActivity.class)));

        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        List<Pill> list = PillStorage.load(this);
        adapter.submit(list);
    }

    private void showDeleteDialog(Pill pill) {
        new AlertDialog.Builder(this)
                .setMessage(pill.itemName + "을(를) 삭제하시겠습니까?")
                .setPositiveButton("예", (DialogInterface d, int w) -> {
                    PillStorage.remove(this, pill);
                    refresh();
                })
                .setNegativeButton("아니오", null)
                .show();
    }

    private void showPillDetailDialog(Pill pill) {
        String company = safeText(pill.entpName);
        String efficacy = safeText(pill.className);
        String shape = safeText(pill.drugShape);
        String color = safeText(pill.color1);

        String message =
                "제약회사: " + company + "\n\n" +
                "효능/분류: " + efficacy + "\n\n" +
                "모양: " + shape + "\n\n" +
                "색상: " + color;

        new AlertDialog.Builder(this)
                .setTitle(pill.itemName)
                .setMessage(message)
                .setPositiveButton("확인", null)
                .show();
    }

    private String safeText(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "정보 없음";
        }
        return value;
    }
}