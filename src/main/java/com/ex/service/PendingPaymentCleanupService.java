package com.ex.service;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.ex.entity.CustomerOrder.OrderStatus;
import com.ex.repository.CustomerOrderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PendingPaymentCleanupService {

    private final CustomerOrderRepository orderRepository;
    private final PaymentApplyService paymentApplyService;

    @Value("${feedflow.payment.pending-timeout-minutes:15}")
    private long timeoutMinutes;

    @Scheduled(fixedDelayString = "${feedflow.payment.pending-cleanup-ms:60000}")
    public void releaseExpiredReservations() {
        LocalDateTime cutoff = LocalDateTime.now()
                .minusMinutes(Math.max(1, timeoutMinutes));
        orderRepository.findByStatusAndCreatedAtBefore(
                        OrderStatus.PAYMENT_PENDING, cutoff)
                .forEach(order -> {
                    try {
                        paymentApplyService.expirePending(order.getOrderNumber());
                    } catch (RuntimeException exception) {
                        log.warn("결제 대기 재고 해제 실패 order={} reason={}",
                                order.getOrderNumber(), exception.getMessage());
                    }
                });
    }
}
