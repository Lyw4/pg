package com.feedflow.admin.dto;

import com.feedflow.domain.MovementType;

/**
 * 센터별 기간 실적 집계 결과 (Repository JPQL 전용 DTO).
 * <p>
 * 유형별로 한 행씩 내려온다. 예를 들어 예산 센터에 입고와 이관 출고가 있었다면
 * {@code (예산, INBOUND, ...)} 와 {@code (예산, TRANSFER_OUT, ...)} 두 행이 나온다.
 * 유형을 컬럼으로 펼치는 것은 화면용 DTO 의 일이다.
 *
 * <h3>어느 센터의 실적으로 세는가</h3>
 * 이력의 <b>{@code binId}(도착 구역)</b> 가 속한 센터를 기준으로 센다.
 * 이관 출고({@code TRANSFER_OUT})는 도착지가 <b>출발 센터의 운송 중 구역</b>이므로
 * 출발 센터 실적으로 잡히고, 이관 입고({@code TRANSFER_IN})는 도착 센터로 잡힌다.
 * 결과적으로 "이 센터에서 나갔다 / 들어왔다" 가 의도대로 갈린다.
 *
 * @param centerId   센터 식별자
 * @param centerName 센터명
 * @param type       이력 유형
 * @param quantity   수량 합계
 * @param rowCount   이력 건수
 */
public record CenterActivityRow(
        Long centerId,
        String centerName,
        MovementType type,
        Long quantity,
        Long rowCount
) {

    public int totalQuantity() {
        return quantity == null ? 0 : quantity.intValue();
    }

    public int count() {
        return rowCount == null ? 0 : rowCount.intValue();
    }
}
