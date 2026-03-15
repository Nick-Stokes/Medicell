package com.sookmyung.list;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class PillSearchCache {

    private static final String PREF = "pill_search_cache";
    private static final long TTL_MS = 24 * 60 * 60 * 1000L; // 1일

    public static List<ApiEnvelope.Item> get(Context ctx, String query) {
        SharedPreferences sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);

        long savedAt = sp.getLong("time_" + query, 0L);
        long now = System.currentTimeMillis();

        if (savedAt == 0L || now - savedAt > TTL_MS) {
            return null;
        }

        String json = sp.getString("data_" + query, null);
        if (json == null) {
            return null;
        }

        Type t = new TypeToken<ArrayList<ApiEnvelope.Item>>() {}.getType();
        List<ApiEnvelope.Item> list = new Gson().fromJson(json, t);
        return list != null ? list : new ArrayList<>();
    }

    public static void put(Context ctx, String query, List<ApiEnvelope.Item> data) {
        SharedPreferences sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        sp.edit()
                .putString("data_" + query, new Gson().toJson(data))
                .putLong("time_" + query, System.currentTimeMillis())
                .apply();
    }

    public static void clear(Context ctx) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .apply();
    }
}
