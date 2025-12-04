package com.sookmyung.medicell;

import android.os.Bundle;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.ViewGroup;
import android.view.Gravity;

import androidx.appcompat.app.AppCompatActivity;

import com.sookmyung.list.Pill;
import com.sookmyung.list.PillStorage;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;
import java.util.ArrayList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class TabooCheck extends AppCompatActivity {

    private static final String TAG = "TabooCheck";

    private static final String DUR_BASE =
            "https://apis.data.go.kr/1471000/DURPrdlstInfoService03/getUsjntTabooInfoList03";
    private static final String API_KEY =
            "d3ba1256acc493aa40399b9f6c3e345f575156f91bb51050d066a63fdc29bc88";

    private LinearLayout container;   // 스크롤뷰 안의 LinearLayout

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_taboo_check);

        container = findViewById(R.id.container_taboo);

        // 1) 전달된 촬영한 알약
        String mainSeq  = getIntent().getStringExtra("main_item_seq");
        String mainName = getIntent().getStringExtra("main_item_name");

        if (mainSeq != null && mainName != null) {
            fetchTabooFor(mainSeq, mainName, true);
        }

        // 2) 저장된 알약 리스트
        List<Pill> saved = PillStorage.load(this);
        for (Pill p : saved) {
            if (p.itemSeq != null && p.itemName != null) {
                fetchTabooFor(p.itemSeq, p.itemName, false);
            }
        }
    }

    /** XML 태그 파싱 */
    private String tag(Element parent, String tag) {
        try {
            NodeList list = parent.getElementsByTagName(tag);
            if (list == null || list.getLength() == 0) return null;
            if (list.item(0).getFirstChild() == null) return null;
            return list.item(0).getFirstChild().getNodeValue();
        } catch (Exception e) {
            return null;
        }
    }

    /** 병용금기 정보 요청 & UI 출력 */
    private void fetchTabooFor(String itemSeq, String itemName, boolean isMain) {
        new Thread(() -> {
            try {
                StringBuilder urlBuilder = new StringBuilder(DUR_BASE);
                urlBuilder.append("?serviceKey=").append(API_KEY);
                urlBuilder.append("&pageNo=1");
                urlBuilder.append("&numOfRows=200");
                urlBuilder.append("&type=xml");
                urlBuilder.append("&itemSeq=").append(URLEncoder.encode(itemSeq, "UTF-8"));

                URL url = new URL(urlBuilder.toString());
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);
                conn.setRequestMethod("GET");

                int code = conn.getResponseCode();
                if (code != HttpURLConnection.HTTP_OK) {
                    Log.e(TAG, "HTTP 오류: " + code);
                }

                InputStream is = conn.getInputStream();
                DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
                DocumentBuilder db = dbf.newDocumentBuilder();
                Document doc = db.parse(is);
                doc.getDocumentElement().normalize();

                NodeList list = doc.getElementsByTagName("item");

                // ------------------------
                // 1개의 약을 위한 텍스트 묶음
                // ------------------------
                StringBuilder block = new StringBuilder();

                // 촬영 or 저장 구분
                if (isMain) {
                    block.append("[촬영한 알약]\n");
                } else {
                    block.append("[저장된 알약]\n");
                }

                block.append("이름: ").append(itemName).append("\n");
                block.append("품목기준코드: ").append(itemSeq).append("\n");

                if (list == null || list.getLength() == 0) {
                    block.append("병용금기 없음\n");
                } else {
                    block.append("병용금기 있음\n");
                    block.append("같이 복용하면 안 되는 약들:\n");

                    for (int i = 0; i < list.getLength(); i++) {
                        Element el = (Element) list.item(i);

                        String mixName = tag(el, "MIXTURE_ITEM_NAME");
                        String mixIngr = tag(el, "MIXTURE_INGR_KOR_NAME");
                        String reason  = tag(el, "PROHBT_CONTENT");

                        // • 약 이름
                        block.append(" • ")
                                .append(mixName != null ? mixName : "이름 정보 없음");

                        // (성분: ~) 있으면 추가
                        if (mixIngr != null) {
                            block.append(" (성분: ").append(mixIngr).append(")");
                        }
                        block.append("\n");

                        // 금기 사유가 있으면 한 줄 아래에 추가
                        if (reason != null) {
                            block.append("   금기 사유: ").append(reason).append("\n");
                        }
                    }
                }



                // UI 업데이트
                String finalText = block.toString();
                runOnUiThread(() -> addText(finalText));

            } catch (Exception e) {
                Log.e(TAG, "Taboo Fetch Error", e);
                String fallback = "[조회 오류]\n" +
                        "이름: " + itemName + "\n" +
                        "품목기준코드: " + itemSeq + "\n";
                runOnUiThread(() -> addText(fallback));
            }
        }).start();
    }

    /** 텍스트뷰 1개 동적 생성하여 LinearLayout에 추가 */
    private void addText(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(16f);
        tv.setLineSpacing(3f, 1.2f);
        tv.setPadding(20, 30, 20, 30);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.setMargins(0, 10, 0, 10);
        tv.setLayoutParams(lp);

        container.addView(tv);
    }
}
