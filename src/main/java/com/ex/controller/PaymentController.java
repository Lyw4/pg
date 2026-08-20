package com.ex.controller;

import com.ex.dto.OrderResponse;
import com.ex.dto.PaymentConfigResponse;
import com.ex.dto.PortOnePaymentCompleteRequest;
import com.ex.service.PaymentService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping("/config")
    public PaymentConfigResponse config(HttpSession session) {
        return paymentService.paymentConfig(memberId(session));
    }

    @PostMapping("/portone/complete")
    public OrderResponse complete(
            @Valid @RequestBody PortOnePaymentCompleteRequest request,
            HttpSession session) {
        return paymentService.completePortOne(
                request.impUid(), request.merchantUid(), request.paymentToken(), memberId(session));
    }

    @PostMapping("/portone/reconcile/{orderNumber}")
    public OrderResponse reconcile(
            @PathVariable("orderNumber") String orderNumber,
            HttpSession session) {
        return paymentService.reconcilePortOne(orderNumber, memberId(session));
    }

    @PostMapping("/portone/fail")
    public void fail(
            @RequestParam("orderNumber") String orderNumber,
            @RequestParam("token") String token,
            HttpSession session) {
        paymentService.failPendingPayment(orderNumber, token, memberId(session));
    }

    @PostMapping("/portone/webhook")
    public Map<String, String> webhook(@RequestBody JsonNode payload) {
        paymentService.handlePortOneWebhook(payload);
        return Map.of("result", "ok");
    }

    private Long memberId(HttpSession session) {
        return SessionMemberSupport.requireMemberId(session);
    }
}
