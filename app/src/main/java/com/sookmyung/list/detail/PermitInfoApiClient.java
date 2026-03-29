package com.sookmyung.list.detail;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class PermitInfoApiClient {

    private static final String BASE_URL =
            "https://apis.data.go.kr/1471000/DrugPrdtPrmsnInfoService07/";

    private static PermitInfoService service;

    public static PermitInfoService get() {
        if (service == null) {
            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();

            service = retrofit.create(PermitInfoService.class);
        }
        return service;
    }
}