package com.sookmyung.list;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class PillStorage {
    private static final String PREF = "pill_pref";
    private static final String KEY = "pill_list";

    public static List<Pill> load(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String json = sp.getString(KEY, "[]");
        Type t = new TypeToken<ArrayList<Pill>>() {}.getType();
        List<Pill> list = new Gson().fromJson(json, t);
        return list != null ? list : new ArrayList<>();
    }

    public static void save(Context ctx, List<Pill> list) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY, new Gson().toJson(list))
                .apply();
    }

    public static void add(Context ctx, Pill p) {
        List<Pill> list = load(ctx);
        list.add(p);
        save(ctx, list);
    }

    public static void remove(Context ctx, Pill p) {
        List<Pill> list = load(ctx);
        List<Pill> out = new ArrayList<>();

        for (Pill x : list) {
            if (x.itemSeq == null || !x.itemSeq.equals(p.itemSeq)) {
                out.add(x);
            }
        }

        save(ctx, out);
    }

    public static void upsert(Context ctx, Pill target) {
        List<Pill> list = load(ctx);
        boolean updated = false;

        for (int i = 0; i < list.size(); i++) {
            Pill x = list.get(i);
            if (x.itemSeq != null && x.itemSeq.equals(target.itemSeq)) {
                list.set(i, target);
                updated = true;
                break;
            }
        }

        if (!updated) {
            list.add(target);
        }

        save(ctx, list);
    }

    public static Pill find(Context ctx, String itemSeq) {
        for (Pill p : load(ctx)) {
            if (p.itemSeq != null && p.itemSeq.equals(itemSeq)) {
                return p;
            }
        }
        return null;
    }
}