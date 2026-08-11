package com.ex.dto;

import com.ex.entity.CustomerOrder;
import com.ex.entity.Delivery;
import com.ex.entity.DeliveryStatusHistory;
import com.ex.entity.Shipment;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 회원 본인이 조회할 수 있는 주문 상세 정보입니다.
 * 주문 요약 정보와 배송지, 출고/배송 정보, 배송 상태 이력을 함께 제공합니다.
 */
public record OrderDetailResponse(
        OrderResponse order,
        PaymentInfo payment,
        Recipient recipient,
        Fulfillment fulfillment,
        ShipmentInfo shipment,
        DeliveryInfo delivery,
        List<TimelineEvent> timeline
) {

    public static OrderDetailResponse from(
            CustomerOrder order,
            Shipment shipment,
            Delivery delivery,
            List<DeliveryStatusHistory> histories) {
        List<TimelineEvent> timeline = new ArrayList<>();
        add(timeline, "ORDERED", order.getCreatedAt(), true, "주문이 접수되었습니다.");

        if (order.getPaymentStatus() != null
                && order.getPaymentStatus().name().equals("DONE")) {
            add(timeline, "PAYMENT_CONFIRMED",
                    order.getPaymentApprovedAt() == null
                            ? order.getCreatedAt()
                            : order.getPaymentApprovedAt(),
                    true, "결제가 확인되었습니다.");
        }
        if (order.getStatus() == CustomerOrder.OrderStatus.PREPARING
                || order.getStatus() == CustomerOrder.OrderStatus.SHIPPING
                || order.getStatus() == CustomerOrder.OrderStatus.DELIVERED) {
            add(timeline, "PREPARING", order.getUpdatedAt(), true,
                    "상품을 준비하고 있습니다.");
        }
        if (shipment != null && shipment.getShippedAt() != null) {
            add(timeline, "SHIPPED", shipment.getShippedAt(), true,
                    "상품이 출고되었습니다.");
        }
        if (histories != null) {
            histories.forEach(history -> add(
                    timeline,
                    "DELIVERY_" + history.getChangedStatus().name(),
                    history.getChangedAt(),
                    true,
                    history.getNote()));
        }
        if (order.getStatus() == CustomerOrder.OrderStatus.CANCELLED) {
            add(timeline, "CANCELLED", order.getCancelledAt(), true,
                    order.getCancellationReason());
        }
        // DB 저장 시각은 상태 변경 시점과 다를 수 있으므로 화면에서는
        // 배송 업무의 의미상 순서를 우선해 정렬합니다.
        timeline.sort(Comparator
                .comparingInt((TimelineEvent event) -> timelineRank(event.code()))
                .thenComparing(
                        TimelineEvent::occurredAt,
                        Comparator.nullsLast(Comparator.naturalOrder())));

        return new OrderDetailResponse(
                OrderResponse.from(order),
                new PaymentInfo(
                        order.getPaymentMethod() == null ? null : order.getPaymentMethod().name(),
                        order.getPaymentProvider() == null ? null : order.getPaymentProvider().name(),
                        order.getPaymentStatus() == null ? null : order.getPaymentStatus().name(),
                        order.getProviderTransactionId(),
                        order.getPaymentApprovedAt(),
                        order.getPaymentReceiptUrl(),
                        order.getVirtualAccountBank(),
                        order.getVirtualAccountNumber(),
                        order.getVirtualAccountDueDate()),
                new Recipient(
                        firstNonBlank(order.getRecipientName(), order.getCustomerName()),
                        firstNonBlank(order.getRecipientPhone(), order.getPhone()),
                        order.getPostalCode(),
                        order.getRoadAddress(),
                        order.getJibunAddress(),
                        order.getDetailAddress(),
                        order.getUnloadingLocation(),
                        order.getDeliveryRequest()),
                order.getFulfillmentWarehouse() == null
                        ? null
                        : new Fulfillment(
                                order.getFulfillmentWarehouse().getCode(),
                                order.getFulfillmentWarehouse().getName(),
                                order.getFulfillmentWarehouse().getAddress(),
                                order.getFulfillmentDistanceKm(),
                                order.getFulfillmentAssignmentBasis()),
                shipment == null ? null : ShipmentInfo.from(shipment),
                delivery == null ? null : DeliveryInfo.from(delivery),
                List.copyOf(timeline));
    }

    private static void add(
            List<TimelineEvent> timeline,
            String code,
            LocalDateTime occurredAt,
            boolean completed,
            String note) {
        if (occurredAt != null) {
            timeline.add(new TimelineEvent(code, occurredAt, completed, note));
        }
    }

    private static String firstNonBlank(String first, String fallback) {
        return first == null || first.isBlank() ? fallback : first;
    }

    private static int timelineRank(String code) {
        return switch (code) {
            case "ORDERED" -> 10;
            case "PAYMENT_CONFIRMED" -> 20;
            case "PREPARING" -> 30;
            case "SHIPPED" -> 40;
            case "DELIVERY_READY" -> 50;
            case "DELIVERY_PICKED_UP" -> 60;
            case "DELIVERY_IN_TRANSIT" -> 70;
            case "DELIVERY_DELIVERED" -> 80;
            case "CANCELLED", "DELIVERY_CANCELLED" -> 90;
            default -> 100;
        };
    }

    public record Recipient(
            String name,
            String phone,
            String postalCode,
            String roadAddress,
            String jibunAddress,
            String detailAddress,
            String unloadingLocation,
            String deliveryRequest) {
    }

    public record PaymentInfo(
            String method,
            String provider,
            String status,
            String transactionId,
            LocalDateTime approvedAt,
            String receiptUrl,
            String virtualAccountBank,
            String virtualAccountNumber,
            String virtualAccountDueDate) {
    }

    public record Fulfillment(
            String warehouseCode,
            String warehouseName,
            String warehouseAddress,
            Double distanceKm,
            String assignmentBasis) {
    }

    public record ShipmentInfo(
            Long shipmentId,
            String shipmentNo,
            String status,
            String worker,
            LocalDateTime createdAt,
            LocalDateTime shippedAt) {
        static ShipmentInfo from(Shipment shipment) {
            return new ShipmentInfo(
                    shipment.getShipmentId(),
                    shipment.getShipmentNo(),
                    shipment.getStatus() == null ? null : shipment.getStatus().name(),
                    shipment.getWorker(),
                    shipment.getCreatedAt(),
                    shipment.getShippedAt());
        }
    }

    public record DeliveryInfo(
            Long deliveryId,
            String carrierName,
            String trackingNumber,
            String status,
            int progress,
            LocalDateTime shippedAt,
            LocalDateTime expectedDeliveryAt,
            LocalDateTime deliveredAt,
            String returnStatus) {
        static DeliveryInfo from(Delivery delivery) {
            return new DeliveryInfo(
                    delivery.getDeliveryId(),
                    delivery.getCarrierName(),
                    delivery.getTrackingNumber(),
                    delivery.getStatus() == null ? null : delivery.getStatus().name(),
                    delivery.getStatus() == null ? 0 : delivery.getStatus().getProgress(),
                    delivery.getShippedAt(),
                    delivery.getExpectedDeliveryAt(),
                    delivery.getDeliveredAt(),
                    delivery.getReturnStatus() == null
                            ? null
                            : delivery.getReturnStatus().name());
        }
    }

    public record TimelineEvent(
            String code,
            LocalDateTime occurredAt,
            boolean completed,
            String note) {
    }
}
