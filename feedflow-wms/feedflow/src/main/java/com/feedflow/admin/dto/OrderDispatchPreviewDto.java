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

    /* ------------------------------------------------------------------
     * 취소 정보 (취소된 주문에만 값이 있다)
     * ------------------------------------------------------------------ */

    private final boolean canceled;

    /** 취소 사유 (미입력 시 null) */
    private final String cancelReason;

    private final LocalDateTime canceledAt;

    /** 취소 처리자 이름 (기록이 없으면 null) */
    private final String canceledByName;

    /**
     * 취소로 되돌린 총 수량.
     * <p>
     * 주문 상태가 아니라 <b>실제 {@code CANCEL} 재고 이력</b>에서 집계한 값이다.
     * 취소된 뒤에는 상태가 CANCELED 로 덮여 "출고 전이었는지" 를 상태로는 알 수 없고,
     * 복구 이력의 존재 여부만이 유일한 근거다.
     */
    private final int restoredQuantity;

    /** 취소로 되돌린 이력 건수 (로트 × 구역 단위) */
    private final int restoredLineCount;

    /** 취소 사유가 입력되었는지 */
    public boolean isCancelReasonPresent() {
        return cancelReason != null && !cancelReason.isBlank();
    }

    /** 취소 처리자가 기록되어 있는지 */
    public boolean isCanceledByPresent() {
        return canceledByName != null && !canceledByName.isBlank();
    }

    /**
     * 취소 시 재고가 실제로 복구되었는지.
     * <p>
     * false 면 출고 전 취소이므로 되돌릴 재고가 없었다는 뜻이다.
     */
    public boolean isStockRestored() {
        return restoredLineCount > 0;
    }

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
