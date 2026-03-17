package com.sookmyung.list.detail;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/** e약은요 상세 API 응답 매핑용 DTO */
public class DrugDetailEnvelope {

    @SerializedName("body")
    public Body body;

    public static class Body {

        @SerializedName("items")
        public List<Item> items;
    }

    public static class Item {

        @SerializedName("itemSeq")
        public String itemSeq;

        @SerializedName("itemName")
        public String itemName;

        @SerializedName("entpName")
        public String entpName;

        @SerializedName("itemImage")
        public String itemImage;

        @SerializedName("efcyQesitm")
        public String efcyQesitm;

        @SerializedName("useMethodQesitm")
        public String useMethodQesitm;

        @SerializedName("atpnQesitm")
        public String atpnQesitm;

        @SerializedName("atpnWarnQesitm")
        public String atpnWarnQesitm;

        @SerializedName("intrcQesitm")
        public String intrcQesitm;

        @SerializedName("seQesitm")
        public String seQesitm;

        @SerializedName("depositMethodQesitm")
        public String depositMethodQesitm;
    }
}