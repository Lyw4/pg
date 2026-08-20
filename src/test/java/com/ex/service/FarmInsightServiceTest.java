package com.ex.service;

import com.ex.entity.CustomerOrder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FarmInsightServiceTest {

    @Test
    void cancelledOrderIsShownAsCancelledWithoutDelivery() {
        assertEquals(
                "주문 취소",
                FarmInsightService.recentDeliveryStatus(
                        CustomerOrder.OrderStatus.CANCELLED, null));
    }

    @Test
    void activeOrderWithoutDeliveryIsShownAsPreparing() {
        assertEquals(
                "배송 준비 전",
                FarmInsightService.recentDeliveryStatus(
                        CustomerOrder.OrderStatus.PAID, null));
    }
}
