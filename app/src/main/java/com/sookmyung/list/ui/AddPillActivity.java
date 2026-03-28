package com.sookmyung.list.ui;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.app.Dialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.sookmyung.list.ApiClient;
import com.sookmyung.list.ApiEnvelope;
import com.sookmyung.list.ApiService;
import com.sookmyung.list.Pill;
import com.sookmyung.list.PillSearchCache;
import com.sookmyung.list.PillStorage;
import com.sookmyung.medicell.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AddPillActivity extends AppCompatActivity {

    private static final String TAG = "PILL_SEQ";
    private static final String KEY =
            "05e7eb40989bb1a835e6fbcc11e6143335a7e69dcacb6929762947845547d798";

    private ApiService api;
    private SearchResultAdapter adapter;
    private ProgressBar progressBar;
    private EditText etQuery;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;
    private String lastQuery = "";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_add_pill);

        api = ApiClient.get();

        RecyclerView rv = findViewById(R.id.recycler);
        etQuery = findViewById(R.id.etQuery);
        ImageView iv = findViewById(R.id.ivSearch);
        progressBar = findViewById(R.id.progressBar);

        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SearchResultAdapter(this::handlePick);
        rv.setAdapter(adapter);

        iv.setOnClickListener(v -> {
            cancelPendingSearch();
            submitExactSearch(etQuery.getText().toString());
        });

        etQuery.setOnEditorActionListener((v, actionId, event) -> {
            boolean isSearchAction =
                    actionId == EditorInfo.IME_ACTION_SEARCH
                            || actionId == EditorInfo.IME_ACTION_DONE
                            || actionId == EditorInfo.IME_ACTION_GO
                            || actionId == EditorInfo.IME_ACTION_UNSPECIFIED
                            || actionId == EditorInfo.IME_NULL;

            boolean isEnterKey =
                    event != null
                            && event.getAction() == KeyEvent.ACTION_DOWN
                            && event.getKeyCode() == KeyEvent.KEYCODE_ENTER;

            if (isSearchAction || isEnterKey) {
                v.post(() -> submitExactSearch(v.getText().toString()));
                return true;
            }
            return false;
        });

        etQuery.setOnKeyListener((v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_ENTER
                    && event != null
                    && event.getAction() == KeyEvent.ACTION_DOWN) {
                v.post(() -> submitExactSearch(etQuery.getText().toString()));
                return true;
            }
            return false;
        });

        etQuery.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                cancelPendingSearch();

                String query = normalize(s.toString());
                if (query.isEmpty()) {
                    lastQuery = "";
                    adapter.submit(new ArrayList<>());
                    progressBar.setVisibility(View.GONE);
                    return;
                }

                pendingSearch = () -> fetch(query, false);
                handler.postDelayed(pendingSearch, 300);
            }

            @Override
            public void afterTextChanged(Editable s) { }
        });
    }

    private void submitExactSearch(String rawQuery) {
        cancelPendingSearch();

        String query = normalize(rawQuery);
        if (query.isEmpty()) {
            adapter.submit(new ArrayList<>());
            progressBar.setVisibility(View.GONE);
            return;
        }

        ApiEnvelope.Item currentExact = adapter.findExactMatch(query);
        if (currentExact != null) {
            handlePick(currentExact);
            return;
        }

        fetch(query, true);
    }

    private void cancelPendingSearch() {
        if (pendingSearch != null) {
            handler.removeCallbacks(pendingSearch);
            pendingSearch = null;
        }
    }

    private void handlePick(ApiEnvelope.Item item) {
        if (item == null) return;

        Log.d(TAG, item.itemName + " / " + item.itemSeq);

        if (isAlreadyAdded(item.itemSeq)) {
            Toast.makeText(this, "이미 추가된 알약입니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        showAddConfirmDialog(item);
    }

    private void fetch(String rawQuery, boolean openExactDialogIfMatched) {
        final String query = normalize(rawQuery);

        if (query.isEmpty()) {
            adapter.submit(new ArrayList<>());
            progressBar.setVisibility(View.GONE);
            return;
        }

        List<ApiEnvelope.Item> cached = PillSearchCache.get(this, query);
        if (cached != null) {
            progressBar.setVisibility(View.GONE);
            List<ApiEnvelope.Item> filtered = sortAndFilter(cached, query);
            afterSearch(query, filtered, openExactDialogIfMatched);
            lastQuery = query;
            return;
        }

        if (query.equals(lastQuery) && !openExactDialogIfMatched) {
            return;
        }
        lastQuery = query;

        progressBar.setVisibility(View.VISIBLE);

        api.searchPills(KEY, 1, 200, "json", query).enqueue(new Callback<ApiEnvelope>() {
            @Override
            public void onResponse(@NonNull Call<ApiEnvelope> call,
                                   @NonNull Response<ApiEnvelope> res) {
                progressBar.setVisibility(View.GONE);

                List<ApiEnvelope.Item> filtered = filterResponse(res, query);
                PillSearchCache.put(AddPillActivity.this, query, filtered);
                afterSearch(query, filtered, openExactDialogIfMatched);
            }

            @Override
            public void onFailure(@NonNull Call<ApiEnvelope> call, @NonNull Throwable t) {
                progressBar.setVisibility(View.GONE);
                adapter.submit(new ArrayList<>());
                Toast.makeText(AddPillActivity.this, "검색 실패: 네트워크를 확인해주세요.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private List<ApiEnvelope.Item> filterResponse(Response<ApiEnvelope> res, String query) {
        List<ApiEnvelope.Item> result = new ArrayList<>();

        if (res.isSuccessful()
                && res.body() != null
                && res.body().body != null
                && res.body().body.items != null) {

            for (ApiEnvelope.Item item : res.body().body.items) {
                if (item == null || item.itemName == null) {
                    continue;
                }

                String itemName = normalize(item.itemName);
                if (itemName.startsWith(query) || itemName.contains(query)) {
                    result.add(item);
                }
            }
        }

        return sortAndFilter(result, query);
    }

    private List<ApiEnvelope.Item> sortAndFilter(List<ApiEnvelope.Item> source, String query) {
        List<ApiEnvelope.Item> result = new ArrayList<>(source);

        Collections.sort(result, new Comparator<ApiEnvelope.Item>() {
            @Override
            public int compare(ApiEnvelope.Item a, ApiEnvelope.Item b) {
                String aName = normalize(a.itemName);
                String bName = normalize(b.itemName);

                int aScore = getMatchScore(aName, query);
                int bScore = getMatchScore(bName, query);

                if (aScore != bScore) {
                    return Integer.compare(aScore, bScore);
                }

                int aIndex = aName.indexOf(query);
                int bIndex = bName.indexOf(query);
                if (aIndex != bIndex) {
                    return Integer.compare(aIndex, bIndex);
                }

                int aGap = Math.abs(aName.length() - query.length());
                int bGap = Math.abs(bName.length() - query.length());
                if (aGap != bGap) {
                    return Integer.compare(aGap, bGap);
                }

                return aName.compareTo(bName);
            }
        });

        return result;
    }

    private int getMatchScore(String itemName, String query) {
        if (itemName.equals(query)) return 0;
        if (itemName.startsWith(query)) return 1;

        int index = itemName.indexOf(query);
        if (index >= 0) return 2 + index;

        return 100;
    }

    private void afterSearch(String query,
                             List<ApiEnvelope.Item> filtered,
                             boolean openExactDialogIfMatched) {
        adapter.submit(filtered);

        if (filtered.isEmpty()) {
            return;
        }

        ApiEnvelope.Item exact = findExactMatch(filtered, query);
        if (openExactDialogIfMatched && exact != null) {
            handlePick(exact);
        }
    }

    private ApiEnvelope.Item findExactMatch(List<ApiEnvelope.Item> items, String query) {
        if (items == null) return null;

        for (ApiEnvelope.Item item : items) {
            if (item != null && query.equals(normalize(item.itemName))) {
                return item;
            }
        }
        return null;
    }

    private void showAddConfirmDialog(ApiEnvelope.Item item) {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_add_pill_confirm);

        TextView tvMessage = dialog.findViewById(R.id.tvMessage);
        Button btnNo = dialog.findViewById(R.id.btnNo);
        Button btnYes = dialog.findViewById(R.id.btnYes);

        String pillName = item.itemName == null ? "" : item.itemName.trim();
        tvMessage.setText(pillName + "\n추가하시겠습니까?");

        btnNo.setText("아니오");
        btnYes.setText("예");

        btnNo.setOnClickListener(v -> dialog.dismiss());

        btnYes.setOnClickListener(v -> {
            PillStorage.add(
                    this,
                    new Pill(
                            item.itemSeq,
                            item.itemName,
                            item.entpName,
                            item.className,
                            item.drugShape,
                            item.color1,
                            item.itemImage
                    )
            );

            Toast.makeText(
                    this,
                    item.itemName + "이(가) 추가되었습니다.",
                    Toast.LENGTH_SHORT
            ).show();

            dialog.dismiss();
            finish();
        });

        dialog.show();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.90f);
            dialog.getWindow().setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setGravity(Gravity.CENTER);
        }
    }

    private String normalize(String text) {
        if (text == null) return "";
        return text.trim().replace(" ", "").replace("\n", "");
    }

    private boolean isAlreadyAdded(String itemSeq) {
        List<Pill> current = PillStorage.load(this);
        for (Pill pill : current) {
            if (pill != null
                    && pill.itemSeq != null
                    && pill.itemSeq.equals(itemSeq)) {
                return true;
            }
        }
        return false;
    }

    static class SearchResultAdapter extends RecyclerView.Adapter<SearchResultVH> {

        interface OnPick {
            void pick(ApiEnvelope.Item item);
        }

        private final List<ApiEnvelope.Item> data = new ArrayList<>();
        private final OnPick cb;

        SearchResultAdapter(OnPick cb) {
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

        ApiEnvelope.Item findExactMatch(String query) {
            if (query == null) return null;

            String normalizedQuery = query.trim().replace(" ", "").replace("\n", "");
            for (ApiEnvelope.Item item : data) {
                if (item != null && item.itemName != null) {
                    String itemName = item.itemName.trim().replace(" ", "").replace("\n", "");
                    if (normalizedQuery.equals(itemName)) {
                        return item;
                    }
                }
            }
            return null;
        }

        @NonNull
        @Override
        public SearchResultVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_pill_search, parent, false);
            return new SearchResultVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull SearchResultVH holder, int position) {
            ApiEnvelope.Item item = data.get(position);

            holder.tvName.setText(item.itemName);
            holder.tvSub.setText(buildSubText(item));

            holder.itemView.setOnClickListener(v -> {
                if (cb != null) {
                    cb.pick(item);
                }
            });
        }

        private String buildSubText(ApiEnvelope.Item item) {
            String entp = safe(item.entpName);
            String cls = safe(item.className);

            if (!entp.isEmpty() && !cls.isEmpty()) {
                return entp + " · " + cls;
            }
            if (!entp.isEmpty()) {
                return entp;
            }
            return cls;
        }

        private String safe(String text) {
            return text == null ? "" : text.trim();
        }

        @Override
        public int getItemCount() {
            return data.size();
        }
    }

    static class SearchResultVH extends RecyclerView.ViewHolder {
        TextView tvName;
        TextView tvSub;

        SearchResultVH(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvSub = itemView.findViewById(R.id.tvSub);
        }
    }
}