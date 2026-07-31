package com.feedflow.admin.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 센터별 재고 분포 한 줄 (화면 표기용).
 * <p>
 * 재고 현황 화면 상단에 "전국 재고가 어느 센터에 얼마나 있는지" 를 칩으로 보여준다.
 * 칩을 누르면 해당 센터로 필터가 걸린다.
 *
 * <h3>왜 {@link CenterStockRow} 를 그대로 쓰지 않는가</h3>
 * {@code CenterStockRow} 는 JPQL {@code new} 구문 전용 projection record 다.
 * 이 프로젝트는 집계 record 를 <b>서비스 계층에서 화면용 DTO 로 변환</b>하는 규약을 쓴다
 * ({@code StockSyncRow → StockSyncResultDto}, {@code DailySalesRow → SalesChartDto}).
 * 또한 비중(%) 처럼 <b>다른 행과의 관계에서 나오는 값</b>은 행 하나만 아는
 * projection 이 계산할 수 없다. 전국 합계를 아는 변환 시점에 계산해야 한다.
 */
@Getter
@Builder
public class CenterStockDto {

    private final Long centerId;
    private final String centerName;

    /** 이 센터의 보관 수량 합계 */
    private final int quantity;

    /** 이 센터의 재고 행 수 (로트 × 구역 조합 수) */
    private final int rowCount;

    /** 전국 합계 대비 비중 (%) — 반올림 */
    private final int sharePercent;

    /**
     * 집계 결과를 화면용 목록으로 변환한다.
     * <p>
     * 비중은 <b>넘겨받은 행들의 합</b>을 분모로 쓴다. 품목 필터가 걸려 있으면
     * 그 품목의 전국 합계가 분모가 되므로 "이 품목이 어느 센터에 쏠려 있는지" 가 보인다.
     */
    public static List<CenterStockDto> listOf(List<CenterStockRow> rows) {
        int total = rows.stream().mapToInt(CenterStockRow::totalQuantity).sum();

        return rows.stream()
                .map(row -> CenterStockDto.builder()
                        .centerId(row.centerId())
                        .centerName(row.centerName())
                        .quantity(row.totalQuantity())
                        .rowCount(row.rows())
                        .sharePercent(share(row.totalQuantity(), total))
                        .build())
                .toList();
    }

    /** 전국 합계가 0 이면 나눗셈이 불가능하므로 0% 로 본다 */
    private static int share(int quantity, int total) {
        if (total <= 0) {
            return 0;
        }
        return (int) Math.round(quantity * 100.0 / total);
    }
}
