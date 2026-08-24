package com.ex.service;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.ex.entity.CustomerOrder;
import com.ex.entity.CustomerOrder.OrderStatus;
import com.ex.entity.PaymentMethod;
import com.ex.repository.CustomerOrderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PendingPaymentCleanupService {

    private final CustomerOrderRepository orderRepository;
    private final PaymentApplyService paymentApplyService;

    /**
     * 카드·간편결제는 결제창을 이탈하면 되돌아올 이유가 없으므로 짧게 정리합니다.
     */
    @Value("${feedflow.payment.pending-timeout-minutes:15}")
    private long timeoutMinutes;

    /**
     * 무통장 입금은 고객이 은행에 실제로 송금해야 합니다. 결제창을 거쳐
     * 가상계좌가 발급되면 WAITING_FOR_DEPOSIT으로 바뀌어 만료 대상에서
     * 빠지지만, 발급 전(READY) 구간에는 결제수단 구분이 없어 카드와 같은
     * 15분이 적용됐습니다. 전자결제가 설정되지 않은 환경에서는 주문이
     * 계속 READY로 남아 입금할 시간도 없이 전부 실패 처리됐습니다.
     */
    @Value("${feedflow.payment.deposit-timeout-minutes:4320}")
    private long depositTimeoutMinutes;

    /**
     * H2 기존 스키마 보정(CommandLineRunner)이 끝나기 전에 스케줄러가
     * CUSTOMER_ORDER를 조회하면 일시적으로 테이블을 찾지 못할 수 있습니다.
     */
    private volatile boolean applicationReady;

    @EventListener(ApplicationReadyEvent.class)
    public void markApplicationReady() {
        applicationReady = true;
    }

    @Scheduled(fixedDelayString = "${feedflow.payment.pending-cleanup-ms:60000}")
    public void releaseExpiredReservations() {
        if (!applicationReady) return;
        LocalDateTime now = LocalDateTime.now();
        // 가장 짧은 기한으로 후보를 좁힌 뒤 주문별 결제수단 기한으로 다시 거릅니다.
        long earliest = Math.min(
                normalized(timeoutMinutes), normalized(depositTimeoutMinutes));
        orderRepository.findByStatusAndCreatedAtBefore(
                        OrderStatus.PAYMENT_PENDING, now.minusMinutes(earliest))
                .forEach(order -> {
                    if (!isExpired(order, now)) {
                        return;
                    }
                    try {
                        paymentApplyService.expirePending(order.getOrderNumber());
                    } catch (RuntimeException exception) {
                        log.warn("결제 대기 재고 해제 실패 order={} reason={}",
                                order.getOrderNumber(), exception.getMessage());
                    }
                });
    }

    private boolean isExpired(CustomerOrder order, LocalDateTime now) {
        LocalDateTime createdAt = order.getCreatedAt();
        if (createdAt == null) {
            return false;
        }
        return createdAt.isBefore(now.minusMinutes(timeoutFor(order)));
    }

    private long timeoutFor(CustomerOrder order) {
        return order.getPaymentMethod() == PaymentMethod.BANK_TRANSFER
                ? normalized(depositTimeoutMinutes)
                : normalized(timeoutMinutes);
    }

    private long normalized(long minutes) {
        return Math.max(1, minutes);
    }
}
