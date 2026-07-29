package com.feedflow.admin.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 주문 항목 + 해당 항목의 FEFO 할당 계획.
 */
@Getter
@Builder
public class OrderItemPreviewDto {

    private final Long orderItemId;
    private final Long productId;
    private final String productCode;
    private final String productName;
    private final String animalType;

    private final int quantity;
    private final Long orderPrice;

    /** 출고 전 할당 계획 (재고 변경 없음) */
    private final AllocationPlanDto plan;

    public boolean isFulfillable() {
        return plan != null && plan.isFulfillable();
    }
}
