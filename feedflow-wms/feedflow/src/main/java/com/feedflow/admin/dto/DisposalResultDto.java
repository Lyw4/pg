package com.feedflow.admin.dto;

import com.feedflow.domain.DisposalReason;
import lombok.Builder;
import lombok.Getter;

/**
 * 재고 폐기 처리 결과.
 */
@Getter
@Builder
public class DisposalResultDto {

    private final String productCode;
    private final String productName;
    private final String lotNo;
    private final String binCode;

    private final DisposalReason reason;

    /** 폐기 수량 */
    private final int quantity;

    /** 폐기 후 구역 보관 수량 */
    private final int binQuantityAfter;

    /** 폐기 후 로트 잔여 수량 */
    private final int lotQuantityAfter;

    /** 폐기 후 품목 전체 재고 */
    private final int productTotalStock;

    /** 해당 구역 재고가 전량 폐기되었는지 */
    public boolean isDepleted() {
        return binQuantityAfter == 0;
    }

    public String getReasonLabel() {
        return reason.getDescription();
    }

    public String getSummaryMessage() {
        return "[" + lotNo + "] 로트를 " + binCode + " 구역에서 " + quantity + "개 폐기했습니다."
                + " (사유: " + reason.getDescription()
                + ", 구역 잔여 " + binQuantityAfter
                + ", 품목 전체 재고 " + productTotalStock + ")";
    }
}
