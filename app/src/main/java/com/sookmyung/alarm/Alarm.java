package com.sookmyung.alarm;

public class Alarm {
    public String id;       // 고유 ID (알람 요청코드)
    public String pillName; // 알림 대상 약 이름
    public int hour;        // 0~23
    public int minute;      // 0~59

    public Alarm() {}
    public Alarm(String id, String pillName, int hour, int minute) {
        this.id = id; this.pillName = pillName; this.hour = hour; this.minute = minute;
    }
}
