package com.feedflow.admin.dto;

/**
 * 재고 정합성 진단 집계 결과 (Repository JPQL 집계 전용 DTO).
 * <p>
 * 품목별로 <b>비정규화된 {@code Product.totalStock}</b> 과
 * <b>{@code ProductLot.lotQuantity} 합계</b> 를 DB 단에서 한 번에 묶어 온다.
 * 품목 수만큼 합계 쿼리를 반복(N+1)하지 않기 위한 것이다.
 */
public record StockSyncRow(
        Long productId,
        String productCode,
        String productName,
        Boolean active,
        Integer totalStock,
        Long lotQuantitySum
) {

    /** 장부상 재고 (null 이면 0) */
    public int bookStock() {
        return totalStock == null ? 0 : totalStock;
    }

    /** 로트 수량 합계 = 정답으로 간주하는 값 (로트가 없으면 0) */
    public int calculatedStock() {
        return lotQuantitySum == null ? 0 : lotQuantitySum.intValue();
    }

    public boolean isActive() {
        return Boolean.TRUE.equals(active);
    }
}
