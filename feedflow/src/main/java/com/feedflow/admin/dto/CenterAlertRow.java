package com.feedflow.admin.dto;

/**
 * 센터별 재고 경보 집계 결과 (Repository JPQL 전용 DTO).
 * <p>
 * 유통기한이 임박·경과한 재고가 어느 센터에 몇 건 있는지 센다.
 *
 * <h3>안전재고는 여기서 세지 않는다</h3>
 * 안전재고({@code Product.safetyStock})는 <b>품목 단위 기준값</b>이고
 * {@code totalStock} 은 전국 합계다. 센터별로 나눌 기준이 없다.
 * "예산 센터의 안전재고 미달" 은 정의되지 않은 개념이므로 만들지 않는다.
 * 대시보드의 안전재고 알림은 전국 기준을 그대로 쓴다.
 *
 * @param centerId      센터 식별자
 * @param centerName    센터명
 * @param expiringCount 유통기한 임박(기준일 이내) 재고 행 수 — 경과분 포함
 * @param expiredCount  이미 경과한 재고 행 수
 * @param expiringQty   임박 재고 수량 합계
 */
public record CenterAlertRow(
        Long centerId,
        String centerName,
        Long expiringCount,
        Long expiredCount,
        Long expiringQty
) {

    public int expiring() {
        return expiringCount == null ? 0 : expiringCount.intValue();
    }

    public int expired() {
        return expiredCount == null ? 0 : expiredCount.intValue();
    }

    public int expiringQuantity() {
        return expiringQty == null ? 0 : expiringQty.intValue();
    }
}
