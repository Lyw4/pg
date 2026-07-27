package com.feedflow.admin.dto;

import com.feedflow.domain.OrderStatus;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 주문 기반 출고 처리 결과.
 */
@Getter
@Builder
public class OrderDispatchResultDto {

    private final Long orderId;
    private final String customerName;

    /** 처리 후 주문 상태 */
    private final OrderStatus status;

    /** 품목별 출고 결과 */
    private final List<OutboundResultDto> items;

    public int getTotalQuantity() {
        return items.stream().mapToInt(OutboundResultDto::getQuantity).sum();
    }

    public int getTotalLineCount() {
        return items.stream().mapToInt(OutboundResultDto::getUsedLotCount).sum();
    }

    public String getSummaryMessage() {
        return "주문 #" + orderId + " (" + customerName + ") 출고를 완료했습니다."
                + " 품목 " + items.size() + "종 / 총 " + getTotalQuantity() + "개"
                + " / 로트 " + getTotalLineCount() + "건 차감";
    }
}
