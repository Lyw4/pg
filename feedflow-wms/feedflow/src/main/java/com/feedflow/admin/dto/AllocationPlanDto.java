package com.feedflow.admin.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 출고 전 FEFO 할당 계획(미리보기).
 * 재고를 변경하지 않고 "어느 로트에서 몇 개가 빠질지" 만 계산한 결과이다.
 */
@Getter
@Builder
public class AllocationPlanDto {

    private final Long productId;
    private final String productCode;
    private final String productName;

    /** 요청 수량 */
    private final int requestedQuantity;

    /** 출고 가능 수량 (유통기한 지난 로트 / 사용중지 구역 제외) */
    private final int availableQuantity;

    /** 실제 할당 가능한 수량 */
    private final int allocatedQuantity;

    private final List<AllocationLineDto> lines;

    /** 요청 수량을 모두 충족할 수 있는지 */
    public boolean isFulfillable() {
        return allocatedQuantity >= requestedQuantity;
    }

    /** 부족 수량 */
    public int getShortage() {
        return Math.max(requestedQuantity - allocatedQuantity, 0);
    }

    /** 사용되는 로트 수 */
    public int getUsedLotCount() {
        return lines.size();
    }
}
