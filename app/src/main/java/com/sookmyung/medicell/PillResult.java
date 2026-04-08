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
import java.util.Locale;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class PillResult extends AppCompatActivity {

    private static final String TAG = "PillResult";

    private static final String PILL_INFO_BASE =
            "https://apis.data.go.kr/1471000/" +
                    "MdcinGrnIdntfcInfoService03/" +
                    "getMdcinGrnIdntfcInfoList03";
    private static final String API_KEY = "d3ba1256acc493aa40399b9f6c3e345f575156f91bb51050d066a63fdc29bc88";

    private ImageView pillPhotoView;
    private TextView  pillLabelView;
    private TextView  pillContentView;

    private Handler mainHandler;

    private TextToSpeech tts;
    private String       spokenName;
    private String       spokenDetail;

    private String bestItemSeq;
    private String bestItemName;

    private String userPrint           = "";
    private String confirmedItemSeq    = "";
    private String confirmedItemName   = "";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pill_result);

        mainHandler     = new Handler(Looper.getMainLooper());
        pillPhotoView   = findViewById(R.id.pillphoto);
        pillLabelView   = findViewById(R.id.pilllabel);
        pillContentView = findViewById(R.id.pill_content);

        // ── Intent에서 받기 ───────────────────────────────
        Intent intent = getIntent();
        String up  = intent.getStringExtra("user_print");
        String seq = intent.getStringExtra("confirmed_item_seq");
        String nm  = intent.getStringExtra("confirmed_item_name");
        if (up  != null) userPrint         = up;
        if (seq != null) confirmedItemSeq  = seq;
        if (nm  != null) confirmedItemName = nm;

        Log.d(TAG, "confirmed_item_seq=" + confirmedItemSeq
                + "  confirmed_item_name=" + confirmedItemName
                + "  userPrint=" + userPrint);

        // ── 초기 상태 ─────────────────────────────────────
        pillLabelView.setText(
                confirmedItemName.isEmpty()
                        ? "정보 불러오는 중..." : confirmedItemName);
        pillContentView.setText("");

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

        // ── 다음 버튼 (TabooCheck) ─────────────────────────
        View nextBtn = findViewById(R.id.next_list);
        if (nextBtn != null) {
            nextBtn.setOnClickListener(v -> {
                if (bestItemSeq == null
                        || bestItemSeq.isEmpty()) {
                    Toast.makeText(this,
                            "알약 정보가 없습니다.",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent i = new Intent(
                        PillResult.this, TabooCheck.class);
                i.putExtra("main_item_seq",  bestItemSeq);
                i.putExtra("main_item_name", bestItemName);
                startActivity(i);
            });
        }

        // ── 비트맵 표시 + API 호출 → 백그라운드 ──────────
        String uriStr = intent.getStringExtra("photo_uri");

        new Thread(() -> {
            try {
                // 비트맵 로드 (사진 표시용)
                Bitmap bmp = PillStorage.getPendingBitmap();
                if (bmp == null && uriStr != null) {
                    Uri uri = Uri.parse(uriStr);
                    bmp = loadBitmapFromUri(uri);
                }
                final Bitmap finalBmp = bmp;

                // 확정된 item_seq로 상세 정보 API 호출
                PillInfo info = fetchPillInfo(confirmedItemSeq);

                // 이름 결정
                String finalName = confirmedItemName;
                if ((finalName == null || finalName.isEmpty())
                        && info != null
                        && info.itemName != null
                        && !info.itemName.isEmpty()) {
                    finalName = info.itemName;
                }
                if (finalName == null || finalName.isEmpty())
                    finalName = confirmedItemSeq;

                String finalDetail = buildDetailText(info);
                String fn = finalName;

                mainHandler.post(() -> {
                    if (finalBmp != null)
                        pillPhotoView.setImageBitmap(finalBmp);
                    pillLabelView.setText(fn);
                    pillContentView.setText(finalDetail);
                    spokenName   = fn;
                    spokenDetail = finalDetail;
                    bestItemSeq  = confirmedItemSeq;
                    bestItemName = fn;
                });

            } catch (Exception e) {
                Log.e(TAG, "오류: " + e.getMessage(), e);
                mainHandler.post(() -> {
                    pillLabelView.setText("오류 발생");
                    pillContentView.setText(e.getMessage());
                });
            }
        }).start();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tts != null) { tts.stop(); tts.shutdown(); }
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


    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 상세 텍스트
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private String buildDetailText(PillInfo info) {
        StringBuilder sb = new StringBuilder();


        sb.append("분류: ")
                .append(info == null
                        || info.className == null
                        || info.className.isEmpty()
                        ? "정보 없음" : info.className)
                .append("\n");
        sb.append("설명: ")
                .append(info == null
                        || info.chart == null
                        || info.chart.isEmpty()
                        ? "정보 없음" : info.chart);

        return sb.toString();
    }


    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // 식약처 API
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    private PillInfo fetchPillInfo(String itemSeq) {
        if (itemSeq == null || itemSeq.isEmpty()) return null;
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
                    != HttpURLConnection.HTTP_OK)
                return null;

            DocumentBuilder db =
                    DocumentBuilderFactory.newInstance()
                            .newDocumentBuilder();
            Document doc = db.parse(conn.getInputStream());
            doc.getDocumentElement().normalize();

            NodeList nodes =
                    doc.getElementsByTagName("item");
            if (nodes == null || nodes.getLength() == 0)
                return null;

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
            Log.e(TAG, "API 실패: " + itemSeq
                    + "  " + e.getMessage());
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
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

    private static class PillInfo {
        String itemSeq, itemName, className, chart;
    }

    private void speakPillInfo() {
        if (tts == null) return;
        StringBuilder sb = new StringBuilder();
        if (spokenName != null && !spokenName.isEmpty())
            sb.append(spokenName).append(". ");
        if (spokenDetail != null && !spokenDetail.isEmpty())
            sb.append(spokenDetail);
        String text = sb.toString().trim();
        if (text.isEmpty()) {
            Toast.makeText(this, "읽을 내용이 없습니다.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH,
                null, "pillResultTTS");
    }
}