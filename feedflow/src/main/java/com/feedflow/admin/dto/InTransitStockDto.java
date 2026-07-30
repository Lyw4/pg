package com.feedflow.admin.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 운송 중(IN_TRANSIT) 가상 구역 잔류 재고 (화면 표기용).
 * <p>
 * 센터 간 이관은 한 트랜잭션에서 출발·도착을 모두 처리하므로 <b>평상시 잔량은 0</b> 이다.
 * 0 이 아니면 이관 트랜잭션이 중간에 깨졌다는 신호다.
 *
 * <h3>왜 정합성 점검 화면에 두는가</h3>
 * 운송 중 구역은 도면에도 나오지 않고 구역 선택 목록에도 없다. 노출을 막아야 하는 구역이지만,
 * <b>그렇다고 관찰할 수 없으면 이상이 생겨도 알아챌 방법이 없다.</b>
 * 정합성 점검은 이미 "장부와 실물이 어긋났는지" 를 보는 화면이므로 여기가 맞는 자리다.
 * <p>
 * 이 잔류 재고는 3계층 불변식을 <b>깨뜨리지 않는다</b>. {@code Inventory} 에 정상적으로
 * 들어 있으므로 로트 합계와 장부는 여전히 일치한다. 문제는 그 재고가
 * <b>어느 센터에서도 팔 수 없는 상태로 갇혀 있다</b>는 점이다.
 * 그래서 기존 점검 항목과 별도로 보여준다.
 */
@Getter
@Builder
public class InTransitStockDto {

    /** 출발 센터 (운송 중 구역은 출발 센터 소속이다) */
    private final Long centerId;
    private final String centerName;

    private final String binCode;

    /** 갇혀 있는 수량 */
    private final int quantity;

    /** 갇혀 있는 재고 행 수 */
    private final int rowCount;

    public static List<InTransitStockDto> listOf(List<InTransitStockRow> rows) {
        return rows.stream()
                .map(row -> InTransitStockDto.builder()
                        .centerId(row.centerId())
                        .centerName(row.centerName())
                        .binCode(row.binCode())
                        .quantity(row.totalQuantity())
                        .rowCount(row.rows())
                        .build())
                .toList();
    }

    /** 전체 잔류 수량 합계 */
    public static int totalQuantityOf(List<InTransitStockDto> rows) {
        return rows.stream().mapToInt(InTransitStockDto::getQuantity).sum();
    }
}
