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

/** 내 약 리스트 화면: 조회/삭제/추가 진입 */
public class PillListActivity extends AppCompatActivity {
    private PillAdapter adapter;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_pill_list);

        RecyclerView rv = findViewById(R.id.recycler);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PillAdapter(p -> {});
        rv.setAdapter(adapter);

        Button btnAdd = findViewById(R.id.btnAdd);
        Button btnDelete = findViewById(R.id.btnDelete);

        btnAdd.setOnClickListener(v ->
                startActivity(new Intent(this, AddPillActivity.class)));

        btnDelete.setOnClickListener(v -> {
            Pill sel = adapter.getSelected();
            if (sel == null) return;
            new AlertDialog.Builder(this)
                    .setMessage(sel.itemName + "을(를) 삭제하시겠습니까?")
                    .setPositiveButton("예", (DialogInterface d, int w) -> {
                        PillStorage.remove(this, sel);
                        refresh();
                    })
                    .setNegativeButton("아니오", null)
                    .show();
        });

        refresh();
    }

    @Override protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        List<Pill> list = PillStorage.load(this);
        adapter.submit(list);
    }
}
