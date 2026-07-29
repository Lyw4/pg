package com.feedflow.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 품목 구분.
 * <p>
 * 취급 품목을 <b>배합사료</b> 와 <b>영양제(보조제)</b> 두 종류로 한정한다.
 * 그 외 기자재 · 약품 등은 취급하지 않는다.
 * <p>
 * 두 구분 모두 로트 · 유통기한 관리 대상이므로 재고 처리 로직은 동일하다.
 * 다만 영양제는 포장 단위가 작고 유통기한이 길어 화면에서 구분해 보여준다.
 */
@Getter
@RequiredArgsConstructor
public enum ProductType {

    FEED("사료", "bg-primary"),
    SUPPLEMENT("영양제", "bg-info text-dark");

    /** 화면 표기용 한글 라벨 */
    private final String description;

    /** Bootstrap 5 뱃지 클래스 */
    private final String badgeClass;
}
