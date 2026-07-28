package com.feedflow.admin.dto;

import com.feedflow.domain.OrderStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 주문 출고 상세 화면 (출고 전 FEFO 할당 미리보기 포함).
 */
@Getter
@Builder
public class OrderDispatchPreviewDto {

    private final Long orderId;
    private final String customerName;
    private final String customerPhone;
    private final String shippingAddress;
    private final OrderStatus status;
    private final Long totalPrice;
    private final Long discountPrice;
    private final Long finalPrice;
    private final LocalDateTime createdAt;

    private final List<OrderItemPreviewDto> items;

    /** 출고 처리 가능한 상태인지 (결제완료 / 출고대기) */
    private final boolean dispatchable;

    /**
     * 취소 가능한 상태인지.
     * <p>
     * 판단 규칙은 {@code Order} 도메인이 갖고 있고 여기에는 결과만 담는다.
     * DTO 가 상태 규칙을 다시 구현하면 두 곳이 어긋날 수 있다.
     */
    private final boolean cancelable;

    /** 이미 출고되어 취소 시 재고 복구가 필요한 상태인지 */
    private final boolean stockDeducted;

    public String getStatusLabel() {
        return status.getDescription();
    }

    public String getStatusBadgeClass() {
        return status.getBadgeClass();
    }

    public int getTotalQuantity() {
        return items.stream().mapToInt(OrderItemPreviewDto::getQuantity).sum();
    }

    /** 모든 항목의 재고가 충분한지 (하나라도 부족하면 출고 불가) */
    public boolean isAllFulfillable() {
        return items.stream().allMatch(OrderItemPreviewDto::isFulfillable);
    }
}
