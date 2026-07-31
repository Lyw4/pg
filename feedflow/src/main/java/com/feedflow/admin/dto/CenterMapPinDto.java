package com.feedflow.admin.dto;

import java.util.List;

/**
 * 전국 지도에 찍을 센터 핀 하나 (JSON 응답).
 * <p>
 * 지도는 {@code /api/admin/center-pins} 를 {@code fetch()} 로 가져가 그린다.
 * HTML 렌더링 시 좌표를 넘기지 않는 이유 — 지도 스크립트나 타일 서버가 실패해도
 * 2D 도면 본문은 그대로 보여야 한다. 지도는 <b>보조 정보</b>다.
 *
 * <h3>재고 정보를 함께 담는 이유</h3>
 * 핀을 눌렀을 때 "이 센터에 얼마나 있는지" 를 바로 보여주려면 좌표만으로는 부족하다.
 * 지도와 재고를 따로 요청하면 두 응답을 화면에서 다시 짝지어야 하고,
 * 한쪽만 실패했을 때의 처리도 늘어난다.
 *
 * @param centerId   센터 식별자 (2D 도면 탭 전환에 쓴다)
 * @param centerCode 센터 코드
 * @param centerName 센터명
 * @param region     권역
 * @param note       운영 방향
 * @param latitude   위도
 * @param longitude  경도
 * @param quantity        이 센터에 있는 재고 수량 전체 (대기 구역 · 운송 중 포함)
 * @param storageQuantity 그중 보관 구역에 있는 수량 — 적재율의 분자
 * @param usageRate       보관 구역 적재율 (%)
 */
public record CenterMapPinDto(
        Long centerId,
        String centerCode,
        String centerName,
        String region,
        String note,
        double latitude,
        double longitude,
        int quantity,
        int storageQuantity,
        int usageRate
) {

    /**
     * 보관 구역 밖에 있는 수량 (입고 · 출고 대기 + 운송 중).
     * <p>
     * 적재율에서는 빠지지만 실물은 이 센터에 있다. 팝업에서 "재고 600 인데 적재율은
     * 39%" 처럼 두 숫자가 안 맞아 보일 때 그 차이를 설명해 주는 값이다.
     */
    public int waitingQuantity() {
        return Math.max(quantity - storageQuantity, 0);
    }

    /**
     * 지도 응답 전체.
     *
     * @param pins        좌표가 있는 센터만
     * @param missingCount 좌표가 없어 지도에서 빠진 센터 수.
     *                     화면이 "N곳은 좌표 미등록" 이라고 알려줄 수 있어야 한다.
     *                     조용히 빠지면 핀 수가 센터 수와 달라도 아무도 눈치채지 못한다.
     */
    public record Response(List<CenterMapPinDto> pins, int missingCount) {

        public boolean hasPins() {
            return !pins.isEmpty();
        }
    }
}
