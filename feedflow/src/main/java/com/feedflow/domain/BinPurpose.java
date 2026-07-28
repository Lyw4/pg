package com.feedflow.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 구역의 용도.
 * <p>
 * 창고에는 재고를 쌓아두는 보관 구역만 있는 것이 아니라, 입고된 물건을 잠시 두는
 * 대기 구역과 출고 직전에 모아두는 구역도 있다. 이들은 <b>도면에는 그려야 하지만
 * 창고 적재율 계산에 넣으면 안 된다.</b> 상시 보관 공간이 아니기 때문이다.
 *
 * @see #isCountedInCapacity()
 */
@Getter
@RequiredArgsConstructor
public enum BinPurpose {

    STORAGE("보관", "bg-primary", true),
    RECEIVING("입고 대기", "bg-info text-dark", false),
    SHIPPING("출고 대기", "bg-warning text-dark", false),
    INSPECTION("검수", "bg-secondary", false);

    /** 화면 표기용 한글 라벨 */
    private final String description;

    /** Bootstrap 5 뱃지 클래스 */
    private final String badgeClass;

    /**
     * 창고 전체 적재율 통계에 포함할 용도인지.
     * <p>
     * 입고/출고 대기 구역은 흐름상 잠시 머무는 곳이라 여기에 물건이 많은 것이
     * "창고가 찼다"는 뜻은 아니다. 통계에 섞으면 적재율이 왜곡된다.
     */
    private final boolean countedInCapacity;
}
