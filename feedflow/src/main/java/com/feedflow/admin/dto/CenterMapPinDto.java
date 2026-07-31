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
 * @param quantity   보관 수량
 * @param usageRate  적재율 (%)
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
        int usageRate
) {

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
