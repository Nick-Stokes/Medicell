package com.sookmyung.list.detail;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

public class PermitInfoEnvelope {

    @SerializedName("body")
    public Body body;

    public static class Body {
        @SerializedName("items")
        public JsonElement items;
    }

    public static class Item {
        @SerializedName(value = "ITEM_SEQ", alternate = {"item_seq", "itemSeq"})
        public String itemSeq;

        @SerializedName(value = "ITEM_NAME", alternate = {"item_name", "itemName"})
        public String itemName;

        @SerializedName(value = "ENTP_NAME", alternate = {"entp_name", "entpName"})
        public String entpName;

        @SerializedName(value = "MAIN_ITEM_INGR", alternate = {"main_item_ingr", "mainItemIngr"})
        public String mainItemIngr;

        @SerializedName(value = "CHART", alternate = {"chart"})
        public String chart;

        @SerializedName(value = "STORAGE_METHOD", alternate = {"storage_method", "storageMethod"})
        public String storageMethod;

        @SerializedName(value = "EE_DOC_DATA", alternate = {"ee_doc_data", "eeDocData"})
        public String eeDocData;

        @SerializedName(value = "UD_DOC_DATA", alternate = {"ud_doc_data", "udDocData"})
        public String udDocData;

        @SerializedName(value = "NB_DOC_DATA", alternate = {"nb_doc_data", "nbDocData"})
        public String nbDocData;

        @SerializedName(value = "PN_DOC_DATA", alternate = {"pn_doc_data", "pnDocData"})
        public String pnDocData;
    }

    public Item getFirstItem() {
        List<Item> items = getItems();
        return items.isEmpty() ? null : items.get(0);
    }

    public List<Item> getItems() {
        List<Item> result = new ArrayList<>();

        if (body == null || body.items == null || body.items.isJsonNull()) {
            return result;
        }

        Gson gson = new Gson();
        JsonElement itemsElement = body.items;

        if (itemsElement.isJsonObject()) {
            JsonObject itemsObject = itemsElement.getAsJsonObject();

            if (itemsObject.has("item")) {
                JsonElement itemElement = itemsObject.get("item");

                if (itemElement != null && !itemElement.isJsonNull()) {
                    if (itemElement.isJsonArray()) {
                        JsonArray array = itemElement.getAsJsonArray();
                        for (JsonElement e : array) {
                            result.add(gson.fromJson(e, Item.class));
                        }
                    } else if (itemElement.isJsonObject()) {
                        result.add(gson.fromJson(itemElement, Item.class));
                    }
                }
            } else {
                result.add(gson.fromJson(itemsObject, Item.class));
            }

            return result;
        }

        if (itemsElement.isJsonArray()) {
            JsonArray array = itemsElement.getAsJsonArray();
            for (JsonElement e : array) {
                result.add(gson.fromJson(e, Item.class));
            }
        }

        return result;
    }
}