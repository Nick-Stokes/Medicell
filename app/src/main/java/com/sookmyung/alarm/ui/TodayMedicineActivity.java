package com.sookmyung.alarm.ui;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import com.sookmyung.medicell.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

public class TodayMedicineActivity extends AppCompatActivity {

    private List<TimeItem> timeItemList = new ArrayList<>();

    static class TimeItem {
        int hour;
        int minute;
        String pillName;

        TimeItem(int hour, int minute, String pillName) {
            this.hour = hour;
            this.minute = minute;
            this.pillName = pillName;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_today_medicine);

        // 예시 데이터 (기존 코드 유지)
        timeItemList.add(new TimeItem(8, 0, "타이레놀"));
        timeItemList.add(new TimeItem(14, 30, "종합비타민"));
        timeItemList.add(new TimeItem(8, 0, "오메가3"));

        sortAndGroup();
    }

    private void sortAndGroup() {

        // ✅ 기존 comparingInt 제거 → 일반 Comparator 사용
        Collections.sort(timeItemList, new Comparator<TimeItem>() {
            @Override
            public int compare(TimeItem o1, TimeItem o2) {
                if (o1.hour != o2.hour) {
                    return o1.hour - o2.hour;
                }
                return o1.minute - o2.minute;
            }
        });

        // 그룹핑
        HashMap<String, List<TimeItem>> groupedMap = new HashMap<>();

        for (TimeItem item : timeItemList) {
            String key = item.hour + ":" + item.minute;

            // ✅ NPE 방지 처리
            if (!groupedMap.containsKey(key)) {
                groupedMap.put(key, new ArrayList<TimeItem>());
            }

            groupedMap.get(key).add(item);
        }
    }
}