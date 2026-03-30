package com.sookmyung.list.ui;

import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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
    private View layoutRecommendSection;
    private final Map<String, SearchGuide> symptomGuideMap = new LinkedHashMap<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_add_pill);

        api = ApiClient.get();

        RecyclerView rv = findViewById(R.id.recycler);
        etQuery = findViewById(R.id.etQuery);
        ImageView iv = findViewById(R.id.ivSearch);
        progressBar = findViewById(R.id.progressBar);
        layoutRecommendSection = findViewById(R.id.layoutRecommendSection);

        initSymptomGuide();

        TextView chipFlu = findViewById(R.id.chipFlu);
        TextView chipCold = findViewById(R.id.chipCold);
        TextView chipDigest = findViewById(R.id.chipDigest);
        TextView chipBodyache = findViewById(R.id.chipBodyache);
        TextView chipRhinitis = findViewById(R.id.chipRhinitis);
        TextView chipHeadache = findViewById(R.id.chipHeadache);

        bindRecommendChip(chipFlu, "독감");
        bindRecommendChip(chipCold, "감기");
        bindRecommendChip(chipDigest, "소화");
        bindRecommendChip(chipBodyache, "몸살");
        bindRecommendChip(chipRhinitis, "비염");
        bindRecommendChip(chipHeadache, "두통");

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
                updateRecommendVisibility(query);

                if (query.isEmpty()) {
                    lastQuery = "";
                    adapter.submit(new ArrayList<>());
                    progressBar.setVisibility(View.GONE);
                    return;
                }

                pendingSearch = () -> searchWithGuide(query, false);
                handler.postDelayed(pendingSearch, 300);
            }

            @Override
            public void afterTextChanged(Editable s) { }
        });
    }

    private void submitExactSearch(String rawQuery) {
        cancelPendingSearch();

        String query = normalize(rawQuery);
        updateRecommendVisibility(query);

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

        searchWithGuide(query, true);
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

    private void updateRecommendVisibility(String query) {
        if (layoutRecommendSection == null) return;

        if (normalize(query).isEmpty()) {
            layoutRecommendSection.setVisibility(View.VISIBLE);
        } else {
            layoutRecommendSection.setVisibility(View.GONE);
        }
    }

    private void bindRecommendChip(TextView chip, String keyword) {
        chip.setOnClickListener(v -> {
            etQuery.setText(keyword);
            etQuery.setSelection(keyword.length());
            cancelPendingSearch();
            updateRecommendVisibility(keyword);
            searchWithGuide(keyword, false);
        });
    }

    private void initSymptomGuide() {
        symptomGuideMap.clear();

        symptomGuideMap.put("두통", new SearchGuide(
                new String[]{"해열", "진통", "소염"},
                new String[]{"타이레놀", "이부프로펜", "게보린"},
                new String[]{"두통", "통증", "진통"}
        ));

        symptomGuideMap.put("몸살", new SearchGuide(
                new String[]{"해열", "진통", "소염"},
                new String[]{"타이레놀", "이부프로펜", "판피린"},
                new String[]{"몸살", "발열", "오한", "통증"}
        ));

        symptomGuideMap.put("소화", new SearchGuide(
                new String[]{"건위", "소화", "제산"},
                new String[]{"훼스탈", "베아제", "겔포스"},
                new String[]{"소화", "소화불량", "위부팽만", "속쓰림"}
        ));

        symptomGuideMap.put("비염", new SearchGuide(
                new String[]{"항히스타민", "비염"},
                new String[]{"지르텍", "클라리틴", "나잘스프레이"},
                new String[]{"비염", "콧물", "재채기", "코막힘"}
        ));

        symptomGuideMap.put("독감", new SearchGuide(
                new String[]{"해열", "진통"},
                new String[]{"타미플루", "타이레놀", "이부프로펜"},
                new String[]{"독감", "인플루엔자", "발열", "오한"}
        ));

        symptomGuideMap.put("감기", new SearchGuide(
                new String[]{"해열", "진통", "소염", "항히스타민", "진해거담", "감기"},
                new String[]{"종합감기약", "판콜", "콜대원", "타이레놀"},
                new String[]{"감기", "기침", "콧물", "인후통", "코막힘"}
        ));

        symptomGuideMap.put("기침", new SearchGuide(
                new String[]{"진해거담", "기침", "호흡기"},
                new String[]{"기침", "진해거담", "콜대원", "판콜"},
                new String[]{"기침", "가래", "인후통"}
        ));

        symptomGuideMap.put("콧물", new SearchGuide(
                new String[]{"항히스타민", "비염"},
                new String[]{"콧물", "지르텍", "클라리틴", "나잘스프레이"},
                new String[]{"콧물", "재채기", "비염", "코막힘"}
        ));

        symptomGuideMap.put("코막힘", new SearchGuide(
                new String[]{"항히스타민", "비염"},
                new String[]{"코막힘", "지르텍", "클라리틴", "나잘스프레이"},
                new String[]{"코막힘", "콧물", "재채기", "비염"}
        ));

        symptomGuideMap.put("인후통", new SearchGuide(
                new String[]{"진통", "소염", "감기"},
                new String[]{"인후통", "종합감기약", "판콜", "타이레놀"},
                new String[]{"인후통", "목아픔", "기침", "감기"}
        ));

        symptomGuideMap.put("열", new SearchGuide(
                new String[]{"해열", "진통"},
                new String[]{"타이레놀", "이부프로펜", "해열"},
                new String[]{"발열", "열", "오한"}
        ));

        symptomGuideMap.put("생리통", new SearchGuide(
                new String[]{"진통", "소염"},
                new String[]{"이부프로펜", "게보린", "타이레놀"},
                new String[]{"생리통", "월경통", "통증"}
        ));

        symptomGuideMap.put("근육통", new SearchGuide(
                new String[]{"진통", "소염"},
                new String[]{"이부프로펜", "타이레놀", "게보린"},
                new String[]{"근육통", "통증", "염좌통"}
        ));

        symptomGuideMap.put("치통", new SearchGuide(
                new String[]{"진통", "소염"},
                new String[]{"타이레놀", "이부프로펜", "게보린"},
                new String[]{"치통", "통증", "진통"}
        ));

        symptomGuideMap.put("소화불량", new SearchGuide(
                new String[]{"건위", "소화", "제산"},
                new String[]{"훼스탈", "베아제", "겔포스"},
                new String[]{"소화불량", "위부팽만", "속쓰림", "소화"}
        ));

        symptomGuideMap.put("설사", new SearchGuide(
                new String[]{"정장", "지사"},
                new String[]{"설사", "정장", "지사"},
                new String[]{"설사", "묽은변", "복통"}
        ));

        symptomGuideMap.put("변비", new SearchGuide(
                new String[]{"하제", "변비"},
                new String[]{"마그밀", "둘코락스", "듀파락"},
                new String[]{"변비", "배변", "장운동"}
        ));

        symptomGuideMap.put("재채기", new SearchGuide(
                new String[]{"항히스타민", "비염"},
                new String[]{"지르텍", "클라리틴", "나잘스프레이"},
                new String[]{"재채기", "콧물", "비염", "코막힘"}
        ));

        symptomGuideMap.put("당뇨", new SearchGuide(
                new String[]{"당뇨", "혈당강하"},
                new String[]{"메트포르민", "글리메피리드", "당뇨"},
                new String[]{"당뇨", "혈당", "제2형당뇨병"}
        ));

        symptomGuideMap.put("당뇨병", new SearchGuide(
                new String[]{"당뇨", "혈당강하"},
                new String[]{"메트포르민", "글리메피리드", "당뇨"},
                new String[]{"당뇨병", "당뇨", "혈당", "제2형당뇨병"}
        ));

        symptomGuideMap.put("혈압", new SearchGuide(
                new String[]{"혈압강하"},
                new String[]{"암로디핀", "로사르탄", "혈압"},
                new String[]{"고혈압", "혈압", "본태성고혈압"}
        ));

        symptomGuideMap.put("고혈압", new SearchGuide(
                new String[]{"혈압강하"},
                new String[]{"암로디핀", "로사르탄", "고혈압"},
                new String[]{"고혈압", "혈압", "본태성고혈압"}
        ));
    }

    private void searchWithGuide(String rawQuery, boolean openExactDialogIfMatched) {
        String query = normalize(rawQuery);

        if (query.isEmpty()) {
            adapter.submit(new ArrayList<>());
            progressBar.setVisibility(View.GONE);
            updateRecommendVisibility(query);
            return;
        }

        SearchGuide guide = symptomGuideMap.get(query);
        if (guide == null) {
            fetch(query, openExactDialogIfMatched);
            return;
        }

        fetchByEfficacyFirst(query, guide, openExactDialogIfMatched);
    }

    private void fetchByEfficacyFirst(String symptom,
                                      SearchGuide guide,
                                      boolean openExactDialogIfMatched) {
        progressBar.setVisibility(View.VISIBLE);

        List<ApiEnvelope.Item> cached = PillSearchCache.get(this, symptom);
        if (cached != null) {
            List<ApiEnvelope.Item> efficacyMatched = filterByEfficacyClassOrName(cached, guide, symptom);

            if (!efficacyMatched.isEmpty()) {
                progressBar.setVisibility(View.GONE);
                afterSearch(symptom, efficacyMatched, openExactDialogIfMatched);
                return;
            }

            fetchByAssistKeywords(symptom, guide, openExactDialogIfMatched);
            return;
        }

        api.searchPills(KEY, 1, 200, "json", symptom).enqueue(new Callback<ApiEnvelope>() {
            @Override
            public void onResponse(@NonNull Call<ApiEnvelope> call,
                                   @NonNull Response<ApiEnvelope> res) {

                List<ApiEnvelope.Item> fetched = filterResponseWithoutQueryMatch(res);
                PillSearchCache.put(AddPillActivity.this, symptom, fetched);

                List<ApiEnvelope.Item> efficacyMatched = filterByEfficacyClassOrName(fetched, guide, symptom);

                if (!efficacyMatched.isEmpty()) {
                    progressBar.setVisibility(View.GONE);
                    afterSearch(symptom, efficacyMatched, openExactDialogIfMatched);
                    return;
                }

                fetchByAssistKeywords(symptom, guide, openExactDialogIfMatched);
            }

            @Override
            public void onFailure(@NonNull Call<ApiEnvelope> call, @NonNull Throwable t) {
                fetchByAssistKeywords(symptom, guide, openExactDialogIfMatched);
            }
        });
    }

    private void fetchByAssistKeywords(String symptom,
                                       SearchGuide guide,
                                       boolean openExactDialogIfMatched) {

        progressBar.setVisibility(View.VISIBLE);

        List<ApiEnvelope.Item> merged = new ArrayList<>();
        Set<String> addedSeq = new HashSet<>();

        fetchAssistKeywordRecursive(symptom, guide, 0, merged, addedSeq, openExactDialogIfMatched);
    }

    private void fetchAssistKeywordRecursive(String symptom,
                                             SearchGuide guide,
                                             int index,
                                             List<ApiEnvelope.Item> merged,
                                             Set<String> addedSeq,
                                             boolean openExactDialogIfMatched) {

        if (index >= guide.assistKeywords.length) {
            progressBar.setVisibility(View.GONE);
            List<ApiEnvelope.Item> filtered = filterByClassOrName(merged, guide, symptom);
            afterSearch(symptom, filtered, openExactDialogIfMatched);
            return;
        }

        String assistKeyword = normalize(guide.assistKeywords[index]);

        List<ApiEnvelope.Item> cached = PillSearchCache.get(this, assistKeyword);
        if (cached != null) {
            mergeUniqueItems(merged, addedSeq, cached);
            fetchAssistKeywordRecursive(symptom, guide, index + 1, merged, addedSeq, openExactDialogIfMatched);
            return;
        }

        api.searchPills(KEY, 1, 200, "json", assistKeyword).enqueue(new Callback<ApiEnvelope>() {
            @Override
            public void onResponse(@NonNull Call<ApiEnvelope> call,
                                   @NonNull Response<ApiEnvelope> res) {

                List<ApiEnvelope.Item> fetched = filterResponseWithoutQueryMatch(res);
                PillSearchCache.put(AddPillActivity.this, assistKeyword, fetched);
                mergeUniqueItems(merged, addedSeq, fetched);

                fetchAssistKeywordRecursive(symptom, guide, index + 1, merged, addedSeq, openExactDialogIfMatched);
            }

            @Override
            public void onFailure(@NonNull Call<ApiEnvelope> call, @NonNull Throwable t) {
                fetchAssistKeywordRecursive(symptom, guide, index + 1, merged, addedSeq, openExactDialogIfMatched);
            }
        });
    }

    private List<ApiEnvelope.Item> filterResponseWithoutQueryMatch(Response<ApiEnvelope> res) {
        List<ApiEnvelope.Item> result = new ArrayList<>();

        if (res.isSuccessful()
                && res.body() != null
                && res.body().body != null
                && res.body().body.items != null) {

            for (ApiEnvelope.Item item : res.body().body.items) {
                if (item != null && item.itemName != null) {
                    result.add(item);
                }
            }
        }

        return result;
    }

    private void mergeUniqueItems(List<ApiEnvelope.Item> target,
                                  Set<String> addedSeq,
                                  List<ApiEnvelope.Item> source) {
        if (source == null) return;

        for (ApiEnvelope.Item item : source) {
            if (item == null || item.itemSeq == null) continue;

            if (!addedSeq.contains(item.itemSeq)) {
                addedSeq.add(item.itemSeq);
                target.add(item);
            }
        }
    }

    private List<ApiEnvelope.Item> filterByEfficacyClassOrName(List<ApiEnvelope.Item> source,
                                                               SearchGuide guide,
                                                               String symptom) {
        List<ApiEnvelope.Item> result = new ArrayList<>();

        for (ApiEnvelope.Item item : source) {
            if (item == null) continue;

            String itemName = normalize(item.itemName);
            String className = normalize(item.className);
            String efficacy = normalize(item.efcyQesitm);

            boolean efficacyMatched =
                    efficacy.contains(normalize(symptom))
                            || containsAny(efficacy, guide.efficacyKeywords);

            boolean classMatched = false;
            for (String classKeyword : guide.classKeywords) {
                if (className.contains(normalize(classKeyword))) {
                    classMatched = true;
                    break;
                }
            }

            boolean nameMatched = false;
            for (String assistKeyword : guide.assistKeywords) {
                if (itemName.contains(normalize(assistKeyword))) {
                    nameMatched = true;
                    break;
                }
            }

            if (efficacyMatched || classMatched || nameMatched) {
                result.add(item);
            }
        }

        return sortSymptomResult(result, guide, symptom);
    }

    private boolean containsAny(String target, String[] keywords) {
        if (target == null || keywords == null) return false;

        for (String keyword : keywords) {
            if (target.contains(normalize(keyword))) {
                return true;
            }
        }
        return false;
    }

    private List<ApiEnvelope.Item> filterByClassOrName(List<ApiEnvelope.Item> source,
                                                       SearchGuide guide,
                                                       String symptom) {
        List<ApiEnvelope.Item> result = new ArrayList<>();

        for (ApiEnvelope.Item item : source) {
            if (item == null) continue;

            String className = normalize(item.className);
            String itemName = normalize(item.itemName);

            boolean classMatched = false;
            for (String classKeyword : guide.classKeywords) {
                if (className.contains(normalize(classKeyword))) {
                    classMatched = true;
                    break;
                }
            }

            boolean nameMatched = false;
            for (String assistKeyword : guide.assistKeywords) {
                if (itemName.contains(normalize(assistKeyword))) {
                    nameMatched = true;
                    break;
                }
            }

            if (classMatched || nameMatched) {
                result.add(item);
            }
        }

        return sortSymptomResult(result, guide, symptom);
    }

    private List<ApiEnvelope.Item> sortSymptomResult(List<ApiEnvelope.Item> source,
                                                     SearchGuide guide,
                                                     String symptom) {
        List<ApiEnvelope.Item> result = new ArrayList<>(source);

        Collections.sort(result, (a, b) -> {
            int aScore = getSymptomScore(a, guide, symptom);
            int bScore = getSymptomScore(b, guide, symptom);

            if (aScore != bScore) {
                return Integer.compare(aScore, bScore);
            }

            String aName = normalize(a.itemName);
            String bName = normalize(b.itemName);
            return aName.compareTo(bName);
        });

        return result;
    }

    private int getSymptomScore(ApiEnvelope.Item item, SearchGuide guide, String symptom) {
        String className = normalize(item.className);
        String itemName = normalize(item.itemName);
        String efficacy = normalize(item.efcyQesitm);

        if (itemName.contains(normalize(symptom))) return 0;
        if (efficacy.contains(normalize(symptom))) return 1;

        for (String keyword : guide.efficacyKeywords) {
            if (efficacy.contains(normalize(keyword))) {
                return 2;
            }
        }

        for (String assistKeyword : guide.assistKeywords) {
            if (itemName.contains(normalize(assistKeyword))) {
                return 3;
            }
        }

        for (String classKeyword : guide.classKeywords) {
            if (className.contains(normalize(classKeyword))) {
                return 4;
            }
        }

        return 10;
    }

    static class SearchGuide {
        final String[] classKeywords;
        final String[] assistKeywords;
        final String[] efficacyKeywords;

        SearchGuide(String[] classKeywords, String[] assistKeywords, String[] efficacyKeywords) {
            this.classKeywords = classKeywords;
            this.assistKeywords = assistKeywords;
            this.efficacyKeywords = efficacyKeywords;
        }
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