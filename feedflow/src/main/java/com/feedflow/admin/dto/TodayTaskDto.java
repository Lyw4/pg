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

    /** 유통기한 경고 로트 건수 (임박 + 이미 만료) */
    private final long expiringLotCount;

    /** 이미 유통기한이 지난 로트 건수 (출고 불가) */
    private final long expiredLotCount;

    public boolean isAllClear() {
        return newOrderCount == 0
                && readyToShipCount == 0
                && safetyStockAlertCount == 0
                && expiringLotCount == 0;
    }

    /** 즉시 조치가 필요한 경고가 있는지 */
    public boolean hasAlert() {
        return safetyStockAlertCount > 0 || expiringLotCount > 0;
    }
}
