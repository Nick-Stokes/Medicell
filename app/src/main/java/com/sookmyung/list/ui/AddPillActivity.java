package com.sookmyung.list.ui;

import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.ImageView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.sookmyung.list.ApiClient;
import com.sookmyung.list.ApiEnvelope;
import com.sookmyung.list.ApiService;
import com.sookmyung.list.Pill;
import com.sookmyung.list.PillStorage;
import com.sookmyung.medicell.R;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** 약 검색/자동완성 → 추가 확인 */
public class AddPillActivity extends AppCompatActivity {

    private RecyclerView rv;
    private SimpleItemAdapter adapter;
    private ApiService api;
    private final Handler handler = new Handler();
    private Runnable pending;
    private static final String KEY =
            "4bb3d4b518ab34f31028273c6d60817e75a88b80bd05abf218bd21e700e0fbf6";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_add_pill);

        api = ApiClient.get();

        rv = findViewById(R.id.recycler);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SimpleItemAdapter(item -> new AlertDialog.Builder(this)
                .setMessage(item.itemName + "을(를) 추가하시겠습니까?")
                .setPositiveButton("예", (d, w) -> {
                    PillStorage.add(this,
                            new Pill(item.itemSeq, item.itemName, item.entpName,
                                     item.className, item.drugShape, item.color1));
                    finish();
                })
                .setNegativeButton("아니오", null)
                .show());
        rv.setAdapter(adapter);

        EditText et = findViewById(R.id.etQuery);
        ImageView iv = findViewById(R.id.ivSearch);

        iv.setOnClickListener(v -> fetch(et.getText().toString().trim()));

        // 디바운스 400ms 자동 검색
        et.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int bfr, int cnt) {
                if (pending != null) handler.removeCallbacks(pending);
                pending = () -> fetch(s.toString().trim());
                handler.postDelayed(pending, 400);
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void fetch(String q) {
        if (q.isEmpty()) { adapter.submit(new ArrayList<>()); return; }
        api.searchPills(KEY, 1, 10, "json", q).enqueue(new Callback<ApiEnvelope>() {
            @Override public void onResponse(Call<ApiEnvelope> call, Response<ApiEnvelope> res) {
                List<ApiEnvelope.Item> items = new ArrayList<>();
                if (res.isSuccessful() && res.body()!=null &&
                    res.body().body!=null && res.body().body.items!=null) {
                    items = res.body().body.items;
                }
                adapter.submit(items);
            }
            @Override public void onFailure(Call<ApiEnvelope> call, Throwable t) {
                adapter.submit(new ArrayList<>());
            }
        });
    }

    /** 검색 결과 간단 표시용 어댑터 */
    static class SimpleItemAdapter extends RecyclerView.Adapter<SimpleItemVH> {
        interface OnPick { void pick(ApiEnvelope.Item item); }
        private final List<ApiEnvelope.Item> data = new ArrayList<>();
        private final OnPick cb;
        SimpleItemAdapter(OnPick cb){ this.cb = cb; }
        void submit(List<ApiEnvelope.Item> d){ data.clear(); if(d!=null) data.addAll(d); notifyDataSetChanged(); }
        @Override public SimpleItemVH onCreateViewHolder(android.view.ViewGroup p, int vt){
            android.view.View v = android.view.LayoutInflater.from(p.getContext())
                    .inflate(android.R.layout.simple_list_item_1, p, false);
            return new SimpleItemVH(v);
        }
        @Override public void onBindViewHolder(SimpleItemVH h, int pos){
            ApiEnvelope.Item i = data.get(pos);
            ((android.widget.TextView)h.itemView.findViewById(android.R.id.text1)).setText(i.itemName);
            h.itemView.setOnClickListener(v -> { if (cb!=null) cb.pick(i); });
        }
        @Override public int getItemCount(){ return data.size(); }
    }
    static class SimpleItemVH extends RecyclerView.ViewHolder { SimpleItemVH(android.view.View v){ super(v); } }
}
