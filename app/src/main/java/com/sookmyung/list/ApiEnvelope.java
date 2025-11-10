package com.sookmyung.list;

import com.google.gson.annotations.SerializedName;
import java.util.List;

/** API 응답(JSON) 매핑용 DTO */
public class ApiEnvelope {
    @SerializedName("body") public Body body;

    public static class Body {
        @SerializedName("items") public List<Item> items;
    }

    public static class Item {
        @SerializedName("ITEM_SEQ")   public String itemSeq;
        @SerializedName("ITEM_NAME")  public String itemName;
        @SerializedName("ENTP_NAME")  public String entpName;
        @SerializedName("CLASS_NAME") public String className;
        @SerializedName("DRUG_SHAPE") public String drugShape;
        @SerializedName("COLOR_CLASS1") public String color1;
    }
}
