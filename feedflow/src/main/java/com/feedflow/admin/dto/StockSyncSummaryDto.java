package com.feedflow.admin.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 재고 정합성 점검 요약 (화면 상단 카드용).
 * <p>
 * 품목별 진단 결과를 집계해 "지금 몇 건이 어긋나 있는지" 를 한눈에 보여준다.
 */
@Getter
@Builder
public class StockSyncSummaryDto {

    /** 점검 대상 품목 수 */
    private final int totalProducts;

    /** 장부와 로트 합계가 어긋난 품목 수 */
    private final int mismatchedCount;

    /** 장부 재고가 실제보다 많은 품목 수 (없는 재고를 판매할 위험) */
    private final int overstatedCount;

    /** 장부 재고가 실제보다 적은 품목 수 (판매 가능 재고 누락) */
    private final int understatedCount;

    /** 과다 계상된 총 수량 */
    private final int overstatedQuantity;

    /** 과소 계상된 총 수량 (절댓값) */
    private final int understatedQuantity;

    public static StockSyncSummaryDto of(List<StockSyncResultDto> rows) {
        int overstatedCount = 0;
        int understatedCount = 0;
        int overstatedQuantity = 0;
        int understatedQuantity = 0;

        for (StockSyncResultDto row : rows) {
            int difference = row.getDifference();
            if (difference > 0) {
                overstatedCount++;
                overstatedQuantity += difference;
            } else if (difference < 0) {
                understatedCount++;
                understatedQuantity += -difference;
            }
        }

        return StockSyncSummaryDto.builder()
                .totalProducts(rows.size())
                .mismatchedCount(overstatedCount + understatedCount)
                .overstatedCount(overstatedCount)
                .understatedCount(understatedCount)
                .overstatedQuantity(overstatedQuantity)
                .understatedQuantity(understatedQuantity)
                .build();
    }

    /** 전부 정합한 상태인지 */
    public boolean isAllClean() {
        return mismatchedCount == 0;
    }
}
