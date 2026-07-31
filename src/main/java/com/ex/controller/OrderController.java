package com.ex.controller;

import com.ex.dto.CreateOrderRequest;
import com.ex.dto.CancelOrderRequest;
import com.ex.dto.OrderResponse;
import com.ex.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse createOrder(@Valid @RequestBody CreateOrderRequest request) {
        return orderService.createOrder(request);
    }

    @GetMapping("/{orderNumber}")
    public OrderResponse findOrder(
            @PathVariable(name = "orderNumber") String orderNumber,
            @RequestParam(name = "phone") String phone
    ) {
        return orderService.findOrder(orderNumber, phone);
    }

    @PatchMapping("/{orderNumber}/cancel")
    public OrderResponse cancelOrder(
            @PathVariable(name = "orderNumber") String orderNumber,
            @Valid @RequestBody CancelOrderRequest request
    ) {
        return orderService.cancelOrder(orderNumber, request.phone());
    }
}
