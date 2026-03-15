package com.sookmyung.list;

/** 알약 정보 데이터 모델 */
public class Pill {
    public String itemSeq;       // 품목기준코드
    public String itemName;      // 제품명
    public String entpName;      // 제조사명
    public String className;     // 효능/분류
    public String drugShape;     // 제형(모양)
    public String color1;        // 색상
    public String itemImage;     // 공식 이미지 URL

    // 상세정보용
    public String ingredient;    // 성분
    public String efficacy;      // 효능
    public String usage;         // 복용방법
    public String caution;       // 주의사항

    public Pill() {}

    public Pill(String itemSeq,
                String itemName,
                String entpName,
                String className,
                String drugShape,
                String color1,
                String itemImage) {
        this.itemSeq = itemSeq;
        this.itemName = itemName;
        this.entpName = entpName;
        this.className = className;
        this.drugShape = drugShape;
        this.color1 = color1;
        this.itemImage = itemImage;
    }
}