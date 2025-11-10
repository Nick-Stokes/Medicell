package com.sookmyung.list;

/** 알약 정보 데이터 모델 */
public class Pill {
    public String itemSeq;     // 품목일련번호
    public String itemName;    // 제품명
    public String entpName;    // 제조사명
    public String className;   // 효능/분류
    public String drugShape;   // 제형(모양)
    public String color1;      // 색상

    public Pill() {}

    public Pill(String itemSeq, String itemName, String entpName,
                String className, String drugShape, String color1) {
        this.itemSeq = itemSeq;
        this.itemName = itemName;
        this.entpName = entpName;
        this.className = className;
        this.drugShape = drugShape;
        this.color1 = color1;
    }
}

