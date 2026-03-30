package com.sookmyung.medicell;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;
import java.util.Locale;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class PillResult extends AppCompatActivity {

    private static final String TAG = "PillResult";

    private static final String PILL_INFO_BASE =
            "https://apis.data.go.kr/1471000/MdcinGrnIdntfcInfoService03/getMdcinGrnIdntfcInfoList03";

    private static final String API_KEY = "d3ba1256acc493aa40399b9f6c3e345f575156f91bb51050d066a63fdc29bc88";

    private ImageView pillPhotoView;
    private TextView  pillLabelView;
    private TextView  pillContentView;
    private TextView  pillTop5View;

    private Bitmap         photoBitmap;
    private PillClassifier classifier;
    private Handler        mainHandler;

    private TextToSpeech tts;
    private String       spokenName;
    private String       spokenDetail;

    private String bestItemSeq;
    private String bestItemName;

    private String color1 = "전체";
    private String color2 = "전체";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pill_result);

        mainHandler     = new Handler(Looper.getMainLooper());
        pillPhotoView   = findViewById(R.id.pillphoto);
        pillLabelView   = findViewById(R.id.pilllabel);
        pillContentView = findViewById(R.id.pill_content);
        pillTop5View    = findViewById(R.id.pill_top5);

        // ── 색깔 Intent에서 받기 ──────────────────────────
        color1 = getIntent().getStringExtra("color1");
        color2 = getIntent().getStringExtra("color2");
        if (color1 == null) color1 = "전체";
        if (color2 == null) color2 = "전체";
        Log.d(TAG, "색깔 필터: color1=" + color1 + "  color2=" + color2);

        // ── 모델 로딩 상태 초기화 ─────────────────────────
        pillLabelView.setText("모델 로딩 중...");
        pillContentView.setText("");
        pillTop5View.setText("");

        // ── TTS 초기화 ────────────────────────────────────
        tts = new TextToSpeech(getApplicationContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                int r = tts.setLanguage(Locale.KOREAN);
                if (r == TextToSpeech.LANG_MISSING_DATA ||
                        r == TextToSpeech.LANG_NOT_SUPPORTED)
                    Log.e(TAG, "TTS: 한국어 미지원");
            } else {
                Log.e(TAG, "TTS 초기화 실패 status=" + status);
            }
        });

        // ── 음성 버튼 ─────────────────────────────────────
        View voiceBtn = findViewById(R.id.voice_pill);
        if (voiceBtn != null)
            voiceBtn.setOnClickListener(v -> speakPillInfo());

        // ── 다음 버튼 ─────────────────────────────────────
        View nextBtn = findViewById(R.id.next_list);
        if (nextBtn != null) {
            nextBtn.setOnClickListener(v -> {
                if (bestItemSeq == null || bestItemSeq.isEmpty()) {
                    Toast.makeText(this, "1위 알약 정보가 없습니다.",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent it = new Intent(PillResult.this, TabooCheck.class);
                it.putExtra("main_item_seq",  bestItemSeq);
                it.putExtra("main_item_name", bestItemName);
                startActivity(it);
            });
        }

        // ── 비트맵 + 모델 로딩 → 백그라운드 ─────────────
        String uriStr = getIntent().getStringExtra("photo_uri");

        new Thread(() -> {
            try {
                // 비트맵 로드
                Bitmap bmp = PillStorage.getPendingBitmap();
                Log.d(TAG, "★ PillStorage 비트맵: " + (bmp != null
                        ? bmp.getWidth() + "x" + bmp.getHeight()
                        : "NULL ← URI fallback 사용"));

                if (bmp == null && uriStr != null) {
                    Uri uri = Uri.parse(uriStr);
                    bmp = loadBitmapFromUri(uri);
                    Log.d(TAG, "URI 비트맵: " + (bmp != null
                            ? bmp.getWidth() + "x" + bmp.getHeight()
                            : "null"));
                }

                final Bitmap finalBmp = bmp;

                // 모델 로드
                PillClassifier cls =
                        new PillClassifier(getApplicationContext());
                Log.d(TAG, "PillClassifier 로드 성공");

                mainHandler.post(() -> {
                    photoBitmap = finalBmp;
                    classifier  = cls;

                    if (photoBitmap != null)
                        pillPhotoView.setImageBitmap(photoBitmap);

                    if (photoBitmap != null) {
                        pillLabelView.setText("분석 중...");
                        runClassificationInBackground();
                    } else {
                        pillLabelView.setText("분류 불가");
                        Log.e(TAG, "photoBitmap null");
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "로드 실패: " + e.getMessage(), e);
                mainHandler.post(() -> {
                    pillLabelView.setText("모델 로드 실패");
                    pillContentView.setText(e.getMessage());
                    Toast.makeText(getApplicationContext(),
                            "모델 로드 실패: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (classifier != null) classifier.close();
        if (tts != null) { tts.stop(); tts.shutdown(); }
    }


    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // URI → Bitmap
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private Bitmap loadBitmapFromUri(Uri uri) throws Exception {
        Bitmap bmp;
        if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.Source src =
                    ImageDecoder.createSource(getContentResolver(), uri);
            bmp = ImageDecoder.decodeBitmap(src, (decoder, info, source) ->
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE));
        } else {
            bmp = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
        }
        if (bmp == null) return null;
        return bmp.copy(Bitmap.Config.ARGB_8888, false);
    }


    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 백그라운드 추론 (색깔 필터 포함)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private void runClassificationInBackground() {
        new Thread(() -> {
            try {
                Log.d(TAG, "classify() 호출  bitmap="
                        + photoBitmap.getWidth() + "x"
                        + photoBitmap.getHeight()
                        + "  color1=" + color1
                        + "  color2=" + color2);

                PillClassifier.InferenceResult inference =
                        classifier.classify(photoBitmap, color1, color2);
                Log.d(TAG, "classify() 완료");

                if (inference == null
                        || inference.top5Predictions == null
                        || inference.top5Predictions.isEmpty()) {
                    Log.e(TAG, "추론 결과 없음");
                    mainHandler.post(() -> {
                        pillLabelView.setText("예측 결과 없음");
                        pillContentView.setText("");
                        pillTop5View.setText("");
                        spokenName = spokenDetail = null;
                        bestItemSeq = bestItemName = null;
                    });
                    return;
                }

                List<PillClassifier.Prediction> preds =
                        inference.top5Predictions;
                PillClassifier.Stage1Result s1 = inference.stage1Result;

                Log.d(TAG, "Top1=" + preds.get(0).label
                        + "  score=" + preds.get(0).score);

                PillClassifier.Prediction best     = preds.get(0);
                String                    bestSeq  = best.label;
                PillInfo                  bestInfo = fetchPillInfo(bestSeq);

                String bestNameLocal = bestSeq;
                if (bestInfo != null
                        && bestInfo.itemName != null
                        && !bestInfo.itemName.isEmpty())
                    bestNameLocal = bestInfo.itemName;

                String bestDetailLocal = buildDetailText(bestInfo, best, s1);

                StringBuilder top5Builder = new StringBuilder();
                for (int i = 0; i < preds.size(); i++) {
                    PillClassifier.Prediction p    = preds.get(i);
                    PillInfo                  info = fetchPillInfo(p.label);

                    String displayName = p.label;
                    if (info != null
                            && info.itemName != null
                            && !info.itemName.isEmpty())
                        displayName = info.itemName;

                    top5Builder.append(i + 1)
                            .append("위: ")
                            .append(displayName)
                            .append("  (유사도 ")
                            .append(String.format("%.1f%%", p.score * 100))
                            .append(")\n");
                }
                String top5Text = top5Builder.toString().trim();

                String finalBestName   = bestNameLocal;
                String finalBestDetail = bestDetailLocal;
                String finalBestSeq    = bestSeq;

                mainHandler.post(() -> {
                    pillLabelView.setText(finalBestName);
                    pillContentView.setText(finalBestDetail);
                    pillTop5View.setText(top5Text);
                    spokenName   = finalBestName;
                    spokenDetail = finalBestDetail;
                    bestItemSeq  = finalBestSeq;
                    bestItemName = finalBestName;
                });

            } catch (Exception e) {
                Log.e(TAG, "추론 오류: " + e.getMessage(), e);
                mainHandler.post(() -> {
                    pillLabelView.setText("오류 발생");
                    pillContentView.setText(e.getMessage());
                    pillTop5View.setText("");
                    spokenName = spokenDetail = null;
                    bestItemSeq = bestItemName = null;
                });
            }
        }).start();
    }


    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 상세 텍스트
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private String buildDetailText(PillInfo info,
                                   PillClassifier.Prediction best,
                                   PillClassifier.Stage1Result s1) {
        StringBuilder sb = new StringBuilder();

        if (s1 != null) {
            sb.append("모양: ")
                    .append(PillClassifier.shapeName(s1.predShape))
                    .append(String.format("  (%.1f%%)", s1.predProb * 100))
                    .append("\n");
        }
        sb.append("색깔: ").append(color1);
        if (!"전체".equals(color2)) sb.append(" / ").append(color2);
        sb.append("\n");
        sb.append("유사도: ")
                .append(String.format("%.1f%%", best.score * 100))
                .append("\n");
        sb.append("분류: ")
                .append(info == null || info.className == null
                        || info.className.isEmpty()
                        ? "정보 없음" : info.className)
                .append("\n");
        sb.append("설명: ")
                .append(info == null || info.chart == null
                        || info.chart.isEmpty()
                        ? "정보 없음" : info.chart);

        return sb.toString();
    }


    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 식약처 API
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private PillInfo fetchPillInfo(String itemSeq) {
        HttpURLConnection conn = null;
        try {
            String urlStr = PILL_INFO_BASE
                    + "?serviceKey=" + API_KEY
                    + "&pageNo=1&numOfRows=1&type=xml"
                    + "&item_seq=" + URLEncoder.encode(itemSeq, "UTF-8");

            conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestMethod("GET");

            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK)
                return null;

            DocumentBuilder db =
                    DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = db.parse(conn.getInputStream());
            doc.getDocumentElement().normalize();

            NodeList nodes = doc.getElementsByTagName("item");
            if (nodes == null || nodes.getLength() == 0) return null;

            Element  item = (Element) nodes.item(0);
            PillInfo info = new PillInfo();
            info.itemSeq   = itemSeq;
            info.itemName  = getTagText(item, "ITEM_NAME");
            info.className = getTagText(item, "CLASS_NAME");
            info.chart     = getTagText(item, "CHART");
            if (info.chart == null || info.chart.isEmpty())
                info.chart = getTagText(item, "DRUG_SHAPE");
            return info;

        } catch (Exception e) {
            Log.e(TAG, "API 실패: " + itemSeq + " " + e.getMessage());
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private String getTagText(Element parent, String tag) {
        NodeList list = parent.getElementsByTagName(tag);
        if (list == null || list.getLength() == 0) return null;
        if (list.item(0) == null
                || list.item(0).getFirstChild() == null) return null;
        return list.item(0).getFirstChild().getNodeValue();
    }

    private static class PillInfo {
        String itemSeq, itemName, className, chart;
    }

    private void speakPillInfo() {
        if (tts == null) return;
        StringBuilder sb = new StringBuilder();
        if (spokenName   != null && !spokenName.isEmpty())
            sb.append(spokenName).append(". ");
        if (spokenDetail != null && !spokenDetail.isEmpty())
            sb.append(spokenDetail);
        String text = sb.toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(this, "읽을 내용이 없습니다.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "pillResultTTS");
    }
}