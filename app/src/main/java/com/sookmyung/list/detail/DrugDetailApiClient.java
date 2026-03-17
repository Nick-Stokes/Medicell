package com.sookmyung.list.detail;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class DrugDetailApiClient {

    private static final String BASE_URL =
            "https://apis.data.go.kr/1471000/DrbEasyDrugInfoService/";

    private static DrugDetailService service;

    public static DrugDetailService get() {

        if (service == null) {

            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();

            service = retrofit.create(DrugDetailService.class);
        }

        return service;
    }
}