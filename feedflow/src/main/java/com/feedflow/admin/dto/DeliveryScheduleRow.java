package com.feedflow.admin.dto;

/**
 * 정기 배송일별 집계 (Repository JPQL 전용 DTO).
 * <p>
 * 농장이 가진 {@code recurringDeliveryDay} 를 그대로 묶은 것이다.
 * 배송 전표 테이블을 만들지 않고 <b>계획만</b> 산출한다.
 *
 * @param deliveryDay 매월 며칠 (1~28)
 * @param farmCount   그날 배송하는 농장 수 (거래 중만)
 * @param quantity    그날 나가야 하는 물량 합계 (포대)
 */
public record DeliveryScheduleRow(
        Integer deliveryDay,
        Long farmCount,
        Long quantity
) {

    public int day() {
        return deliveryDay == null ? 0 : deliveryDay;
    }

    public int farms() {
        return farmCount == null ? 0 : farmCount.intValue();
    }

    public int amount() {
        return quantity == null ? 0 : quantity.intValue();
    }

    /** 예: 매월 15일 */
    public String label() {
        return "매월 " + day() + "일";
    }
}
