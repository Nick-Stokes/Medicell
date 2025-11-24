package com.sookmyung.medicell;

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
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class PillResult extends AppCompatActivity {

    private static final String TAG = "PillResult";

    // 식약처 알약식별 API
    private static final String PILL_INFO_BASE =
            "https://apis.data.go.kr/1471000/MdcinGrnIdntfcInfoService03/getMdcinGrnIdntfcInfoList03";

    // DUR 병용금기 API
    private static final String DUR_TABOO_BASE =
            "https://apis.data.go.kr/1471000/DURPrdlstInfoService03/getUsjntTabooInfoList03";

    // 공통 인증키
    private static final String API_KEY =
            "d3ba1256acc493aa40399b9f6c3e345f575156f91bb51050d066a63fdc29bc88";

    private ImageView pillPhotoView;
    private TextView pillLabelView;
    private TextView pillContentView;
    private TextView pillTop5View;

    private Bitmap photoBitmap;
    private PillClassifier classifier;
    private Handler mainHandler;

    // TTS 관련
    private TextToSpeech tts;
    private String spokenName;    // 1위 약 이름
    private String spokenDetail;  // pillContentView 텍스트

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
                    Log.e(TAG, "TTS: 한국어 미지원 또는 데이터 없음");
                }
            } else {
                Log.e(TAG, "TTS 초기화 실패");
            }
        });

        // 음성 버튼 (id = voice_pill) 클릭 시 TTS로 읽어주기
        View voiceBtn = findViewById(R.id.voice_pill);
        if (voiceBtn != null) {
            voiceBtn.setOnClickListener(v -> speakPillInfo());
        }

        if (photoBitmap != null && classifier != null) {
            runClassificationInBackground();
        } else {
            pillLabelView.setText("분류 불가");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
    }

    /** URI에서 Bitmap 읽기 (HARDWARE config 피해서) */
    private Bitmap loadBitmapFromUri(Uri uri) throws Exception {
        Bitmap bmp;
        if (Build.VERSION.SDK_INT >= 28) {
            ImageDecoder.Source src = ImageDecoder.createSource(getContentResolver(), uri);
            bmp = ImageDecoder.decodeBitmap(src, (decoder, info, src2) ->
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)); // HARDWARE 방지
        } else {
            bmp = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
        }
        if (bmp == null) return null;
        return bmp.copy(Bitmap.Config.ARGB_8888, false);
    }

    /**
     * 분류:
     *  - 예측 상위 100개에 대해 병용금기 API + 알약식별 API 둘 다 검사
     *  - 둘 중 하나라도 DB에 있는 애들만 후보로 모아 (union),
     *    예측 순서를 유지한 채 앞에서부터 최대 5개까지 화면에 표시
     *  - 만약 둘 다에 없는 애들 뿐이면
     *    → 원래 예측 상위 1~5위를 알약식별 API 기준으로 보여줌
     */
    private void runClassificationInBackground() {
        new Thread(() -> {
            try {
                // 1) TFLite 예측
                List<PillClassifier.Prediction> preds = classifier.clssify(photoBitmap);
                if (preds == null || preds.isEmpty()) {
                    mainHandler.post(() -> {
                        pillLabelView.setText("예측 결과 없음");
                        pillTop5View.setText("");
                        pillContentView.setText("");
                        spokenName = null;
                        spokenDetail = null;
                    });
                    return;
                }

                // 2) 예측 상위 100개까지:
                //    병용금기 API, 알약식별 API 둘 다 호출해서
                //    어느 쪽이든 존재하는 애들만 후보로 모은다 (union).
                List<PillInfo> candidates = new ArrayList<>();
                int maxCheck = Math.min(100, preds.size());

                for (int i = 0; i < maxCheck; i++) {
                    PillClassifier.Prediction p = preds.get(i);

                    // 여기서 p.label 은 pill_labels.txt 를 통해 class → itemSeq 로 변환된 값이라고 가정
                    String itemSeq = p.label; // 품목기준코드

                    // (1) 병용금기 API
                    PillInfo tabooInfo = fetchTabooInfo(itemSeq);
                    // (2) 알약식별 API
                    PillInfo idInfo = fetchPillInfo(itemSeq);

                    // 둘 다 null이면 DB에 없는 것으로 보고 스킵
                    if (tabooInfo == null && idInfo == null) {
                        continue;
                    }

                    // (3) 정보 병합
                    PillInfo merged = new PillInfo();
                    merged.itemSeq = itemSeq;

                    // 이름: 알약식별 > 병용금기
                    if (idInfo != null && idInfo.itemName != null) {
                        merged.itemName = idInfo.itemName;
                    } else if (tabooInfo != null) {
                        merged.itemName = tabooInfo.itemName;
                    }

                    // 분류: 알약식별 > 병용금기
                    if (idInfo != null && idInfo.className != null) {
                        merged.className = idInfo.className;
                    } else if (tabooInfo != null) {
                        merged.className = tabooInfo.className;
                    }

                    // 외형: 알약식별 > 병용금기
                    if (idInfo != null && idInfo.chart != null) {
                        merged.chart = idInfo.chart;
                    } else if (tabooInfo != null) {
                        merged.chart = tabooInfo.chart;
                    }

                    // 이름/분류/외형 전부 null인 경우는 버림
                    if (merged.itemName == null && merged.className == null && merged.chart == null) {
                        continue;
                    }

                    candidates.add(merged);
                }

                String finalTopName;
                String finalTopDetail;
                String finalTop5Text;

                // 3-A) 병용금기/알약식별 어느 쪽이든 DB에 존재하는 후보가 하나라도 있을 때:
                //      → 그 후보 리스트(candidates)에서 앞에서부터 최대 5개 사용
                if (!candidates.isEmpty()) {

                    StringBuilder top5Builder = new StringBuilder();
                    int limit = Math.min(5, candidates.size());

                    for (int i = 0; i < limit; i++) {
                        PillInfo info = candidates.get(i);
                        int rank = i + 1;

                        String displayName =
                                (info.itemName != null) ? info.itemName : ("코드 " + info.itemSeq);

                        top5Builder
                                .append(rank)
                                .append("위: ")
                                .append(displayName)
                                .append(" (")
                                .append(info.itemSeq)
                                .append(")\n");
                    }

                    // 최상위(1위) 후보 상세 정보 = candidates[0]
                    PillInfo best = candidates.get(0);

                    if (best.itemName != null) {
                        finalTopName = best.itemName;
                    } else {
                        finalTopName = "코드 " + best.itemSeq;
                    }

                    if (best.chart != null || best.className != null) {
                        StringBuilder sb = new StringBuilder();
                        sb.append("분류: ")
                                .append(best.className == null ? "정보 없음" : best.className)
                                .append("\n");
                        sb.append("외형: ")
                                .append(best.chart == null ? "정보 없음" : best.chart);
                        finalTopDetail = sb.toString();
                    } else {
                        finalTopDetail = "해당 코드에 대한 알약 정보가 없습니다.";
                    }

                    finalTop5Text = top5Builder.toString();
                }
                // 3-B) 둘 다에 존재하는 후보가 하나도 없을 때:
                //      → 그냥 원래 예측 상위 1~5위를 알약식별 API 기준으로 보여줌
                else {
                    StringBuilder fallbackBuilder = new StringBuilder();

                    String fbTopName = null;
                    String fbTopDetail = null;

                    int rank = 1;
                    for (PillClassifier.Prediction p : preds) {
                        if (rank > 5) break;

                        String itemSeq = p.label; // pill_labels.txt 에서 얻은 itemSeq
                        PillInfo info = fetchPillInfo(itemSeq);

                        String nameForLine =
                                (info != null && info.itemName != null)
                                        ? info.itemName
                                        : ("코드 " + itemSeq);

                        fallbackBuilder
                                .append(rank)
                                .append("위: ")
                                .append(nameForLine)
                                .append(" (")
                                .append(itemSeq)
                                .append(")\n");

                        if (rank == 1) {
                            fbTopName = nameForLine;
                            if (info != null && (info.chart != null || info.className != null)) {
                                StringBuilder sb = new StringBuilder();
                                sb.append("분류: ")
                                        .append(info.className == null ? "정보 없음" : info.className)
                                        .append("\n");
                                sb.append("외형: ")
                                        .append(info.chart == null ? "정보 없음" : info.chart);
                                fbTopDetail = sb.toString();
                            } else {
                                fbTopDetail = "해당 코드에 대한 알약 정보가 없습니다.";
                            }
                        }

                        rank++;
                    }

                    if (fbTopName == null) {
                        fbTopName = "예측 결과 없음";
                    }
                    if (fbTopDetail == null) {
                        fbTopDetail = "해당 코드에 대한 알약 정보가 없습니다.";
                    }

                    finalTopName   = fbTopName;
                    finalTopDetail = fbTopDetail;
                    finalTop5Text  = fallbackBuilder.toString();
                }

                // 4) UI 갱신 + TTS용 텍스트 저장
                String finalTopNameCopy   = finalTopName;
                String finalTopDetailCopy = finalTopDetail;
                String finalTop5TextCopy  = finalTop5Text;

                mainHandler.post(() -> {
                    pillLabelView.setText(finalTopNameCopy);
                    pillContentView.setText(finalTopDetailCopy);
                    pillTop5View.setText(finalTop5TextCopy);

                    spokenName   = finalTopNameCopy;
                    spokenDetail = finalTopDetailCopy;
                });

            } catch (Exception e) {
                Log.e(TAG, "분류/조회 중 오류", e);
                mainHandler.post(() -> {
                    Toast.makeText(PillResult.this, "결과 처리 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show();
                    pillLabelView.setText("오류");
                    spokenName = null;
                    spokenDetail = null;
                });
            }
        }).start();
    }

    /**
     * DUR 병용금기 API에서 itemSeq로 데이터 조회
     * - 존재 여부 + 이름/분류/외형까지 반환
     */
    private PillInfo fetchTabooInfo(String itemSeq) {
        HttpURLConnection conn = null;
        try {
            StringBuilder urlBuilder = new StringBuilder(DUR_TABOO_BASE);
            urlBuilder.append("?serviceKey=").append(API_KEY);
            urlBuilder.append("&pageNo=1");
            urlBuilder.append("&numOfRows=1");
            urlBuilder.append("&type=xml");
            urlBuilder.append("&itemSeq=").append(URLEncoder.encode(itemSeq, "UTF-8"));

            URL url = new URL(urlBuilder.toString());
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestMethod("GET");

            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                Log.d(TAG, "DUR(병용금기) HTTP 코드: " + code + " for itemSeq=" + itemSeq);
                return null;
            }

            InputStream is = conn.getInputStream();
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document doc = db.parse(is);
            doc.getDocumentElement().normalize();

            NodeList itemNodes = doc.getElementsByTagName("item");
            if (itemNodes == null || itemNodes.getLength() == 0) {
                Log.d(TAG, "DUR(병용금기) 응답에 item 노드 없음 (itemSeq=" + itemSeq + ")");
                return null;
            }

            Element item = (Element) itemNodes.item(0);

            String itemName  = getTagText(item, "ITEM_NAME");
            String className = getTagText(item, "CLASS_NAME");
            String chart     = getTagText(item, "CHART");

            PillInfo info = new PillInfo();
            info.itemSeq   = itemSeq;
            info.itemName  = itemName;
            info.className = className;
            info.chart     = chart;

            // 전부 null이면 의미 없음
            if (info.itemName == null && info.className == null && info.chart == null) {
                return null;
            }

            return info;

        } catch (Exception e) {
            Log.e(TAG, "DUR(병용금기) 조회 실패 (itemSeq=" + itemSeq + ")", e);
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * 식약처 알약식별 API
     * getMdcinGrnIdntfcInfoList03
     * - item_seq(=품목기준코드)로 검색
     * - 이름(ITEM_NAME), 분류(CLASS_NAME), 외형(CHART 또는 DRUG_SHAPE)을 가져옴
     */
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

            String itemName  = getTagText(item, "ITEM_NAME");    // 품목명
            String className = getTagText(item, "CLASS_NAME");   // 분류
            String chart     = getTagText(item, "CHART");        // 외형 설명
            if (chart == null) {
                chart = getTagText(item, "DRUG_SHAPE");
            }

            PillInfo info = new PillInfo();
            info.itemSeq   = itemSeq;
            info.itemName  = itemName;
            info.chart     = chart;
            info.className = className;

            if (info.itemName == null && info.className == null && info.chart == null) {
                return null;
            }

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

    /** 알약 정보 모델 (병용금기 + 알약식별 통합용) */
    private static class PillInfo {
        String itemSeq;
        String itemName;   // 이름
        String chart;      // 외형 설명
        String className;  // 분류
    }

    /** TTS로 1순위 알약 이름 + pillContentView 내용 읽어주기 */
    private void speakPillInfo() {
        if (tts == null) {
            Toast.makeText(this, "음성 기능을 사용할 수 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

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
