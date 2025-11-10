package com.sookmyung.list;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/** Retrofit 클라이언트 생성 */
public class ApiClient {
    private static final String BASE =
        "https://apis.data.go.kr/1471000/MdcinGrnIdntfcInfoService03/";

    public static ApiService get() {
        return new Retrofit.Builder()
                .baseUrl(BASE)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(ApiService.class);
    }
}
