package com.sookmyung.alarm;

import java.util.ArrayList;
import java.util.List;

public class Alarm {
    public String id;
    public String groupId;
    public String pillName;
    public int hour;
    public int minute;
    public long startDateMillis;
    public long endDateMillis;
    public boolean everyDay;
    public List<Integer> daysOfWeek;

    public Alarm() {
        daysOfWeek = new ArrayList<>();
    }

    public Alarm(String id,
                 String groupId,
                 String pillName,
                 int hour,
                 int minute,
                 long startDateMillis,
                 long endDateMillis,
                 boolean everyDay,
                 List<Integer> daysOfWeek) {
        this.id = id;
        this.groupId = groupId;
        this.pillName = pillName;
        this.hour = hour;
        this.minute = minute;
        this.startDateMillis = startDateMillis;
        this.endDateMillis = endDateMillis;
        this.everyDay = everyDay;
        this.daysOfWeek = (daysOfWeek != null) ? new ArrayList<>(daysOfWeek) : new ArrayList<>();
    }
}
