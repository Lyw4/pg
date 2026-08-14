package com.ex.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.ex.dto.OrderResponse;
import com.ex.entity.CustomerOrder;
import com.ex.entity.PaymentStatus;
import com.ex.repository.CustomerOrderRepository;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;

/** PortOne 네트워크 조회가 끝난 뒤 DB 상태만 짧게 잠그고 반영합니다. */
@Service
@RequiredArgsConstructor
public class PaymentApplyService {

    private static final DateTimeFormatter VBANK_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                    .withZone(ZoneId.of("Asia/Seoul"));

    private final CustomerOrderRepository orderRepository;
    private final OrderService orderService;

    @Transactional
    public OrderResponse applyForMember(
            String orderNumber,
            String token,
            Long memberId,
            String impUid,
            JsonNode payment) {
        if (memberId == null) throw new IllegalArgumentException("로그인이 필요합니다.");
        CustomerOrder order = locked(orderNumber);
        if (order.getMember() == null
                || !memberId.equals(order.getMember().getId())) {
            throw new IllegalArgumentException("본인 주문만 결제 처리할 수 있습니다.");
        }
        requireToken(order, token);
        return applyVerified(order, impUid, payment);
    }

    @Transactional
    public OrderResponse applyForCallback(
            String orderNumber,
            String token,
            String impUid,
            JsonNode payment) {
        CustomerOrder order = locked(orderNumber);
        requireToken(order, token);
        return applyVerified(order, impUid, payment);
    }

    @Transactional
    public OrderResponse applyForWebhook(
            String orderNumber,
            String impUid,
            JsonNode payment) {
        return applyVerified(locked(orderNumber), impUid, payment);
    }

    @Transactional
    public OrderResponse cancelForMember(String orderNumber, Long memberId) {
        if (memberId == null) throw new IllegalArgumentException("로그인이 필요합니다.");
        CustomerOrder order = locked(orderNumber);
        if (order.getMember() == null
                || !memberId.equals(order.getMember().getId())) {
            throw new IllegalArgumentException("본인 주문만 취소할 수 있습니다.");
        }
        if (order.getStatus() == CustomerOrder.OrderStatus.CANCELLED) {
            throw new IllegalStateException("이미 취소된 주문입니다.");
        }
        if (order.getStatus() == CustomerOrder.OrderStatus.SHIPPING
                || order.getStatus() == CustomerOrder.OrderStatus.DELIVERED) {
            throw new IllegalStateException("배송이 시작된 주문은 고객이 직접 취소할 수 없습니다.");
        }
        order.cancelPayment();
        orderService.releasePaymentReservation(order, "회원 마이페이지 주문 취소");
        return OrderResponse.from(order);
    }

    private OrderResponse applyVerified(
            CustomerOrder order,
            String requestedImpUid,
            JsonNode payment) {
        String verifiedImpUid = requiredText(payment, "imp_uid", "결제번호");
        String merchantUid = requiredText(payment, "merchant_uid", "주문번호");
        int paidAmount = payment.path("amount").asInt(-1);
        int expectedAmount = order.getFinalPrice().intValueExact();
        if (!secureEquals(requestedImpUid, verifiedImpUid)
                || !secureEquals(order.getOrderNumber(), merchantUid)) {
            throw new IllegalArgumentException("포트원 결제 식별값이 주문과 일치하지 않습니다.");
        }
        if (paidAmount != expectedAmount) {
            throw new IllegalArgumentException("포트원 결제 금액이 주문 금액과 일치하지 않습니다.");
        }
        CustomerOrder owner = orderRepository
                .findByProviderTransactionId(verifiedImpUid).orElse(null);
        if (owner != null && !owner.getOrderId().equals(order.getOrderId())) {
            throw new IllegalArgumentException("이미 다른 주문에 반영된 결제 거래번호입니다.");
        }
        if (order.getPaymentStatus() == PaymentStatus.DONE) {
            if (!secureEquals(order.getProviderTransactionId(), verifiedImpUid)) {
                throw new IllegalArgumentException("이미 다른 결제번호로 완료된 주문입니다.");
            }
            return OrderResponse.from(order);
        }

        switch (payment.path("status").asText("")) {
            case "paid" -> order.completePayment(
                    verifiedImpUid, payment.path("receipt_url").asText(null));
            case "ready" -> order.waitForDeposit(
                    verifiedImpUid,
                    payment.path("vbank_name").asText(null),
                    payment.path("vbank_num").asText(null),
                    formatVbankDate(payment.path("vbank_date").asLong(0)));
            case "failed" -> {
                order.failPayment();
                orderService.releasePaymentReservation(order, "결제 실패");
            }
            case "cancelled", "canceled" -> {
                order.cancelPayment();
                orderService.releasePaymentReservation(order, "결제 취소");
            }
            default -> throw new IllegalArgumentException(
                    "아직 완료되지 않은 포트원 결제 상태입니다.");
        }
        return OrderResponse.from(order);
    }

    private CustomerOrder locked(String orderNumber) {
        return orderRepository.findByOrderNumberForUpdate(orderNumber)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다."));
    }

    private void requireToken(CustomerOrder order, String token) {
        if (!secureEquals(order.getPaymentCallbackToken(), token)) {
            throw new IllegalArgumentException("결제 확인 토큰이 올바르지 않습니다.");
        }
    }

    private String requiredText(JsonNode node, String field, String label) {
        String value = node.path(field).asText("");
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("포트원 " + label + "가 없습니다.");
        }
        return value;
    }

    private String formatVbankDate(long epochSecond) {
        return epochSecond <= 0 ? null
                : VBANK_DATE_FORMAT.format(Instant.ofEpochSecond(epochSecond));
    }

    private boolean secureEquals(String left, String right) {
        if (left == null || right == null) return false;
        return MessageDigest.isEqual(
                left.getBytes(StandardCharsets.UTF_8),
                right.getBytes(StandardCharsets.UTF_8));
    }
}
