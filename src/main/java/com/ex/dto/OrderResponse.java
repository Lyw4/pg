package com.ex.dto;

import com.ex.entity.CustomerOrder.OrderStatus;
import com.ex.entity.CustomerOrder;
import com.ex.entity.PaymentMethod;
import com.ex.entity.PaymentProvider;
import com.ex.entity.PaymentStatus;

import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(
        Long id,
        String orderNumber,
        OrderStatus status,
        int productAmount,
        int deliveryFee,
        int discountAmount,
        int totalAmount,
        PaymentMethod paymentMethod,
        PaymentProvider paymentProvider,
        PaymentStatus paymentStatus,
        String paymentToken,
        String receiptUrl,
        String virtualAccountBank,
        String virtualAccountNumber,
        String virtualAccountDueDate,
        LocalDateTime orderedAt,
        List<OrderLineResponse> items
) {
    public static OrderResponse from(CustomerOrder order) {
        return new OrderResponse(
                order.getOrderId(),
                order.getOrderNumber(),
                order.getStatus(),
                order.getProductAmount().intValueExact(),
                order.getDeliveryFee().intValueExact(),
                order.getDiscountPrice().intValueExact(),
                order.getFinalPrice().intValueExact(),
                order.getPaymentMethod(),
                order.getPaymentProvider(),
                order.getPaymentStatus(),
                order.getPaymentCallbackToken(),
                order.getPaymentReceiptUrl(),
                order.getVirtualAccountBank(),
                order.getVirtualAccountNumber(),
                order.getVirtualAccountDueDate(),
                order.getCreatedAt(),
                order.getItems().stream()
                        .map(item -> new OrderLineResponse(
                                item.getProduct().getProductId(),
                                item.getProductName(),
                                item.getQuantity(),
                                item.getOrderPrice().intValueExact(),
                                item.getLineAmount().intValueExact(),
                                item.getProduct().getWeightKg().doubleValue()
                                        * item.getQuantity()))
                        .toList());
    }

    public record OrderLineResponse(
            Long productId,
            String productName,
            int quantity,
            int unitPrice,
            int lineAmount,
            double totalWeightKg) {
    }
}
