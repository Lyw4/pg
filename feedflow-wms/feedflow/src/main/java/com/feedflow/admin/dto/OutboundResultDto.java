package com.feedflow.admin.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 출고 처리 결과 (품목 1건 기준).
 */
@Getter
@Builder
public class OutboundResultDto {

    private final Long productId;
    private final String productCode;
    private final String productName;

    /** 출고 수량 */
    private final int quantity;

    /** 로트별 차감 내역 (FEFO 순서) */
    private final List<AllocationLineDto> lines;

    /** 출고 후 품목 전체 재고 */
    private final int productTotalStock;

    public int getUsedLotCount() {
        return lines.size();
    }

    /** 전량 소진된 로트 수 */
    public long getDepletedLotCount() {
        return lines.stream().filter(AllocationLineDto::isDepleted).count();
    }

    public String getSummaryMessage() {
        return "[" + productCode + "] " + quantity + "개를 "
                + getUsedLotCount() + "개 로트에서 선입선출(FEFO)로 출고했습니다."
                + " (품목 잔여 재고 " + productTotalStock + "개)";
    }
}
