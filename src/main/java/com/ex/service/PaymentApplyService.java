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
import com.ex.entity.PaymentMethod;
import com.ex.entity.PaymentStatus;
import com.ex.repository.CustomerOrderRepository;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;

/** PortOne 네트워크 조회가 끝난 뒤 DB 상태만 짧게 잠그고 반영합니다. */
@Service
@RequiredArgsConstructor
public class PaymentApplyService {

    public record CancellationContext(
            String providerTransactionId,
            PaymentStatus paymentStatus,
            int amount) {
    }

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

    /**
     * Member-authenticated recovery has no browser callback token. Ownership is
     * checked under the row lock, while provider identifiers and amount are still
     * checked by applyVerified.
     */
    @Transactional
    public OrderResponse applyForReconcile(
            String orderNumber,
            Long memberId,
            String impUid,
            JsonNode payment) {
        if (memberId == null) throw new IllegalArgumentException("로그인이 필요합니다.");
        CustomerOrder order = locked(orderNumber);
        requireOwner(order, memberId);
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
    public CancellationContext beginCancellation(
            String orderNumber,
            Long memberId) {
        if (memberId == null) throw new IllegalArgumentException("로그인이 필요합니다.");
        CustomerOrder order = locked(orderNumber);
        requireOwner(order, memberId);
        if (order.getStatus() == CustomerOrder.OrderStatus.CANCELLED
                && order.getPaymentStatus() != PaymentStatus.CANCEL_REQUESTED) {
            throw new IllegalStateException("이미 취소된 주문입니다.");
        }
        PaymentStatus previousPayment = order.getPaymentStatus();
        order.beginPaymentCancellation("회원 마이페이지");
        return new CancellationContext(
                order.getProviderTransactionId(),
                previousPayment,
                order.getFinalPrice().intValueExact());
    }

    @Transactional
    public CancellationContext beginCancellationForAdmin(
            Long orderId,
            String manager) {
        if (orderId == null) throw new IllegalArgumentException("주문을 선택해 주세요.");
        CustomerOrder order = locked(orderId);
        PaymentStatus previousPayment = order.getPaymentStatus()
                == PaymentStatus.CANCEL_REQUESTED
                && order.getCancellationPreviousPaymentStatus() != null
                ? order.getCancellationPreviousPaymentStatus()
                : order.getPaymentStatus();
        order.beginPaymentCancellation(manager);
        return new CancellationContext(
                order.getProviderTransactionId(),
                previousPayment,
                order.getFinalPrice().intValueExact());
    }

    @Transactional
    public OrderResponse completeCancellation(
            String orderNumber,
            Long memberId) {
        CustomerOrder order = locked(orderNumber);
        requireOwner(order, memberId);
        if (order.getPaymentStatus() != PaymentStatus.CANCEL_REQUESTED) {
            throw new IllegalStateException("결제 취소 요청 상태가 아닙니다.");
        }
        order.cancelPayment();
        orderService.releasePaymentReservation(order, "회원 마이페이지 주문 취소");
        return OrderResponse.from(order);
    }

    @Transactional
    public void abortCancellation(String orderNumber, Long memberId) {
        CustomerOrder order = locked(orderNumber);
        requireOwner(order, memberId);
        order.abortPaymentCancellation();
    }

    @Transactional
    public void abortCancellationForAdmin(Long orderId) {
        locked(orderId).abortPaymentCancellation();
    }

    @Transactional
    public void failUnstartedForMember(
            String orderNumber,
            String token,
            Long memberId) {
        if (memberId == null) throw new IllegalArgumentException("로그인이 필요합니다.");
        CustomerOrder order = locked(orderNumber);
        requireOwner(order, memberId);
        requireToken(order, token);
        failUnstarted(order);
    }

    @Transactional
    public void failUnstartedForCallback(String orderNumber, String token) {
        CustomerOrder order = locked(orderNumber);
        requireToken(order, token);
        failUnstarted(order);
    }

    /**
     * 무통장 입금 주문을 관리자가 계좌 입금을 확인한 뒤 수동으로 완료 처리합니다.
     * 재고는 주문 생성 시 이미 확정되어 있으므로 상태만 전이시킵니다.
     * 전자결제 거래가 시작된 주문은 포트원 검증 결과가 정답이라 건드리지 않습니다.
     */
    @Transactional
    public void confirmManualDeposit(Long orderId, String manager) {
        if (!StringUtils.hasText(manager)) {
            throw new IllegalArgumentException("입금 확인 담당자를 입력해 주세요.");
        }
        CustomerOrder order = locked(orderId);
        if (order.getPaymentMethod() != PaymentMethod.BANK_TRANSFER) {
            throw new IllegalArgumentException(
                    "무통장 입금 주문만 수동으로 입금 확인할 수 있습니다.");
        }
        if (StringUtils.hasText(order.getProviderTransactionId())) {
            throw new IllegalStateException(
                    "전자결제 거래가 시작된 주문은 결제 확인 결과로만 반영됩니다.");
        }
        if (order.getStatus() != CustomerOrder.OrderStatus.PAYMENT_PENDING) {
            throw new IllegalStateException(
                    "입금 대기 상태의 주문만 입금 확인할 수 있습니다.");
        }
        if (order.getPaymentStatus() != PaymentStatus.READY) {
            throw new IllegalStateException(
                    "이미 처리된 결제 상태여서 입금 확인할 수 없습니다.");
        }
        order.completePayment(null, null);
    }

    @Transactional
    public void expirePending(String orderNumber) {
        CustomerOrder order = locked(orderNumber);
        if (order.getStatus() != CustomerOrder.OrderStatus.PAYMENT_PENDING
                || order.getPaymentStatus() != PaymentStatus.READY) {
            return;
        }
        failUnstarted(order);
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
        validatePaymentMethod(order, payment);
        CustomerOrder owner = orderRepository
                .findByProviderTransactionId(verifiedImpUid).orElse(null);
        if (owner != null && !owner.getOrderId().equals(order.getOrderId())) {
            throw new IllegalArgumentException("이미 다른 주문에 반영된 결제 거래번호입니다.");
        }
        String providerStatus = payment.path("status").asText("");
        boolean locallyTerminated = order.getStatus() == CustomerOrder.OrderStatus.CANCELLED
                || order.getPaymentStatus() == PaymentStatus.FAILED
                || order.getPaymentStatus() == PaymentStatus.CANCELLED
                || order.getPaymentStatus() == PaymentStatus.CANCEL_REQUESTED;
        if (locallyTerminated
                && !"cancelled".equals(providerStatus)
                && !"canceled".equals(providerStatus)) {
            throw new IllegalStateException(
                    "종료되었거나 취소 처리 중인 주문에는 결제 상태를 다시 적용할 수 없습니다.");
        }
        if (order.getPaymentStatus() == PaymentStatus.DONE
                && "paid".equals(providerStatus)) {
            if (!secureEquals(order.getProviderTransactionId(), verifiedImpUid)) {
                throw new IllegalArgumentException("이미 다른 결제번호로 완료된 주문입니다.");
            }
            return OrderResponse.from(order);
        }

        switch (providerStatus) {
            case "paid" -> order.completePayment(
                    verifiedImpUid, payment.path("receipt_url").asText(null));
            case "ready" -> {
                if (order.getPaymentMethod() != PaymentMethod.BANK_TRANSFER) {
                    throw new IllegalStateException(
                            "카드·간편결제는 결제 승인이 완료된 뒤에만 주문에 반영됩니다. "
                                    + "결제창에서 승인을 마친 후 결제상태를 다시 확인해 주세요.");
                }
                order.waitForDeposit(
                        verifiedImpUid,
                        payment.path("vbank_name").asText(null),
                        payment.path("vbank_num").asText(null),
                        formatVbankDate(payment.path("vbank_date").asLong(0)));
            }
            case "failed" -> {
                order.failPayment();
                orderService.releasePaymentReservation(order, "결제 실패");
            }
            case "cancelled", "canceled" -> {
                if (order.getStatus() == CustomerOrder.OrderStatus.SHIPPING
                        || order.getStatus() == CustomerOrder.OrderStatus.DELIVERED) {
                    throw new IllegalStateException(
                            "출고 이후 결제 취소는 관리자 회수 절차로 처리해야 합니다.");
                }
                order.cancelPayment();
                orderService.releasePaymentReservation(order, "결제 취소");
            }
            default -> throw new IllegalArgumentException(
                    "아직 완료되지 않은 포트원 결제 상태입니다.");
        }
        return OrderResponse.from(order);
    }

    private void validatePaymentMethod(
            CustomerOrder order,
            JsonNode payment) {
        String providerMethod = requiredText(
                payment, "pay_method", "결제수단");
        boolean providerVirtualAccount = "vbank".equals(providerMethod);
        boolean orderVirtualAccount = order.getPaymentMethod()
                == PaymentMethod.BANK_TRANSFER;
        if (providerVirtualAccount != orderVirtualAccount) {
            throw new IllegalArgumentException(
                    "선택한 결제수단과 포트원 승인 결제수단이 일치하지 않습니다.");
        }
    }

    private CustomerOrder locked(String orderNumber) {
        return orderRepository.findByOrderNumberForUpdate(orderNumber)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다."));
    }

    private CustomerOrder locked(Long orderId) {
        return orderRepository.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다."));
    }

    private void requireOwner(CustomerOrder order, Long memberId) {
        if (order.getMember() == null
                || !memberId.equals(order.getMember().getId())) {
            throw new IllegalArgumentException("본인 주문만 결제 처리할 수 있습니다.");
        }
    }

    private void failUnstarted(CustomerOrder order) {
        if (StringUtils.hasText(order.getProviderTransactionId())
                || order.getPaymentStatus() == PaymentStatus.DONE
                || order.getPaymentStatus() == PaymentStatus.WAITING_FOR_DEPOSIT) {
            throw new IllegalStateException(
                    "외부 거래가 시작된 주문은 자동 실패 처리할 수 없습니다.");
        }
        order.failPayment();
        orderService.releasePaymentReservation(
                order, "결제창 취소 또는 결제 실패");
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
