package com.feedflow.admin.dto;

/**
 * 센터별 보관 수량 집계 결과 (Repository JPQL 전용 DTO).
 * <p>
 * 재고 현황 화면에서 "전국 재고가 어느 센터에 얼마나 있는지" 를 한 줄로 보여줄 때 쓴다.
 * 목록을 자바에서 그룹핑하지 않고 별도 집계 쿼리로 받는 이유는,
 * 목록에는 이미 센터 필터가 적용되어 있어 <b>선택한 센터 하나만</b> 남기 때문이다.
 * 분포는 필터와 무관해야 "다른 센터에도 재고가 있다" 는 사실을 알 수 있다.
 *
 * @param centerId  센터 식별자
 * @param centerName 센터명
 * @param quantity  보관 수량 합계 (재고가 없으면 이 행 자체가 나오지 않는다)
 * @param rowCount  재고 행 수 (로트 × 구역 조합 수)
 */
public record CenterStockRow(
        Long centerId,
        String centerName,
        Long quantity,
        Long rowCount
) {

    public int totalQuantity() {
        return quantity == null ? 0 : quantity.intValue();
    }

    public int rows() {
        return rowCount == null ? 0 : rowCount.intValue();
    }
}
