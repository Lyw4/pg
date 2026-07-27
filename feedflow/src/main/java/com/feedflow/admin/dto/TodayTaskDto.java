package com.feedflow.admin.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 대시보드 '오늘의 할 일' 요약 (STAFF / ADMIN 공통 노출).
 */
@Getter
@Builder
public class TodayTaskDto {

    /** 오늘 접수된 신규 주문 건수 */
    private final long newOrderCount;

    /** 출고 대기 건수 */
    private final long readyToShipCount;

    /** 안전재고 미달 상품 건수 */
    private final long safetyStockAlertCount;

    /** 유통기한 임박 로트 건수 */
    private final long expiringLotCount;

    public boolean isAllClear() {
        return newOrderCount == 0
                && readyToShipCount == 0
                && safetyStockAlertCount == 0
                && expiringLotCount == 0;
    }
}
