package com.feedflow.admin.dto;

import java.time.LocalDate;

/**
 * 일별 매출 집계 결과 (Repository JPQL 집계 전용 DTO).
 * 자바단 반복문 합산이 아니라 DB GROUP BY 결과를 그대로 담는다.
 */
public record DailySalesRow(
        Integer year,
        Integer month,
        Integer day,
        Long totalAmount
) {

    public LocalDate saleDate() {
        return LocalDate.of(year, month, day);
    }

    public long amount() {
        return totalAmount == null ? 0L : totalAmount;
    }
}
