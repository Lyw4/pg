package com.feedflow.admin.dto;

import com.feedflow.domain.OrderStatus;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 출고(주문) 취소 처리 결과.
 */
@Getter
@Builder
public class OrderCancelResultDto {

    private final Long orderId;
    private final String customerName;

    /** 취소 전 상태 (재고 복구가 있었는지 판단 근거) */
    private final OrderStatus previousStatus;

    /** 취소 후 상태 (항상 CANCELED) */
    private final OrderStatus status;

    /** 되돌린 재고 내역 (출고 전이었다면 비어 있다) */
    private final List<RestorationLineDto> restoredLines;

    /** 재고를 실제로 되돌렸는지 (출고 완료 주문이었는지) */
    private final boolean stockRestored;

    public int getRestoredQuantity() {
        return restoredLines.stream()
                .mapToInt(RestorationLineDto::getRestoredQuantity)
                .sum();
    }

    public int getRestoredLineCount() {
        return restoredLines.size();
    }

    public String getSummaryMessage() {
        if (!stockRestored) {
            return "주문 #" + orderId + " (" + customerName + ") 을 취소했습니다."
                    + " 출고 전이라 되돌릴 재고가 없습니다.";
        }
        return "주문 #" + orderId + " (" + customerName + ") 출고를 취소했습니다."
                + " 로트 " + getRestoredLineCount() + "건 / 총 " + getRestoredQuantity() + "개를 복구했습니다.";
    }
}
