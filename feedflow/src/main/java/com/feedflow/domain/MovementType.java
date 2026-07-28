package com.feedflow.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 재고 이동 유형.
 */
@Getter
@RequiredArgsConstructor
public enum MovementType {

    INBOUND("입고", "bg-primary", 1),
    OUTBOUND("출고", "bg-warning text-dark", -1),

    /**
     * 출고 취소에 따른 재고 복구.
     * <p>
     * 재고가 늘어나는 점은 입고와 같지만 <b>입고와 구분해서 남긴다.</b>
     * 입고로 기록하면 실제로 들어오지 않은 물량이 입고 실적에 섞여
     * 매입 집계와 이력 추적이 왜곡된다.
     */
    CANCEL("출고취소", "bg-dark", 1),

    DISPOSAL("폐기", "bg-danger", -1),
    MOVE("구역이동", "bg-info text-dark", 0),
    ADJUST("재고조정", "bg-secondary", 0);

    private final String description;
    private final String badgeClass;

    /** 재고 증감 방향 (+1 증가, -1 감소, 0 이동/조정) */
    private final int sign;
}
