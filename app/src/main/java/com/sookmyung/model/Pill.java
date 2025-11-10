package com.sookmyung.model;

/**
 * Pill.java
 * 알약의 기본 정보를 저장하는 데이터 모델 클래스
 * (이름, 제조사, 효능, 모양, 색상 등)
 */
public class Pill {
    public String itemSeq;     // 품목일련번호
    public String itemName;    // 제품명
    public String entpName;    // 제조사명
    public String className;   // 효능분류명
    public String drugShape;   // 제형(모양)
    public String color1;      // 색상

    // 기본 생성자
    public Pill() {}

    // 모든 필드를 초기화하는 생성자
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
