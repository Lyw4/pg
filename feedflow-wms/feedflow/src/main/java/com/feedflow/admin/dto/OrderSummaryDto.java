package com.feedflow.admin.dto;

import com.feedflow.domain.Order;
import com.feedflow.domain.OrderStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 출고 대상 주문 목록 행.
 */
@Getter
@Builder
public class OrderSummaryDto {

    private final Long orderId;
    private final String customerName;
    private final String customerPhone;
    private final String shippingAddress;
    private final OrderStatus status;
    private final Long finalPrice;
    private final LocalDateTime createdAt;

    /**
     * @param order user 가 fetch join 으로 초기화된 상태여야 한다.
     */
    public static OrderSummaryDto from(Order order) {
        return OrderSummaryDto.builder()
                .orderId(order.getOrderId())
                .customerName(order.getUser().getName())
                .customerPhone(order.getUser().getPhone())
                .shippingAddress(order.getShippingAddress())
                .status(order.getStatus())
                .finalPrice(order.getFinalPrice())
                .createdAt(order.getCreatedAt())
                .build();
    }

    public String getStatusLabel() {
        return status.getDescription();
    }

    public String getStatusBadgeClass() {
        return status.getBadgeClass();
    }
}
