package com.feedflow.admin.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 매출 통계 요약 (ADMIN 전용 노출).
 */
@Getter
@Builder
public class SalesSummaryDto {

    /** 오늘 총 매출액 */
    private final long todaySales;

    /** 이번 달 총 매출액 */
    private final long monthSales;

    /** 오늘 주문 건수 */
    private final long todayOrderCount;

    /** 오늘 평균 주문 금액 */
    public long getTodayAverageOrderAmount() {
        return todayOrderCount == 0 ? 0L : todaySales / todayOrderCount;
    }
}
