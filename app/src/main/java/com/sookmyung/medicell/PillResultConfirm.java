package com.sookmyung.medicell;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class PillResultConfirm extends AppCompatActivity {

    private static final String TAG = "PillResultConfirm";

    private static final String PILL_INFO_BASE =
            "https://apis.data.go.kr/1471000/" +
                    "MdcinGrnIdntfcInfoService03/" +
                    "getMdcinGrnIdntfcInfoList03";
    private static final String API_KEY = "d3ba1256acc493aa40399b9f6c3e345f575156f91bb51050d066a63fdc29bc88";

    private ImageView ivPillImage;
    private TextView  tvRank;
    private TextView  tvPillName;
    private TextView  tvScore;
    private Button    btnConfirm;
    private Button    btnNext;
    private Button    btnNone;

    private Handler mainHandler;

    // 추론 결과
    private List<String> itemSeqs  = new ArrayList<>();
    private List<Float>  scores    = new ArrayList<>();
    private List<String> itemNames = new ArrayList<>();
    private List<String> imageUrls = new ArrayList<>();

    private int    currentIdx = 0;
    private String userPrint  = "";
    private String photoUri   = "";

    private Bitmap photoBitmap;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pill_result_confirm);

        mainHandler = new Handler(Looper.getMainLooper());

        ivPillImage = findViewById(R.id.ivPillImage);
        tvRank      = findViewById(R.id.tvRank);
        tvPillName  = findViewById(R.id.tvPillName);
        tvScore     = findViewById(R.id.tvScore);
        btnConfirm  = findViewById(R.id.btnConfirm);
        btnNext     = findViewById(R.id.btnNext);
        btnNone     = findViewById(R.id.btnNone);

        // Intent에서 받기
        Intent intent = getIntent();
        String up = intent.getStringExtra("user_print");
        String pu = intent.getStringExtra("photo_uri");
        if (up != null) userPrint = up;
        if (pu != null) photoUri  = pu;

        // 로딩 표시
        tvPillName.setText("분석 중...");
        tvRank.setText("- / -");
        btnConfirm.setEnabled(false);
        btnNext.setEnabled(false);

        // 버튼 리스너
        btnConfirm.setOnClickListener(v ->
                goToPillResult(currentIdx));

        btnNext.setOnClickListener(v -> {
            int next = currentIdx + 1;
            if (next < itemSeqs.size()) {
                showCard(next);
            } else {
                btnNone.setVisibility(View.VISIBLE);
                Toast.makeText(this,
                        "모든 후보를 확인했습니다.",
                        Toast.LENGTH_SHORT).show();
            }
        });

        btnNone.setOnClickListener(v -> {
            Toast.makeText(this,
                    "다시 촬영해 주세요.",
                    Toast.LENGTH_SHORT).show();
            finish();
        });

        // 백그라운드: 비트맵 로드 → 추론 → API
        new Thread(() -> {
            try {
                // 1. 비트맵 로드
                Bitmap bmp = PillStorage.getPendingBitmap();
                if (bmp == null && !photoUri.isEmpty()) {
                    Uri uri = Uri.parse(photoUri);
                    bmp = loadBitmapFromUri(uri);
                }
                if (bmp == null) {
                    mainHandler.post(() -> {
                        Toast.makeText(this,
                                "이미지를 불러올 수 없습니다.",
                                Toast.LENGTH_SHORT).show();
                        finish();
                    });
                    return;
                }
                photoBitmap = bmp;

                // 2. 추론
                PillClassifier classifier =
                        new PillClassifier(getApplicationContext());
                PillClassifier.InferenceResult result =
                        classifier.classify(photoBitmap, userPrint);
                classifier.close();

                if (result == null
                        || result.top5Predictions == null
                        || result.top5Predictions.isEmpty()) {
                    mainHandler.post(() -> {
                        Toast.makeText(this,
                                "추론 결과가 없습니다.",
                                Toast.LENGTH_SHORT).show();
                        finish();
                    });
                    return;
                }

                // 3. Top5 item_seq, score 저장
                for (PillClassifier.Prediction p
                        : result.top5Predictions) {
                    itemSeqs.add(p.label);
                    scores.add(p.score);
                }

                // 4. API로 이름 + 이미지 URL 가져오기
                for (String seq : itemSeqs) {
                    String[] info = fetchNameAndImage(seq);
                    itemNames.add(info[0]);
                    imageUrls.add(info[1]);
                }

                // 5. 첫 번째 카드 표시
                mainHandler.post(() -> {
                    btnConfirm.setEnabled(true);
                    btnNext.setEnabled(true);
                    showCard(0);
                });

            } catch (Exception e) {
                Log.e(TAG, "오류: " + e.getMessage(), e);
                mainHandler.post(() -> {
                    Toast.makeText(this,
                            "오류: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                    finish();
                });
            }
        }).start();
    }


    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 카드 표시
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private void showCard(int idx) {
        currentIdx = idx;
        int total  = itemSeqs.size();

        tvRank.setText((idx + 1) + " / " + total);
        tvPillName.setText(
                idx < itemNames.size()
                        ? itemNames.get(idx)
                        : itemSeqs.get(idx));
        tvScore.setText(String.format("유사도 %.1f%%",
                idx < scores.size()
                        ? scores.get(idx) * 100 : 0f));

        // 마지막 카드면 해당없음 표시
        if (idx == total - 1)
            btnNone.setVisibility(View.VISIBLE);

        // 이미지 로드
        String imgUrl = idx < imageUrls.size()
                ? imageUrls.get(idx) : null;
        if (imgUrl != null && !imgUrl.isEmpty()) {
            loadImageAsync(imgUrl);
        } else {
            ivPillImage.setImageResource(
                    android.R.drawable.ic_menu_gallery);
        }
    }


    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // PillResult로 이동
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private void goToPillResult(int idx) {
        if (photoBitmap != null)
            PillStorage.setPendingBitmap(photoBitmap);

        Intent i = new Intent(this, PillResult.class);
        i.putExtra("photo_uri",          photoUri);
        i.putExtra("user_print",         userPrint);
        i.putExtra("confirmed_item_seq",
                itemSeqs.get(idx));
        i.putExtra("confirmed_item_name",
                idx < itemNames.size()
                        ? itemNames.get(idx) : "");
        startActivity(i);
        finish();
    }


    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // API: 이름 + 이미지 URL
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private String[] fetchNameAndImage(String itemSeq) {
        String name   = itemSeq;
        String imgUrl = "";

        HttpURLConnection conn = null;
        try {
            String urlStr = PILL_INFO_BASE
                    + "?serviceKey=" + API_KEY
                    + "&pageNo=1&numOfRows=1&type=xml"
                    + "&item_seq="
                    + URLEncoder.encode(itemSeq, "UTF-8");

            conn = (HttpURLConnection)
                    new URL(urlStr).openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestMethod("GET");

            if (conn.getResponseCode()
                    == HttpURLConnection.HTTP_OK) {
                DocumentBuilder db =
                        DocumentBuilderFactory.newInstance()
                                .newDocumentBuilder();
                Document doc =
                        db.parse(conn.getInputStream());
                doc.getDocumentElement().normalize();
                NodeList nodes =
                        doc.getElementsByTagName("item");

                if (nodes != null
                        && nodes.getLength() > 0) {
                    Element item = (Element) nodes.item(0);
                    String n = getTagText(item, "ITEM_NAME");
                    String u = getTagText(item, "ITEM_IMAGE");
                    if (n != null && !n.isEmpty()) name   = n;
                    if (u != null && !u.isEmpty()) imgUrl = u;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "API 실패: " + itemSeq
                    + "  " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
        return new String[]{name, imgUrl};
    }


    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 이미지 비동기 로드
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private void loadImageAsync(String url) {
        ivPillImage.setImageResource(
                android.R.drawable.ic_menu_gallery);
        new Thread(() -> {
            try {
                HttpURLConnection conn =
                        (HttpURLConnection)
                                new URL(url).openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.connect();
                InputStream is = conn.getInputStream();
                Bitmap bmp = BitmapFactory.decodeStream(is);
                conn.disconnect();
                if (bmp != null)
                    mainHandler.post(() ->
                            ivPillImage.setImageBitmap(bmp));
            } catch (Exception e) {
                Log.e(TAG, "이미지 로드 실패: "
                        + e.getMessage());
            }
        }).start();
    }


    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // URI → Bitmap
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private Bitmap loadBitmapFromUri(Uri uri)
            throws Exception {
        Bitmap bmp;
        if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.Source src =
                    ImageDecoder.createSource(
                            getContentResolver(), uri);
            bmp = ImageDecoder.decodeBitmap(src,
                    (decoder, info, source) ->
                            decoder.setAllocator(
                                    ImageDecoder
                                            .ALLOCATOR_SOFTWARE));
        } else {
            bmp = MediaStore.Images.Media.getBitmap(
                    getContentResolver(), uri);
        }
        if (bmp == null) return null;
        return bmp.copy(Bitmap.Config.ARGB_8888, false);
    }

    private String getTagText(Element parent, String tag) {
        NodeList list = parent.getElementsByTagName(tag);
        if (list == null || list.getLength() == 0)
            return null;
        if (list.item(0) == null
                || list.item(0).getFirstChild() == null)
            return null;
        return list.item(0).getFirstChild().getNodeValue();
    }
}