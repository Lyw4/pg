package com.feedflow.admin.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 재고 정합성 재계산 결과.
 * <p>
 * {@code Product.totalStock} 은 조회 성능을 위해 비정규화한 값이므로
 * 로트 수량 합계와 어긋날 수 있다. 이 DTO 는 보정 전/후 값을 함께 담아
 * 관리자 화면에서 무엇이 얼마나 바뀌었는지 보여줄 수 있게 한다.
 */
@Getter
@Builder
public class StockSyncResultDto {

    private final Long productId;
    private final String productCode;
    private final String productName;

    /** 보정 전 totalStock */
    private final int previousStock;

    /** 로트 수량 합계 (정답으로 간주하는 값) */
    private final int calculatedStock;

    /** 실제로 값이 바뀌었는지 */
    private final boolean adjusted;

    /** 차이 (양수면 totalStock 이 과다 계상되어 있었음) */
    public int getDifference() {
        return previousStock - calculatedStock;
    }

    public String getSummaryMessage() {
        if (!adjusted) {
            return "[" + productCode + "] 재고가 정확합니다. (" + calculatedStock + ")";
        }
        return "[" + productCode + "] 재고를 보정했습니다. "
                + previousStock + " → " + calculatedStock
                + " (차이 " + getDifference() + ")";
    }
}
