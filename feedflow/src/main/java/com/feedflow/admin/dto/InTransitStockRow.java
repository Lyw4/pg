package com.feedflow.admin.dto;

/**
 * 운송 중(IN_TRANSIT) 가상 구역에 남아 있는 재고 집계 (Repository JPQL 전용 DTO).
 * <p>
 * 센터 간 이관은 한 트랜잭션에서 출발·도착을 모두 처리하므로 <b>평상시 잔량은 0</b> 이다.
 * 0 이 아니면 다음 중 하나다.
 * <ul>
 *     <li>이관 트랜잭션이 중간에 깨졌다 (버그)</li>
 *     <li>도착 처리를 분리한 뒤(P3b) 아직 받지 않은 물량이 있다 (정상)</li>
 * </ul>
 * 가상 구역을 관찰할 수 없으면 앞의 경우를 알아챌 방법이 없다.
 * 재고 정합성 점검 화면에서 이 집계를 보여준다.
 *
 * @param centerId   출발 센터 (운송 중 구역은 출발 센터 소속이다)
 * @param centerName 출발 센터명
 * @param binCode    가상 구역 코드 (예: {@code TRANSIT-WH1})
 * @param quantity   잔류 수량 합계
 * @param rowCount   잔류 재고 행 수
 */
public record InTransitStockRow(
        Long centerId,
        String centerName,
        String binCode,
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
