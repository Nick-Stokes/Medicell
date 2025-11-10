package com.sookmyung.list;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

/** 공공데이터포털 의약품 낱알식별 검색 API */
public interface ApiService {

    @GET("getMdcinGrnIdntfcInfoList03")
    Call<ApiEnvelope> searchPills(
            @Query("serviceKey") String key,
            @Query("pageNo") int pageNo,
            @Query("numOfRows") int rows,
            @Query("type") String type,
            @Query("item_name") String itemName  // 부분 검색: "게보"
    );
}
