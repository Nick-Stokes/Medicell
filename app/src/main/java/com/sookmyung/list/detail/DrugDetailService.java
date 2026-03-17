package com.sookmyung.list.detail;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface DrugDetailService {

    @GET("getDrbEasyDrugList")
    Call<DrugDetailEnvelope> getDetail(
            @Query("serviceKey") String key,
            @Query("pageNo") int pageNo,
            @Query("numOfRows") int numOfRows,
            @Query("type") String type,
            @Query("itemSeq") String itemSeq
    );
}