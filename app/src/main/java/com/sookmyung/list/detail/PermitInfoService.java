package com.sookmyung.list.detail;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface PermitInfoService {

    @GET("getDrugPrdtPrmsnDtlInq06")
    Call<PermitInfoEnvelope> getDetailByItemSeq(
            @Query("serviceKey") String key,
            @Query("pageNo") int pageNo,
            @Query("numOfRows") int numOfRows,
            @Query("type") String type,
            @Query("item_seq") String itemSeq,
            @Query("main_item_ingr") Boolean mainItemIngr
    );

    @GET("getDrugPrdtPrmsnDtlInq06")
    Call<PermitInfoEnvelope> getDetailByItemName(
            @Query("serviceKey") String key,
            @Query("pageNo") int pageNo,
            @Query("numOfRows") int numOfRows,
            @Query("type") String type,
            @Query("item_name") String itemName,
            @Query("main_item_ingr") Boolean mainItemIngr
    );

    @GET("getDrugPrdtPrmsnInq07")
    Call<PermitInfoEnvelope> findItemSeqByNameAndCompany(
            @Query("serviceKey") String key,
            @Query("pageNo") int pageNo,
            @Query("numOfRows") int numOfRows,
            @Query("type") String type,
            @Query("item_name") String itemName,
            @Query("entp_name") String entpName
    );
}