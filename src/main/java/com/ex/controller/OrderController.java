package com.ex.controller;

import com.ex.dto.CreateOrderRequest;
import com.ex.dto.OrderResponse;
import com.ex.dto.OrderDetailResponse;
import com.ex.service.OrderService;
import com.ex.service.PaymentService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final PaymentService paymentService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            HttpSession session) {
        return orderService.createOrder(request, memberId(session));
    }

    @GetMapping("/{orderNumber}")
    public OrderResponse findOrder(
            @PathVariable(name = "orderNumber") String orderNumber,
            @RequestParam(name = "phone") String phone,
            HttpSession session
    ) {
        return orderService.findOrder(
                orderNumber, phone, memberId(session));
    }

    @PatchMapping("/{orderNumber}/cancel")
    public OrderResponse cancelOrder(
            @PathVariable(name = "orderNumber") String orderNumber,
            HttpSession session
    ) {
        return paymentService.cancelOrder(orderNumber, memberId(session));
    }

    @GetMapping("/mine")
    public List<OrderResponse> findMyOrders(HttpSession session) {
        return orderService.findMemberOrders(memberId(session));
    }

    @GetMapping("/mine/{orderNumber}/detail")
    public OrderDetailResponse findMyOrderDetail(
            @PathVariable("orderNumber") String orderNumber,
            HttpSession session) {
        return orderService.findMemberOrderDetail(
                orderNumber,
                memberId(session));
    }

    @PatchMapping("/mine/{orderNumber}/cancel")
    public OrderResponse cancelMyOrder(
            @PathVariable("orderNumber") String orderNumber,
            HttpSession session) {
        return paymentService.cancelOrder(orderNumber, memberId(session));
    }

    private Long memberId(HttpSession session) {
        return SessionMemberSupport.requireMemberId(session);
    }
}
