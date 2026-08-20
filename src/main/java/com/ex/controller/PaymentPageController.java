package com.ex.controller;

import com.ex.dto.OrderResponse;
import com.ex.entity.PaymentStatus;
import com.ex.service.OrderService;
import com.ex.service.PaymentService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.ui.Model;
import org.springframework.web.util.UriComponentsBuilder;

@Controller
@RequiredArgsConstructor
public class PaymentPageController {

    private final PaymentService paymentService;
    private final OrderService orderService;

    @GetMapping("/payments/test-receipt")
    public String testReceipt() {
        return "test-receipt";
    }

    @GetMapping("/payments/receipt/{orderNumber}")
    public String receipt(
            @PathVariable("orderNumber") String orderNumber,
            HttpSession session,
            Model model) {
        Long memberId = sessionMemberId(session);
        if (memberId == null) {
            return "redirect:/?account=login&sessionExpired=true";
        }
        try {
            model.addAttribute("detail", orderService.findMemberOrderDetail(
                    orderNumber,
                    memberId));
            return "receipt";
        } catch (IllegalArgumentException exception) {
            model.addAttribute("message", exception.getMessage());
            return "error/404";
        }
    }

    private Long sessionMemberId(HttpSession session) {
        // 영수증 화면은 비로그인 상태를 404 안내로 처리하므로 예외를 던지지
        // 않는 쪽을 사용합니다.
        return SessionMemberSupport.memberIdOrNull(session);
    }

    @GetMapping("/payments/portone/redirect")
    public String redirect(
            @RequestParam(name = "imp_uid", required = false) String impUid,
            @RequestParam(name = "merchant_uid") String merchantUid,
            @RequestParam("token") String token,
            @RequestParam(name = "error_msg", required = false) String errorMessage) {
        if (impUid == null || impUid.isBlank()) {
            paymentService.failPendingPaymentByCallback(merchantUid, token);
            return result("fail", merchantUid,
                    errorMessage == null ? "결제가 완료되지 않았습니다." : errorMessage);
        }
        try {
            OrderResponse order = paymentService.completePortOneByCallback(impUid, merchantUid, token);
            String payment = order.paymentStatus() == PaymentStatus.DONE
                    ? "success"
                    : order.paymentStatus() == PaymentStatus.WAITING_FOR_DEPOSIT ? "waiting" : "fail";
            return result(payment, order.orderNumber(), null);
        } catch (RuntimeException exception) {
            return result("fail", merchantUid, exception.getMessage());
        }
    }

    private String result(String payment, String orderNumber, String message) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/")
                .queryParam("payment", payment)
                .queryParam("orderNumber", orderNumber);
        if (message != null && !message.isBlank()) builder.queryParam("message", message);
        return "redirect:" + builder.build().encode().toUriString();
    }
}
