package com.sookmyung.list.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
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

/** 약 검색 / 자동완성 / 추가 확인 */
public class AddPillActivity extends AppCompatActivity {

    private SimpleItemAdapter adapter;
    private ApiService api;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;

    private static final String KEY =
            "05e7eb40989bb1a835e6fbcc11e6143335a7e69dcacb6929762947845547d798";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_add_pill);

        api = ApiClient.get();

        RecyclerView rv = findViewById(R.id.recycler);
        rv.setLayoutManager(new LinearLayoutManager(this));

        adapter = new SimpleItemAdapter(item -> {
            if (isAlreadyAdded(item.itemSeq)) {
                Toast.makeText(this, "이미 추가된 알약입니다.", Toast.LENGTH_SHORT).show();
                return;
            }

            new AlertDialog.Builder(this)
                    .setMessage(item.itemName + " 을(를) 추가하시겠습니까?")
                    .setPositiveButton("예", (d, w) -> {
                        PillStorage.add(
                                this,
                                new Pill(
                                        item.itemSeq,
                                        item.itemName,
                                        item.entpName,
                                        item.className,
                                        item.drugShape,
                                        item.color1
                                )
                        );
                        Toast.makeText(this, item.itemName + "이(가) 추가되었습니다.", Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .setNegativeButton("아니오", null)
                    .show();
        });

        rv.setAdapter(adapter);

        EditText et = findViewById(R.id.etQuery);
        ImageView iv = findViewById(R.id.ivSearch);

        iv.setOnClickListener(v -> {
            if (pendingSearch != null) {
                handler.removeCallbacks(pendingSearch);
            }
            fetch(et.getText().toString().trim());
        });

        et.setOnEditorActionListener((TextView v, int actionId, KeyEvent event) -> {
            boolean isSearchAction = actionId == EditorInfo.IME_ACTION_SEARCH;
            boolean isEnterKey = event != null
                    && event.getAction() == KeyEvent.ACTION_DOWN
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER;

            if (isSearchAction || isEnterKey) {
                if (pendingSearch != null) {
                    handler.removeCallbacks(pendingSearch);
                }
                fetch(et.getText().toString().trim());
                return true;
            }
            return false;
        });

        et.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (pendingSearch != null) {
                    handler.removeCallbacks(pendingSearch);
                }

                String query = normalize(s.toString());

                if (query.isEmpty()) {
                    adapter.submit(new ArrayList<>());
                    return;
                }

                pendingSearch = () -> fetch(query);
                handler.postDelayed(pendingSearch, 350);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void fetch(String q) {
        final String query = normalize(q);

        if (query.isEmpty()) {
            adapter.submit(new ArrayList<>());
            return;
        }

        final String apiQuery = query.substring(0, 1);

        api.searchPills(KEY, 1, 100, "json", apiQuery).enqueue(new Callback<ApiEnvelope>() {
            @Override
            public void onResponse(@NonNull Call<ApiEnvelope> call, @NonNull Response<ApiEnvelope> res) {
                List<ApiEnvelope.Item> filtered = new ArrayList<>();

                if (res.isSuccessful()
                        && res.body() != null
                        && res.body().body != null
                        && res.body().body.items != null) {

                    for (ApiEnvelope.Item item : res.body().body.items) {
                        if (item == null || item.itemName == null) {
                            continue;
                        }

                        String itemName = normalize(item.itemName);

                        if (itemName.startsWith(query)) {
                            filtered.add(item);
                        }
                    }
                }

                adapter.submit(filtered);

                if (filtered.isEmpty()) {
                    Toast.makeText(AddPillActivity.this, "검색 결과가 없습니다.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiEnvelope> call, @NonNull Throwable t) {
                adapter.submit(new ArrayList<>());
                Toast.makeText(AddPillActivity.this, "검색 실패: 네트워크를 확인해주세요.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.trim().replace(" ", "");
    }

    private boolean isAlreadyAdded(String itemSeq) {
        List<Pill> current = PillStorage.load(this);
        for (Pill pill : current) {
            if (pill != null && pill.itemSeq != null && pill.itemSeq.equals(itemSeq)) {
                return true;
            }
        }
        return false;
    }

    /** 검색 결과 표시용 어댑터 */
    static class SimpleItemAdapter extends RecyclerView.Adapter<SimpleItemVH> {

        interface OnPick {
            void pick(ApiEnvelope.Item item);
        }

        private final List<ApiEnvelope.Item> data = new ArrayList<>();
        private final OnPick cb;

        SimpleItemAdapter(OnPick cb) {
            this.cb = cb;
        }

        void submit(List<ApiEnvelope.Item> d) {
            int oldSize = data.size();
            if (oldSize > 0) {
                data.clear();
                notifyItemRangeRemoved(0, oldSize);
            } else {
                data.clear();
            }

            if (d != null && !d.isEmpty()) {
                data.addAll(d);
                notifyItemRangeInserted(0, data.size());
            }
        }

        @NonNull
        @Override
        public SimpleItemVH onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            android.view.View v = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(android.R.layout.simple_list_item_1, parent, false);
            return new SimpleItemVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull SimpleItemVH holder, int position) {
            ApiEnvelope.Item item = data.get(position);

            TextView tv = holder.itemView.findViewById(android.R.id.text1);
            tv.setText(item.itemName);
            tv.setTextSize(22f);
            tv.setPadding(20, 20, 20, 20);

            holder.itemView.setOnClickListener(v -> {
                if (cb != null) {
                    cb.pick(item);
                }
            });
        }

        @Override
        public int getItemCount() {
            return data.size();
        }
    }

    static class SimpleItemVH extends RecyclerView.ViewHolder {
        SimpleItemVH(@NonNull android.view.View v) {
            super(v);
        }
    }
}