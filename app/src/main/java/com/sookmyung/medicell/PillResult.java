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

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;
import java.util.Locale;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class PillResult extends AppCompatActivity {

    private static final String TAG = "PillResult";

    // 식약처 알약식별 API
    private static final String PILL_INFO_BASE =
            "https://apis.data.go.kr/1471000/MdcinGrnIdntfcInfoService03/getMdcinGrnIdntfcInfoList03";

    // 인증키
    private static final String API_KEY =
            "d3ba1256acc493aa40399b9f6c3e345f575156f91bb51050d066a63fdc29bc88";

    private ImageView pillPhotoView;
    private TextView pillLabelView;
    private TextView pillContentView;
    private TextView pillTop5View;

    private Bitmap photoBitmap;
    private PillClassifier classifier;
    private Handler mainHandler;

    // TTS
    private TextToSpeech tts;
    private String spokenName;
    private String spokenDetail;

    // 다음 페이지 전달용
    private String bestItemSeq;
    private String bestItemName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pill_result);

        mainHandler = new Handler(Looper.getMainLooper());

        pillPhotoView   = findViewById(R.id.pillphoto);
        pillLabelView   = findViewById(R.id.pilllabel);
        pillContentView = findViewById(R.id.pill_content);
        pillTop5View    = findViewById(R.id.pill_top5);

        // 사진 URI 받아서 표시
        String uriStr = getIntent().getStringExtra("photo_uri");
        if (uriStr != null) {
            try {
                Uri uri = Uri.parse(uriStr);
                photoBitmap = loadBitmapFromUri(uri);
                if (photoBitmap != null) {
                    pillPhotoView.setImageBitmap(photoBitmap);
                }
            } catch (Exception e) {
                Log.e(TAG, "사진 로드 실패", e);
                Toast.makeText(this, "사진 로드 실패", Toast.LENGTH_SHORT).show();
            }
        }

        // 분류기 로드
        try {
            classifier = new PillClassifier(getApplicationContext());
        } catch (Exception e) {
            Log.e(TAG, "PillClassifier 로드 실패", e);
            Toast.makeText(this, "모델 로드 실패", Toast.LENGTH_SHORT).show();
            classifier = null;
        }

        // TTS 초기화
        tts = new TextToSpeech(getApplicationContext(), status -> {
            if (status == TextToSpeech.SUCCESS) {
                int r = tts.setLanguage(Locale.KOREAN);
                if (r == TextToSpeech.LANG_MISSING_DATA ||
                        r == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "TTS: 한국어 미지원");
                }
            } else {
                Log.e(TAG, "TTS 초기화 실패");
            }
        });

        View voiceBtn = findViewById(R.id.voice_pill);
        if (voiceBtn != null) {
            voiceBtn.setOnClickListener(v -> speakPillInfo());
        }

        View nextBtn = findViewById(R.id.next_list);
        if (nextBtn != null) {
            nextBtn.setOnClickListener(v -> {
                if (bestItemSeq == null || bestItemSeq.isEmpty()) {
                    Toast.makeText(this, "1위 알약 정보가 없습니다.", Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent it = new Intent(PillResult.this, TabooCheck.class);
                it.putExtra("main_item_seq", bestItemSeq);
                it.putExtra("main_item_name", bestItemName);
                startActivity(it);
            });
        }

        if (photoBitmap != null && classifier != null) {
            runClassificationInBackground();
        } else {
            pillLabelView.setText("분류 불가");
            pillContentView.setText("");
            pillTop5View.setText("");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (classifier != null) classifier.close();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }

    /** URI -> Bitmap */
    private Bitmap loadBitmapFromUri(Uri uri) throws Exception {
        Bitmap bmp;
        if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.Source src = ImageDecoder.createSource(getContentResolver(), uri);
            bmp = ImageDecoder.decodeBitmap(src);
        } else {
            bmp = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
        }
        if (bmp == null) return null;
        return bmp.copy(Bitmap.Config.ARGB_8888, false);
    }

    /** AI 분류 + itemSeq -> API로 이름/설명 붙이기 */
    private void runClassificationInBackground() {
        new Thread(() -> {
            try {
                PillClassifier.InferenceResult inference = classifier.clssify(photoBitmap);

                if (inference == null ||
                        inference.top5Predictions == null ||
                        inference.top5Predictions.isEmpty()) {

                    mainHandler.post(() -> {
                        pillLabelView.setText("예측 결과 없음");
                        pillContentView.setText("");
                        pillTop5View.setText("");
                        spokenName = null;
                        spokenDetail = null;
                        bestItemSeq = null;
                        bestItemName = null;
                    });
                    return;
                }

                List<PillClassifier.Prediction> preds = inference.top5Predictions;

                // 1위
                PillClassifier.Prediction best = preds.get(0);
                String bestSeqLocal = best.label;

                PillInfo bestInfo = fetchPillInfo(bestSeqLocal);

                String bestNameLocal;
                if (bestInfo != null && bestInfo.itemName != null && !bestInfo.itemName.isEmpty()) {
                    bestNameLocal = bestInfo.itemName;
                } else {
                    bestNameLocal = bestSeqLocal;
                }

                String bestDetailLocal;
                if (bestInfo != null) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("분류: ")
                            .append(bestInfo.className == null || bestInfo.className.isEmpty() ? "정보 없음" : bestInfo.className)
                            .append("\n");
                    sb.append("설명: ")
                            .append(bestInfo.chart == null || bestInfo.chart.isEmpty() ? "정보 없음" : bestInfo.chart);
                    bestDetailLocal = sb.toString();
                } else {
                    bestDetailLocal = "분류: 정보 없음\n설명: 정보 없음";
                }

                // 2~5위 이름만
                StringBuilder top5Builder = new StringBuilder();
                for (int i = 1; i < preds.size() && i < 5; i++) {
                    PillClassifier.Prediction p = preds.get(i);

                    PillInfo info = fetchPillInfo(p.label);
                    String displayName;
                    if (info != null && info.itemName != null && !info.itemName.isEmpty()) {
                        displayName = info.itemName;
                    } else {
                        displayName = p.label;
                    }

                    top5Builder.append(i + 1)
                            .append("위: ")
                            .append(displayName)
                            .append("\n");
                }

                String top5TextLocal = top5Builder.toString();

                mainHandler.post(() -> {
                    pillLabelView.setText(bestNameLocal);
                    pillContentView.setText(bestDetailLocal);
                    pillTop5View.setText(top5TextLocal);

                    spokenName = bestNameLocal;
                    spokenDetail = bestDetailLocal;

                    bestItemSeq = bestSeqLocal;
                    bestItemName = bestNameLocal;
                });

            } catch (Exception e) {
                Log.e(TAG, "분류 오류", e);

                mainHandler.post(() -> {
                    pillLabelView.setText("오류");
                    pillContentView.setText("");
                    pillTop5View.setText("");
                    spokenName = null;
                    spokenDetail = null;
                    bestItemSeq = null;
                    bestItemName = null;
                });
            }
        }).start();
    }

    /** itemSeq로 알약명/분류/설명 조회 */
    private PillInfo fetchPillInfo(String itemSeq) {
        HttpURLConnection conn = null;
        try {
            StringBuilder urlBuilder = new StringBuilder(PILL_INFO_BASE);
            urlBuilder.append("?serviceKey=").append(API_KEY);
            urlBuilder.append("&pageNo=1");
            urlBuilder.append("&numOfRows=1");
            urlBuilder.append("&type=xml");
            urlBuilder.append("&item_seq=").append(URLEncoder.encode(itemSeq, "UTF-8"));

            URL url = new URL(urlBuilder.toString());
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestMethod("GET");

            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                Log.d(TAG, "알약식별 HTTP 코드: " + code + " for itemSeq=" + itemSeq);
                return null;
            }

            InputStream is = conn.getInputStream();
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(is);
            doc.getDocumentElement().normalize();

            NodeList nodes = doc.getElementsByTagName("item");
            if (nodes == null || nodes.getLength() == 0) {
                Log.d(TAG, "알약식별 응답에 item 노드 없음 (itemSeq=" + itemSeq + ")");
                return null;
            }

            Element item = (Element) nodes.item(0);

            String itemName  = getTagText(item, "ITEM_NAME");
            String className = getTagText(item, "CLASS_NAME");
            String chart     = getTagText(item, "CHART");
            if (chart == null || chart.isEmpty()) {
                chart = getTagText(item, "DRUG_SHAPE");
            }

            PillInfo info = new PillInfo();
            info.itemSeq   = itemSeq;
            info.itemName  = itemName;
            info.className = className;
            info.chart     = chart;
            return info;

        } catch (Exception e) {
            Log.e(TAG, "알약식별 API 조회 실패 (itemSeq=" + itemSeq + ")", e);
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /** XML에서 특정 태그 텍스트 얻기 */
    private String getTagText(Element parent, String tag) {
        NodeList list = parent.getElementsByTagName(tag);
        if (list == null || list.getLength() == 0) return null;
        if (list.item(0).getFirstChild() == null) return null;
        return list.item(0).getFirstChild().getNodeValue();
    }

    /** 알약 정보 모델 */
    private static class PillInfo {
        String itemSeq;
        String itemName;
        String className;
        String chart;
    }

    /** TTS */
    private void speakPillInfo() {
        if (tts == null) return;

        StringBuilder sb = new StringBuilder();

        if (spokenName != null && !spokenName.isEmpty()) {
            sb.append(spokenName).append(". ");
        }

        if (spokenDetail != null && !spokenDetail.isEmpty()) {
            sb.append(spokenDetail);
        }

        String text = sb.toString();

        if (text.isEmpty()) {
            Toast.makeText(this, "읽을 내용이 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "pillResultTTS");
    }
}