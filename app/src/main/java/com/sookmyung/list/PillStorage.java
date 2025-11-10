package com.sookmyung.list;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/** 알약 리스트를 로컬에 저장/조회/삭제 (SharedPreferences) */
public class PillStorage {
    private static final String PREF = "pill_pref";
    private static final String KEY  = "pill_list";

    public static List<Pill> load(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String json = sp.getString(KEY, "[]");
        Type t = new TypeToken<ArrayList<Pill>>(){}.getType();
        return new Gson().fromJson(json, t);
    }

    public static void save(Context ctx, List<Pill> list) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
           .edit().putString(KEY, new Gson().toJson(list)).apply();
    }

    public static void add(Context ctx, Pill p) {
        List<Pill> list = load(ctx);
        for (Pill x : list) {
            if (x.itemSeq != null && x.itemSeq.equals(p.itemSeq)) return; // 중복 방지
        }
        list.add(p);
        save(ctx, list);
    }

    public static void remove(Context ctx, Pill p) {
        List<Pill> list = load(ctx);
        List<Pill> out = new ArrayList<>();
        for (Pill x : list) {
            if (p.itemSeq == null || !p.itemSeq.equals(x.itemSeq)) out.add(x);
        }
        save(ctx, out);
    }
}
