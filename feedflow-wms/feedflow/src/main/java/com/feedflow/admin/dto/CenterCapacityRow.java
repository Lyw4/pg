package com.feedflow.admin.dto;

/**
 * 센터별 보관 구역 수용량 집계 결과 (Repository JPQL 전용 DTO).
 * <p>
 * 적재율의 분모다. <b>적재율 통계에 포함되는 구역만</b> 센다 —
 * 사용 중지 구역 · 입출고 대기 구역 · 운송 중 가상 구역은 빠진다.
 * 2D 도면의 창고 요약과 같은 기준이어야 같은 센터의 적재율이 화면마다 같게 나온다.
 *
 * @param centerId 센터 식별자
 * @param capacity 수용량 합계
 * @param binCount 보관 구역 수
 */
public record CenterCapacityRow(
        Long centerId,
        Long capacity,
        Long binCount
) {

    public int totalCapacity() {
        return capacity == null ? 0 : capacity.intValue();
    }

    public int bins() {
        return binCount == null ? 0 : binCount.intValue();
    }
}
