package com.feedflow.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 창고(동) 구분.
 * <p>
 * 구역(Bin)은 반드시 하나의 창고에 속한다. 2D 도면은 창고 단위로 한 장씩 그려지고
 * 화면에서는 탭으로 전환한다. 창고를 나누지 않으면 서로 떨어진 건물의 구역이
 * 한 도면에 섞여 실제 위치를 오해하게 된다.
 *
 * <h3>보관 정책</h3>
 * <ul>
 *     <li>{@code WH1} : 상온 보관. 배합사료 주력 창고</li>
 *     <li>{@code WH2} : 저온(COLD) 구역 포함. 영양제 · 온도 민감 품목</li>
 * </ul>
 */
@Getter
@RequiredArgsConstructor
public enum Warehouse {

    WH1("제1창고", "상온 · 배합사료"),
    WH2("제2창고", "저온 · 영양제");

    /** 화면 표기용 한글 라벨 */
    private final String description;

    /** 보관 정책 요약 (탭 부제) */
    private final String note;
}
