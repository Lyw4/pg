package com.feedflow.admin.dto;

import java.time.LocalDate;

/**
 * 창고 2D 맵 집계 결과 (Repository JPQL 전용 DTO).
 * <p>
 * 구역 하나당 한 행이 내려온다. 재고가 전혀 없는 구역도 포함되며 이때 적재량은 0 이다.
 * 구역 수만큼 재고 합계 쿼리를 반복(N+1)하지 않기 위해 {@code left join} + {@code group by} 로
 * DB 단에서 집계한다.
 *
 * @param loadedQuantity      적재 수량 합계 (재고 없으면 0)
 * @param lotCount            보관 중인 서로 다른 로트 수
 * @param productCount        보관 중인 서로 다른 품목 수
 * @param earliestExpiration  가장 먼저 만료되는 로트의 유통기한 (재고 없으면 null)
 */
public record WarehouseMapRow(
        Long binId,
        String binCode,
        String zone,
        String rack,
        Integer binLevel,
        Integer maxCapacity,
        Boolean active,
        Long loadedQuantity,
        Long lotCount,
        Long productCount,
        LocalDate earliestExpiration
) {

    public int loaded() {
        return loadedQuantity == null ? 0 : loadedQuantity.intValue();
    }

    public int capacity() {
        return maxCapacity == null ? 0 : maxCapacity;
    }

    public int lots() {
        return lotCount == null ? 0 : lotCount.intValue();
    }

    public int products() {
        return productCount == null ? 0 : productCount.intValue();
    }

    public boolean isActive() {
        return Boolean.TRUE.equals(active);
    }
}
