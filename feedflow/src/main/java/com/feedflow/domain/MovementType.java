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
    DISPOSAL("폐기", "bg-danger", -1),
    MOVE("구역이동", "bg-info text-dark", 0),
    ADJUST("재고조정", "bg-secondary", 0);

    private final String description;
    private final String badgeClass;

    /** 재고 증감 방향 (+1 증가, -1 감소, 0 이동/조정) */
    private final int sign;
}
