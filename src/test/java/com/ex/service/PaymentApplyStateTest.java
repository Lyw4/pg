package com.ex.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.ex.entity.CustomerOrder;
import com.ex.entity.CustomerOrder.OrderStatus;
import com.ex.entity.PaymentMethod;
import com.ex.entity.PaymentStatus;
import com.ex.repository.CustomerOrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@SpringBootTest
@ActiveProfiles("test")
class PaymentApplyStateTest {

    @Autowired private PaymentApplyService paymentApplyService;
    @Autowired private PaymentService paymentService;
    @Autowired private CustomerOrderRepository orderRepository;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void providerCancellationAfterPaymentCannotBeOverwrittenByLateApproval() {
        CustomerOrder order = pendingOrder();
        String impUid = "imp_" + UUID.randomUUID();

        paymentApplyService.applyForWebhook(
                order.getOrderNumber(), impUid,
                payment(order, impUid, "paid"));
        paymentApplyService.applyForWebhook(
                order.getOrderNumber(), impUid,
                payment(order, impUid, "cancelled"));

        CustomerOrder cancelled = orderRepository.findById(
                order.getOrderId()).orElseThrow();
        assertEquals(OrderStatus.CANCELLED, cancelled.getStatus());
        assertEquals(PaymentStatus.CANCELLED, cancelled.getPaymentStatus());

        assertThrows(IllegalStateException.class,
                () -> paymentApplyService.applyForWebhook(
                        order.getOrderNumber(), impUid,
                        payment(order, impUid, "paid")));
        CustomerOrder unchanged = orderRepository.findById(
                order.getOrderId()).orElseThrow();
        assertEquals(OrderStatus.CANCELLED, unchanged.getStatus());
        assertEquals(PaymentStatus.CANCELLED, unchanged.getPaymentStatus());
    }

    @Test
    void expiredPendingPaymentReleasesOrderAndRejectsLateApproval() {
        CustomerOrder order = pendingOrder();
        String impUid = "imp_" + UUID.randomUUID();

        paymentApplyService.expirePending(order.getOrderNumber());

        CustomerOrder expired = orderRepository.findById(
                order.getOrderId()).orElseThrow();
        assertEquals(OrderStatus.CANCELLED, expired.getStatus());
        assertEquals(PaymentStatus.FAILED, expired.getPaymentStatus());
        assertThrows(IllegalStateException.class,
                () -> paymentApplyService.applyForWebhook(
                        order.getOrderNumber(), impUid,
                        payment(order, impUid, "paid")));
    }

    @Test
    void administratorCancellationFinishesLocalOrderAfterRefundStage() {
        CustomerOrder order = pendingOrder();

        paymentService.cancelOrderByAdmin(
                order.getOrderId(), "관리자 승인 취소", "admin");

        CustomerOrder cancelled = orderRepository.findById(
                order.getOrderId()).orElseThrow();
        assertEquals(OrderStatus.CANCELLED, cancelled.getStatus());
        assertEquals(PaymentStatus.CANCELLED, cancelled.getPaymentStatus());
        assertEquals("관리자 승인 취소", cancelled.getCancellationReason());
    }

    private CustomerOrder pendingOrder() {
        CustomerOrder order = CustomerOrder.storefront(
                "PAY-" + UUID.randomUUID(),
                "결제테스트농장",
                "010-1111-2222",
                "서울특별시 테스트로 1",
                "101호",
                "정문",
                "결제 상태 테스트",
                PaymentMethod.CARD,
                false,
                BigDecimal.valueOf(10_000),
                BigDecimal.ZERO,
                BigDecimal.ZERO);
        order.prepareExternalPayment();
        return orderRepository.saveAndFlush(order);
    }

    private ObjectNode payment(
            CustomerOrder order,
            String impUid,
            String status) {
        ObjectNode payment = objectMapper.createObjectNode();
        payment.put("imp_uid", impUid);
        payment.put("merchant_uid", order.getOrderNumber());
        payment.put("amount", order.getFinalPrice().intValueExact());
        payment.put("status", status);
        payment.put("pay_method", order.getPaymentMethod() == PaymentMethod.BANK_TRANSFER
                ? "vbank"
                : order.getPaymentMethod() == PaymentMethod.KAKAO_PAY
                        ? "kakaopay"
                        : "card");
        return payment;
    }
}
