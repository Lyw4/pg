package com.feedflow.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 주문 상태.
 * 대시보드의 '오늘의 할 일' 집계 기준으로 사용된다.
 */
@Getter
@RequiredArgsConstructor
public enum OrderStatus {

    PAID("결제완료", "bg-success"),
    READY("출고대기", "bg-warning text-dark"),
    SHIPPED("출고완료", "bg-info text-dark"),
    DELIVERED("배송완료", "bg-secondary"),
    CANCELED("주문취소", "bg-dark");

    private final String description;
    private final String badgeClass;
}
