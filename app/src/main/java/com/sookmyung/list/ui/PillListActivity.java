package com.sookmyung.list.ui;

import android.widget.ProgressBar;
import com.sookmyung.list.detail.PermitInfoApiClient;
import com.sookmyung.list.detail.PermitInfoEnvelope;
import com.sookmyung.list.detail.PermitInfoService;
import com.sookmyung.medicell.threeButton;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.app.Dialog;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.sookmyung.list.Pill;
import com.sookmyung.list.PillStorage;
import com.sookmyung.list.detail.DrugDetailApiClient;
import com.sookmyung.list.detail.DrugDetailEnvelope;
import com.sookmyung.list.detail.DrugDetailService;
import com.sookmyung.medicell.R;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PillListActivity extends AppCompatActivity {

    private static final String KEY =
            "05e7eb40989bb1a835e6fbcc11e6143335a7e69dcacb6929762947845547d798";

    private static final int DETAIL_TAB_SELECTED_COLOR = Color.parseColor("#F28C28");
    private static final int DETAIL_TAB_DEFAULT_COLOR = Color.parseColor("#222222");

    private PillAdapter adapter;
    private DrugDetailService detailService;
    private PermitInfoService permitInfoService;
    private TextView tvEmpty;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_pill_list);

        detailService = DrugDetailApiClient.get();
        permitInfoService = PermitInfoApiClient.get();

        RecyclerView rv = findViewById(R.id.recycler);
        tvEmpty = findViewById(R.id.tvEmpty);

        rv.setLayoutManager(new LinearLayoutManager(this));

        adapter = new PillAdapter(
                this::showPillDetailDialog,
                this::showDeleteDialog
        );

        rv.setAdapter(adapter);

        findViewById(R.id.btnBackCircle).setOnClickListener(v -> {
            Intent intent = new Intent(this, threeButton.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        });

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

        if (list.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
        } else {
            tvEmpty.setVisibility(View.GONE);
        }
    }

    private void showDeleteDialog(Pill pill) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_add_pill_confirm);

        TextView tvMessage = dialog.findViewById(R.id.tvMessage);
        Button btnNo = dialog.findViewById(R.id.btnNo);
        Button btnYes = dialog.findViewById(R.id.btnYes);

        String pillName = pill.itemName == null ? "" : pill.itemName.trim();
        tvMessage.setText(pillName + "\n삭제하시겠습니까?");

        btnNo.setText("아니오");
        btnYes.setText("예");

        btnNo.setOnClickListener(v -> dialog.dismiss());

        btnYes.setOnClickListener(v -> {
            PillStorage.remove(this, pill);
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

    private void showPillDetailDialog(Pill pill) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_pill_detail);

        ImageView ivPill = dialog.findViewById(R.id.ivPill);
        TextView tvTitle = dialog.findViewById(R.id.tvPillTitle);

        LinearLayout layoutTabSection = dialog.findViewById(R.id.layoutTabSection);
        LinearLayout tabBasic = dialog.findViewById(R.id.tabBasic);
        LinearLayout tabCaution = dialog.findViewById(R.id.tabCaution);

        TextView tvTabBasic = dialog.findViewById(R.id.tvTabBasic);
        TextView tvTabCaution = dialog.findViewById(R.id.tvTabCaution);
        View viewTabBasicUnderline = dialog.findViewById(R.id.viewTabBasicUnderline);
        View viewTabCautionUnderline = dialog.findViewById(R.id.viewTabCautionUnderline);

        LinearLayout layoutBasicContent = dialog.findViewById(R.id.layoutBasicContent);
        LinearLayout layoutCautionContent = dialog.findViewById(R.id.layoutCautionContent);

        LinearLayout layoutEfficacy = dialog.findViewById(R.id.layoutEfficacy);
        LinearLayout layoutUsage = dialog.findViewById(R.id.layoutUsage);
        LinearLayout layoutCaution = dialog.findViewById(R.id.layoutCaution);

        TextView tvEfficacy = dialog.findViewById(R.id.tvEfficacy);
        TextView tvUsage = dialog.findViewById(R.id.tvUsage);
        TextView tvCaution = dialog.findViewById(R.id.tvCaution);
        View viewNoDetailDivider = dialog.findViewById(R.id.viewNoDetailDivider);
        LinearLayout layoutNoDetailState = dialog.findViewById(R.id.layoutNoDetailState);
        ProgressBar progressDetailLoading = dialog.findViewById(R.id.progressDetailLoading);
        TextView tvNoDetail = dialog.findViewById(R.id.tvNoDetail);
        TextView btnClose = dialog.findViewById(R.id.btnClose);

        tvTitle.setText(pill.itemName);
        loadPillImage(ivPill, pill);

        tabBasic.setOnClickListener(v -> switchDetailTab(
                true,
                layoutBasicContent,
                layoutCautionContent,
                tvTabBasic,
                tvTabCaution,
                viewTabBasicUnderline,
                viewTabCautionUnderline
        ));

        tabCaution.setOnClickListener(v -> switchDetailTab(
                false,
                layoutBasicContent,
                layoutCautionContent,
                tvTabBasic,
                tvTabCaution,
                viewTabBasicUnderline,
                viewTabCautionUnderline
        ));

        btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.92f);
            int height = (int) (getResources().getDisplayMetrics().heightPixels * 0.86f);
            dialog.getWindow().setLayout(width, height);
            dialog.getWindow().setGravity(Gravity.CENTER);
        }

        boolean hasCachedDetail =
                !isEmpty(pill.efficacy) ||
                        !isEmpty(pill.usage) ||
                        !isEmpty(pill.caution);

        if (hasCachedDetail) {
            applyDetailToViews(
                    pill.efficacy,
                    pill.usage,
                    pill.caution,
                    layoutTabSection,
                    layoutBasicContent,
                    layoutCautionContent,
                    layoutEfficacy,
                    layoutUsage,
                    layoutCaution,
                    tvEfficacy,
                    tvUsage,
                    tvCaution,
                    viewNoDetailDivider,
                    layoutNoDetailState,
                    progressDetailLoading,
                    tvNoDetail,
                    tvTabBasic,
                    tvTabCaution,
                    viewTabBasicUnderline,
                    viewTabCautionUnderline
            );
        } else {
            showDetailLoadingState(
                    layoutTabSection,
                    layoutBasicContent,
                    layoutCautionContent,
                    layoutEfficacy,
                    layoutUsage,
                    layoutCaution,
                    viewNoDetailDivider,
                    layoutNoDetailState,
                    progressDetailLoading,
                    tvNoDetail
            );
        }

        loadDrugDetail(
                pill,
                layoutTabSection,
                layoutBasicContent,
                layoutCautionContent,
                layoutEfficacy,
                layoutUsage,
                layoutCaution,
                tvEfficacy,
                tvUsage,
                tvCaution,
                viewNoDetailDivider,
                layoutNoDetailState,
                progressDetailLoading,
                tvNoDetail,
                tvTabBasic,
                tvTabCaution,
                viewTabBasicUnderline,
                viewTabCautionUnderline
        );
    }

    private void loadDrugDetail(
            Pill pill,
            LinearLayout layoutTabSection,
            LinearLayout layoutBasicContent,
            LinearLayout layoutCautionContent,
            LinearLayout layoutEfficacy,
            LinearLayout layoutUsage,
            LinearLayout layoutCaution,
            TextView tvEfficacy,
            TextView tvUsage,
            TextView tvCaution,
            View viewNoDetailDivider,
            LinearLayout layoutNoDetailState,
            ProgressBar progressDetailLoading,
            TextView tvNoDetail,
            TextView tvTabBasic,
            TextView tvTabCaution,
            View viewTabBasicUnderline,
            View viewTabCautionUnderline
    ) {
        detailService.getDetail(KEY, 1, 10, "json", pill.itemSeq)
                .enqueue(new Callback<DrugDetailEnvelope>() {

                    @Override
                    public void onResponse(
                            @NonNull Call<DrugDetailEnvelope> call,
                            @NonNull Response<DrugDetailEnvelope> response) {

                        Log.d("API_TEST", "응답 성공 여부: " + response.isSuccessful());
                        Log.d("API_TEST", "HTTP 코드: " + response.code());

                        if (response.body() == null
                                || response.body().body == null
                                || response.body().body.items == null
                                || response.body().body.items.isEmpty()) {

                            loadPermitDetailFallback(
                                    pill,
                                    layoutTabSection,
                                    layoutBasicContent,
                                    layoutCautionContent,
                                    layoutEfficacy,
                                    layoutUsage,
                                    layoutCaution,
                                    tvEfficacy,
                                    tvUsage,
                                    tvCaution,
                                    viewNoDetailDivider,
                                    layoutNoDetailState,
                                    progressDetailLoading,
                                    tvNoDetail,
                                    tvTabBasic,
                                    tvTabCaution,
                                    viewTabBasicUnderline,
                                    viewTabCautionUnderline
                            );
                            return;
                        }

                        DrugDetailEnvelope.Item item = response.body().body.items.get(0);

                        String efficacy = cleanHtml(item.efcyQesitm);
                        String usage = cleanHtml(item.useMethodQesitm);
                        String caution = mergeNonEmpty(
                                cleanHtml(item.atpnWarnQesitm),
                                cleanHtml(item.atpnQesitm),
                                cleanHtml(item.intrcQesitm),
                                cleanHtml(item.seQesitm),
                                cleanHtml(item.depositMethodQesitm)
                        );

                        if (isEmpty(efficacy) && isEmpty(usage) && isEmpty(caution)) {
                            loadPermitDetailFallback(
                                    pill,
                                    layoutTabSection,
                                    layoutBasicContent,
                                    layoutCautionContent,
                                    layoutEfficacy,
                                    layoutUsage,
                                    layoutCaution,
                                    tvEfficacy,
                                    tvUsage,
                                    tvCaution,
                                    viewNoDetailDivider,
                                    layoutNoDetailState,
                                    progressDetailLoading,
                                    tvNoDetail,
                                    tvTabBasic,
                                    tvTabCaution,
                                    viewTabBasicUnderline,
                                    viewTabCautionUnderline
                            );
                            return;
                        }

                        pill.efficacy = efficacy;
                        pill.usage = usage;
                        pill.caution = caution;

                        PillStorage.upsert(PillListActivity.this, pill);

                        applyDetailToViews(
                                pill.efficacy,
                                pill.usage,
                                pill.caution,
                                layoutTabSection,
                                layoutBasicContent,
                                layoutCautionContent,
                                layoutEfficacy,
                                layoutUsage,
                                layoutCaution,
                                tvEfficacy,
                                tvUsage,
                                tvCaution,
                                viewNoDetailDivider,
                                layoutNoDetailState,
                                progressDetailLoading,
                                tvNoDetail,
                                tvTabBasic,
                                tvTabCaution,
                                viewTabBasicUnderline,
                                viewTabCautionUnderline
                        );
                    }

                    @Override
                    public void onFailure(
                            @NonNull Call<DrugDetailEnvelope> call,
                            @NonNull Throwable t) {

                        Log.e("API_TEST", "API 실패: " + t.getMessage());

                        loadPermitDetailFallback(
                                pill,
                                layoutTabSection,
                                layoutBasicContent,
                                layoutCautionContent,
                                layoutEfficacy,
                                layoutUsage,
                                layoutCaution,
                                tvEfficacy,
                                tvUsage,
                                tvCaution,
                                viewNoDetailDivider,
                                layoutNoDetailState,
                                progressDetailLoading,
                                tvNoDetail,
                                tvTabBasic,
                                tvTabCaution,
                                viewTabBasicUnderline,
                                viewTabCautionUnderline
                        );
                    }
                });
    }

    private void loadPermitDetailFallback(
            Pill pill,
            LinearLayout layoutTabSection,
            LinearLayout layoutBasicContent,
            LinearLayout layoutCautionContent,
            LinearLayout layoutEfficacy,
            LinearLayout layoutUsage,
            LinearLayout layoutCaution,
            TextView tvEfficacy,
            TextView tvUsage,
            TextView tvCaution,
            View viewNoDetailDivider,
            LinearLayout layoutNoDetailState,
            ProgressBar progressDetailLoading,
            TextView tvNoDetail,
            TextView tvTabBasic,
            TextView tvTabCaution,
            View viewTabBasicUnderline,
            View viewTabCautionUnderline
    ) {
        permitInfoService.getDetailByItemSeq(
                KEY, 1, 10, "json", pill.itemSeq, null
        ).enqueue(new Callback<PermitInfoEnvelope>() {
            @Override
            public void onResponse(
                    @NonNull Call<PermitInfoEnvelope> call,
                    @NonNull Response<PermitInfoEnvelope> response
            ) {
                PermitInfoEnvelope.Item permitItem =
                        response.body() == null ? null : response.body().getFirstItem();

                if (permitItem != null) {
                    applyPermitFallbackResult(
                            pill,
                            permitItem,
                            layoutTabSection,
                            layoutBasicContent,
                            layoutCautionContent,
                            layoutEfficacy,
                            layoutUsage,
                            layoutCaution,
                            tvEfficacy,
                            tvUsage,
                            tvCaution,
                            viewNoDetailDivider,
                            layoutNoDetailState,
                            progressDetailLoading,
                            tvNoDetail,
                            tvTabBasic,
                            tvTabCaution,
                            viewTabBasicUnderline,
                            viewTabCautionUnderline
                    );
                    return;
                }

                loadPermitDetailFallbackByName(
                        pill,
                        layoutTabSection,
                        layoutBasicContent,
                        layoutCautionContent,
                        layoutEfficacy,
                        layoutUsage,
                        layoutCaution,
                        tvEfficacy,
                        tvUsage,
                        tvCaution,
                        viewNoDetailDivider,
                        layoutNoDetailState,
                        progressDetailLoading,
                        tvNoDetail,
                        tvTabBasic,
                        tvTabCaution,
                        viewTabBasicUnderline,
                        viewTabCautionUnderline
                );
            }

            @Override
            public void onFailure(
                    @NonNull Call<PermitInfoEnvelope> call,
                    @NonNull Throwable t
            ) {
                loadPermitDetailFallbackByName(
                        pill,
                        layoutTabSection,
                        layoutBasicContent,
                        layoutCautionContent,
                        layoutEfficacy,
                        layoutUsage,
                        layoutCaution,
                        tvEfficacy,
                        tvUsage,
                        tvCaution,
                        viewNoDetailDivider,
                        layoutNoDetailState,
                        progressDetailLoading,
                        tvNoDetail,
                        tvTabBasic,
                        tvTabCaution,
                        viewTabBasicUnderline,
                        viewTabCautionUnderline
                );
            }
        });
    }

    private void loadPermitDetailFallbackByName(
            Pill pill,
            LinearLayout layoutTabSection,
            LinearLayout layoutBasicContent,
            LinearLayout layoutCautionContent,
            LinearLayout layoutEfficacy,
            LinearLayout layoutUsage,
            LinearLayout layoutCaution,
            TextView tvEfficacy,
            TextView tvUsage,
            TextView tvCaution,
            View viewNoDetailDivider,
            LinearLayout layoutNoDetailState,
            ProgressBar progressDetailLoading,
            TextView tvNoDetail,
            TextView tvTabBasic,
            TextView tvTabCaution,
            View viewTabBasicUnderline,
            View viewTabCautionUnderline
    ) {
        permitInfoService.findItemSeqByNameAndCompany(
                KEY,
                1,
                1,
                "json",
                pill.itemName,
                pill.entpName
        ).enqueue(new Callback<PermitInfoEnvelope>() {
            @Override
            public void onResponse(
                    @NonNull Call<PermitInfoEnvelope> call,
                    @NonNull Response<PermitInfoEnvelope> response
            ) {
                PermitInfoEnvelope.Item matchedItem =
                        response.body() == null ? null : response.body().getFirstItem();

                if (matchedItem == null) {
                    applyDetailToViews(
                            "",
                            "",
                            "",
                            layoutTabSection,
                            layoutBasicContent,
                            layoutCautionContent,
                            layoutEfficacy,
                            layoutUsage,
                            layoutCaution,
                            tvEfficacy,
                            tvUsage,
                            tvCaution,
                            viewNoDetailDivider,
                            layoutNoDetailState,
                            progressDetailLoading,
                            tvNoDetail,
                            tvTabBasic,
                            tvTabCaution,
                            viewTabBasicUnderline,
                            viewTabCautionUnderline
                    );
                    return;
                }

                String matchedItemSeq = matchedItem.itemSeq;

                if (isEmpty(matchedItemSeq)) {
                    applyDetailToViews(
                            "",
                            "",
                            "",
                            layoutTabSection,
                            layoutBasicContent,
                            layoutCautionContent,
                            layoutEfficacy,
                            layoutUsage,
                            layoutCaution,
                            tvEfficacy,
                            tvUsage,
                            tvCaution,
                            viewNoDetailDivider,
                            layoutNoDetailState,
                            progressDetailLoading,
                            tvNoDetail,
                            tvTabBasic,
                            tvTabCaution,
                            viewTabBasicUnderline,
                            viewTabCautionUnderline
                    );
                    return;
                }

                permitInfoService.getDetailByItemSeq(
                        KEY,
                        1,
                        10,
                        "json",
                        matchedItemSeq,
                        null
                ).enqueue(new Callback<PermitInfoEnvelope>() {
                    @Override
                    public void onResponse(
                            @NonNull Call<PermitInfoEnvelope> call,
                            @NonNull Response<PermitInfoEnvelope> detailResponse
                    ) {
                        PermitInfoEnvelope.Item detailItem =
                                detailResponse.body() == null ? null : detailResponse.body().getFirstItem();

                        if (detailItem != null) {
                            applyPermitFallbackResult(
                                    pill,
                                    detailItem,
                                    layoutTabSection,
                                    layoutBasicContent,
                                    layoutCautionContent,
                                    layoutEfficacy,
                                    layoutUsage,
                                    layoutCaution,
                                    tvEfficacy,
                                    tvUsage,
                                    tvCaution,
                                    viewNoDetailDivider,
                                    layoutNoDetailState,
                                    progressDetailLoading,
                                    tvNoDetail,
                                    tvTabBasic,
                                    tvTabCaution,
                                    viewTabBasicUnderline,
                                    viewTabCautionUnderline
                            );
                            return;
                        }

                        applyDetailToViews(
                                "",
                                "",
                                "",
                                layoutTabSection,
                                layoutBasicContent,
                                layoutCautionContent,
                                layoutEfficacy,
                                layoutUsage,
                                layoutCaution,
                                tvEfficacy,
                                tvUsage,
                                tvCaution,
                                viewNoDetailDivider,
                                layoutNoDetailState,
                                progressDetailLoading,
                                tvNoDetail,
                                tvTabBasic,
                                tvTabCaution,
                                viewTabBasicUnderline,
                                viewTabCautionUnderline
                        );
                    }

                    @Override
                    public void onFailure(
                            @NonNull Call<PermitInfoEnvelope> call,
                            @NonNull Throwable t
                    ) {
                        applyDetailToViews(
                                "",
                                "",
                                "",
                                layoutTabSection,
                                layoutBasicContent,
                                layoutCautionContent,
                                layoutEfficacy,
                                layoutUsage,
                                layoutCaution,
                                tvEfficacy,
                                tvUsage,
                                tvCaution,
                                viewNoDetailDivider,
                                layoutNoDetailState,
                                progressDetailLoading,
                                tvNoDetail,
                                tvTabBasic,
                                tvTabCaution,
                                viewTabBasicUnderline,
                                viewTabCautionUnderline
                        );
                    }
                });
            }

            @Override
            public void onFailure(
                    @NonNull Call<PermitInfoEnvelope> call,
                    @NonNull Throwable t
            ) {
                applyDetailToViews(
                        "",
                        "",
                        "",
                        layoutTabSection,
                        layoutBasicContent,
                        layoutCautionContent,
                        layoutEfficacy,
                        layoutUsage,
                        layoutCaution,
                        tvEfficacy,
                        tvUsage,
                        tvCaution,
                        viewNoDetailDivider,
                        layoutNoDetailState,
                        progressDetailLoading,
                        tvNoDetail,
                        tvTabBasic,
                        tvTabCaution,
                        viewTabBasicUnderline,
                        viewTabCautionUnderline
                );
            }
        });
    }

    private boolean hasPermitItem(Response<PermitInfoEnvelope> response) {
        return response.body() != null
                && response.body().getFirstItem() != null;
    }

    private void applyPermitFallbackResult(
            Pill pill,
            PermitInfoEnvelope.Item item,
            LinearLayout layoutTabSection,
            LinearLayout layoutBasicContent,
            LinearLayout layoutCautionContent,
            LinearLayout layoutEfficacy,
            LinearLayout layoutUsage,
            LinearLayout layoutCaution,
            TextView tvEfficacy,
            TextView tvUsage,
            TextView tvCaution,
            View viewNoDetailDivider,
            LinearLayout layoutNoDetailState,
            ProgressBar progressDetailLoading,
            TextView tvNoDetail,
            TextView tvTabBasic,
            TextView tvTabCaution,
            View viewTabBasicUnderline,
            View viewTabCautionUnderline
    ) {
        pill.efficacy = mergeNonEmpty(
                cleanHtml(item.eeDocData),
                cleanHtml(item.mainItemIngr)
        );

        pill.usage = mergeNonEmpty(
                cleanHtml(item.udDocData),
                cleanHtml(item.chart),
                cleanHtml(item.storageMethod)
        );

        pill.caution = mergeNonEmpty(
                cleanHtml(item.pnDocData),
                cleanHtml(item.nbDocData)
        );

        PillStorage.upsert(PillListActivity.this, pill);

        applyDetailToViews(
                pill.efficacy,
                pill.usage,
                pill.caution,
                layoutTabSection,
                layoutBasicContent,
                layoutCautionContent,
                layoutEfficacy,
                layoutUsage,
                layoutCaution,
                tvEfficacy,
                tvUsage,
                tvCaution,
                viewNoDetailDivider,
                layoutNoDetailState,
                progressDetailLoading,
                tvNoDetail,
                tvTabBasic,
                tvTabCaution,
                viewTabBasicUnderline,
                viewTabCautionUnderline
        );
    }

    private void showDetailLoadingState(
            LinearLayout layoutTabSection,
            LinearLayout layoutBasicContent,
            LinearLayout layoutCautionContent,
            LinearLayout layoutEfficacy,
            LinearLayout layoutUsage,
            LinearLayout layoutCaution,
            View viewNoDetailDivider,
            LinearLayout layoutNoDetailState,
            ProgressBar progressDetailLoading,
            TextView tvNoDetail
    ) {
        viewNoDetailDivider.setVisibility(View.VISIBLE);
        layoutNoDetailState.setVisibility(View.VISIBLE);
        progressDetailLoading.setVisibility(View.VISIBLE);
        tvNoDetail.setVisibility(View.VISIBLE);
        tvNoDetail.setText(getString(R.string.loading_detail_message));

        layoutTabSection.setVisibility(View.GONE);
        layoutBasicContent.setVisibility(View.GONE);
        layoutCautionContent.setVisibility(View.GONE);

        layoutEfficacy.setVisibility(View.GONE);
        layoutUsage.setVisibility(View.GONE);
        layoutCaution.setVisibility(View.GONE);
    }

    private void applyDetailToViews(
            String efficacy,
            String usage,
            String caution,
            LinearLayout layoutTabSection,
            LinearLayout layoutBasicContent,
            LinearLayout layoutCautionContent,
            LinearLayout layoutEfficacy,
            LinearLayout layoutUsage,
            LinearLayout layoutCaution,
            TextView tvEfficacy,
            TextView tvUsage,
            TextView tvCaution,
            View viewNoDetailDivider,
            LinearLayout layoutNoDetailState,
            ProgressBar progressDetailLoading,
            TextView tvNoDetail,
            TextView tvTabBasic,
            TextView tvTabCaution,
            View viewTabBasicUnderline,
            View viewTabCautionUnderline
    ) {
        boolean noEfficacy = isEmpty(efficacy);
        boolean noUsage = isEmpty(usage);
        boolean noCaution = isEmpty(caution);

        boolean allEmpty = noEfficacy && noUsage && noCaution;

        progressDetailLoading.setVisibility(View.GONE);
        layoutNoDetailState.setVisibility(View.GONE);

        if (allEmpty) {
            viewNoDetailDivider.setVisibility(View.VISIBLE);
            layoutNoDetailState.setVisibility(View.VISIBLE);
            tvNoDetail.setVisibility(View.VISIBLE);
            tvNoDetail.setText(getString(R.string.no_detail_message));

            layoutTabSection.setVisibility(View.GONE);
            layoutBasicContent.setVisibility(View.GONE);
            layoutCautionContent.setVisibility(View.GONE);

            layoutEfficacy.setVisibility(View.GONE);
            layoutUsage.setVisibility(View.GONE);
            layoutCaution.setVisibility(View.GONE);
            return;
        }

        viewNoDetailDivider.setVisibility(View.GONE);
        layoutNoDetailState.setVisibility(View.GONE);
        tvNoDetail.setVisibility(View.GONE);

        layoutTabSection.setVisibility(View.VISIBLE);
        layoutBasicContent.setVisibility(View.VISIBLE);
        layoutCautionContent.setVisibility(View.GONE);

        layoutEfficacy.setVisibility(View.VISIBLE);
        layoutUsage.setVisibility(View.VISIBLE);
        layoutCaution.setVisibility(View.VISIBLE);

        tvEfficacy.setText(noEfficacy ? getString(R.string.info_not_found) : efficacy);
        tvUsage.setText(noUsage ? getString(R.string.info_not_found) : usage);
        tvCaution.setText(noCaution ? getString(R.string.info_not_found) : caution);

        switchDetailTab(
                true,
                layoutBasicContent,
                layoutCautionContent,
                tvTabBasic,
                tvTabCaution,
                viewTabBasicUnderline,
                viewTabCautionUnderline
        );
    }

    private void switchDetailTab(
            boolean showBasic,
            LinearLayout layoutBasicContent,
            LinearLayout layoutCautionContent,
            TextView tvTabBasic,
            TextView tvTabCaution,
            View viewTabBasicUnderline,
            View viewTabCautionUnderline
    ) {
        layoutBasicContent.setVisibility(showBasic ? View.VISIBLE : View.GONE);
        layoutCautionContent.setVisibility(showBasic ? View.GONE : View.VISIBLE);

        tvTabBasic.setTextColor(showBasic ? DETAIL_TAB_SELECTED_COLOR : DETAIL_TAB_DEFAULT_COLOR);
        tvTabCaution.setTextColor(showBasic ? DETAIL_TAB_DEFAULT_COLOR : DETAIL_TAB_SELECTED_COLOR);

        viewTabBasicUnderline.setVisibility(showBasic ? View.VISIBLE : View.INVISIBLE);
        viewTabCautionUnderline.setVisibility(showBasic ? View.INVISIBLE : View.VISIBLE);
    }

    private void loadPillImage(ImageView imageView, Pill pill) {
        if (pill.itemImage != null && !pill.itemImage.trim().isEmpty()) {
            Glide.with(this)
                    .load(pill.itemImage)
                    .placeholder(R.drawable.ic_pill_placeholder)
                    .error(R.drawable.ic_pill_placeholder)
                    .into(imageView);
        } else {
            imageView.setImageResource(R.drawable.ic_pill_placeholder);
        }
    }

    private boolean isEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String mergeNonEmpty(String... values) {
        StringBuilder sb = new StringBuilder();

        for (String value : values) {
            if (!isEmpty(value)) {
                if (sb.length() > 0) {
                    sb.append("\n\n");
                }
                sb.append(value.trim());
            }
        }

        return sb.toString();
    }

    private String cleanHtml(String value) {
        if (value == null) return "";

        String text;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            text = Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY).toString();
        } else {
            text = Html.fromHtml(value).toString();
        }

        text = text.replace("•", "\n• ");
        text = text.replace("○", "\n○ ");
        text = text.replace("※", "\n※ ");

        return text.trim();
    }
}