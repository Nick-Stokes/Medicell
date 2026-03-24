package com.sookmyung.list.ui;

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

    private PillAdapter adapter;
    private DrugDetailService detailService;
    private TextView tvEmpty;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_pill_list);

        detailService = DrugDetailApiClient.get();

        RecyclerView rv = findViewById(R.id.recycler);
        tvEmpty = findViewById(R.id.tvEmpty);

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

        LinearLayout layoutEfficacy = dialog.findViewById(R.id.layoutEfficacy);
        LinearLayout layoutUsage = dialog.findViewById(R.id.layoutUsage);
        LinearLayout layoutCaution = dialog.findViewById(R.id.layoutCaution);

        TextView tvEfficacy = dialog.findViewById(R.id.tvEfficacy);
        TextView tvUsage = dialog.findViewById(R.id.tvUsage);
        TextView tvCaution = dialog.findViewById(R.id.tvCaution);
        TextView tvNoDetail = dialog.findViewById(R.id.tvNoDetail);
        TextView btnClose = dialog.findViewById(R.id.btnClose);

        tvTitle.setText(pill.itemName);
        loadPillImage(ivPill, pill);

        btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();

        if (dialog.getWindow() != null) {
            int width = (int) (getResources().getDisplayMetrics().widthPixels * 0.95f);
            int height = (int) (getResources().getDisplayMetrics().heightPixels * 0.88f);
            dialog.getWindow().setLayout(width, height);
        }

        applyDetailToViews(
                pill.efficacy,
                pill.usage,
                pill.caution,
                layoutEfficacy,
                layoutUsage,
                layoutCaution,
                tvEfficacy,
                tvUsage,
                tvCaution,
                tvNoDetail
        );

        loadDrugDetail(
                pill,
                layoutEfficacy,
                layoutUsage,
                layoutCaution,
                tvEfficacy,
                tvUsage,
                tvCaution,
                tvNoDetail
        );
    }

    private void loadDrugDetail(
            Pill pill,
            LinearLayout layoutEfficacy,
            LinearLayout layoutUsage,
            LinearLayout layoutCaution,
            TextView tvEfficacy,
            TextView tvUsage,
            TextView tvCaution,
            TextView tvNoDetail
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

                            applyDetailToViews(
                                    "",
                                    "",
                                    "",
                                    layoutEfficacy,
                                    layoutUsage,
                                    layoutCaution,
                                    tvEfficacy,
                                    tvUsage,
                                    tvCaution,
                                    tvNoDetail
                            );
                            return;
                        }

                        DrugDetailEnvelope.Item item = response.body().body.items.get(0);

                        pill.efficacy = cleanHtml(item.efcyQesitm);
                        pill.usage = cleanHtml(item.useMethodQesitm);
                        pill.caution = mergeNonEmpty(
                                cleanHtml(item.atpnWarnQesitm),
                                cleanHtml(item.atpnQesitm),
                                cleanHtml(item.intrcQesitm),
                                cleanHtml(item.seQesitm),
                                cleanHtml(item.depositMethodQesitm)
                        );

                        PillStorage.upsert(PillListActivity.this, pill);

                        applyDetailToViews(
                                pill.efficacy,
                                pill.usage,
                                pill.caution,
                                layoutEfficacy,
                                layoutUsage,
                                layoutCaution,
                                tvEfficacy,
                                tvUsage,
                                tvCaution,
                                tvNoDetail
                        );
                    }

                    @Override
                    public void onFailure(
                            @NonNull Call<DrugDetailEnvelope> call,
                            @NonNull Throwable t) {

                        Log.e("API_TEST", "API 실패: " + t.getMessage());

                        applyDetailToViews(
                                "",
                                "",
                                "",
                                layoutEfficacy,
                                layoutUsage,
                                layoutCaution,
                                tvEfficacy,
                                tvUsage,
                                tvCaution,
                                tvNoDetail
                        );

                        Toast.makeText(
                                PillListActivity.this,
                                R.string.detail_load_failed,
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                });
    }

    private void applyDetailToViews(
            String efficacy,
            String usage,
            String caution,
            LinearLayout layoutEfficacy,
            LinearLayout layoutUsage,
            LinearLayout layoutCaution,
            TextView tvEfficacy,
            TextView tvUsage,
            TextView tvCaution,
            TextView tvNoDetail
    ) {
        boolean noEfficacy = isEmpty(efficacy);
        boolean noUsage = isEmpty(usage);
        boolean noCaution = isEmpty(caution);

        boolean allEmpty = noEfficacy && noUsage && noCaution;

        if (allEmpty) {
            tvNoDetail.setVisibility(View.VISIBLE);
            tvNoDetail.setText(getString(R.string.no_detail_message));

            layoutEfficacy.setVisibility(View.GONE);
            layoutUsage.setVisibility(View.GONE);
            layoutCaution.setVisibility(View.GONE);
            return;
        }

        tvNoDetail.setVisibility(View.GONE);

        layoutEfficacy.setVisibility(View.VISIBLE);
        layoutUsage.setVisibility(View.VISIBLE);
        layoutCaution.setVisibility(View.VISIBLE);

        tvEfficacy.setText(noEfficacy ? getString(R.string.info_not_found) : efficacy);
        tvUsage.setText(noUsage ? getString(R.string.info_not_found) : usage);
        tvCaution.setText(noCaution ? getString(R.string.info_not_found) : caution);
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