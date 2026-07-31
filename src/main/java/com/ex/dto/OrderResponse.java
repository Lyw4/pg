package com.ex.dto;

import com.ex.entity.OrderStatus;

import java.time.LocalDateTime;

public record OrderResponse(
        Long id,
        String orderNumber,
        OrderStatus status,
        int productAmount,
        int deliveryFee,
        int discountAmount,
        int totalAmount,
        LocalDateTime orderedAt
) {
}
