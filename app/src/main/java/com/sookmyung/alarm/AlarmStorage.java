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
    private static final String KEY  = "alarm_list";

    public static List<Alarm> load(Context ctx){
        SharedPreferences sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String json = sp.getString(KEY,"[]");
        Type t = new TypeToken<ArrayList<Alarm>>(){}.getType();
        return new Gson().fromJson(json, t);
    }
    public static void save(Context ctx, List<Alarm> list){
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
           .edit().putString(KEY, new Gson().toJson(list)).apply();
    }
    public static void add(Context ctx, Alarm a){
        List<Alarm> list = load(ctx); list.add(a); save(ctx, list);
    }
    public static void remove(Context ctx, Alarm a){
        List<Alarm> list = load(ctx);
        List<Alarm> out = new ArrayList<>();
        for (Alarm x : list) if (!x.id.equals(a.id)) out.add(x);
        save(ctx, out);
    }
    public static Alarm find(Context ctx, String id){
        for (Alarm a : load(ctx)) if (a.id.equals(id)) return a;
        return null;
    }
}
