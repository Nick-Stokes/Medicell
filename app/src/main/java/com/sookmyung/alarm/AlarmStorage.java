package com.sookmyung.alarm;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class AlarmStorage {
    private static final String PREF = "alarm_pref";
    private static final String KEY = "alarm_list";

    public static List<Alarm> load(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String json = sp.getString(KEY, "[]");
        Type t = new TypeToken<ArrayList<Alarm>>() {}.getType();
        List<Alarm> list = new Gson().fromJson(json, t);
        return list != null ? list : new ArrayList<>();
    }

    public static void save(Context ctx, List<Alarm> list) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY, new Gson().toJson(list))
                .apply();
    }

    public static void add(Context ctx, Alarm alarm) {
        List<Alarm> list = load(ctx);
        list.add(alarm);
        save(ctx, list);
    }

    public static void addAll(Context ctx, List<Alarm> alarms) {
        List<Alarm> list = load(ctx);
        list.addAll(alarms);
        save(ctx, list);
    }

    public static Alarm find(Context ctx, String id) {
        for (Alarm alarm : load(ctx)) {
            if (alarm.id != null && alarm.id.equals(id)) {
                return alarm;
            }
        }
        return null;
    }

    public static List<Alarm> findByGroup(Context ctx, String groupId) {
        List<Alarm> result = new ArrayList<>();
        if (groupId == null) {
            return result;
        }
        for (Alarm alarm : load(ctx)) {
            if (groupId.equals(alarm.groupId)) {
                result.add(alarm);
            }
        }
        return result;
    }

    public static void removeByGroup(Context ctx, String groupId) {
        List<Alarm> out = new ArrayList<>();
        for (Alarm alarm : load(ctx)) {
            if (groupId == null || !groupId.equals(alarm.groupId)) {
                out.add(alarm);
            }
        }
        save(ctx, out);
    }

    public static void replaceGroup(Context ctx, String groupId, List<Alarm> alarms) {
        List<Alarm> out = new ArrayList<>();
        for (Alarm alarm : load(ctx)) {
            if (groupId == null || !groupId.equals(alarm.groupId)) {
                out.add(alarm);
            }
        }
        if (alarms != null) {
            out.addAll(alarms);
        }
        save(ctx, out);
    }
}
