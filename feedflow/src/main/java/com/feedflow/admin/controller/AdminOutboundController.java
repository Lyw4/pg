package com.feedflow.admin.controller;

import com.feedflow.admin.dto.OrderDispatchResultDto;
import com.feedflow.admin.dto.OutboundForm;
import com.feedflow.admin.dto.OutboundResultDto;
import com.feedflow.admin.service.OutboundService;
import com.feedflow.admin.service.ProductService;
import com.feedflow.common.exception.BusinessRuleException;
import com.feedflow.security.LoginUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * 출고 관리 화면 컨트롤러 (선입선출 FEFO 출고).
 */
@Controller
@RequestMapping("/admin/outbound")
@RequiredArgsConstructor
public class AdminOutboundController {

    private static final String LIST_VIEW = "admin/outbound/list";
    private static final String ORDER_VIEW = "admin/outbound/order-detail";
    private static final String DIRECT_VIEW = "admin/outbound/direct";

    private final OutboundService outboundService;
    private final ProductService productService;

    @ModelAttribute("menu")
    public String menu() {
        return "outbound";
    }

    /* ------------------------------------------------------------------
     * 출고 대기 주문 목록
     * ------------------------------------------------------------------ */

    @GetMapping
    public String list(Model model) {
        model.addAttribute("orders", outboundService.getDispatchTargets());
        model.addAttribute("subMenu", "list");
        return LIST_VIEW;
    }

    /* ------------------------------------------------------------------
     * 주문 출고 (FEFO 미리보기 → 처리)
     * ------------------------------------------------------------------ */

    @GetMapping("/orders/{orderId}")
    public String orderDetail(@PathVariable("orderId") Long orderId, Model model) {
        model.addAttribute("preview", outboundService.getOrderPreview(orderId));
        model.addAttribute("subMenu", "list");
        return ORDER_VIEW;
    }

    @PostMapping("/orders/{orderId}/dispatch")
    public String dispatchOrder(@PathVariable("orderId") Long orderId,
                                @AuthenticationPrincipal LoginUser loginUser,
                                RedirectAttributes redirectAttributes) {

        Long userId = LoginUser.idOf(loginUser);
        String userName = LoginUser.nameOf(loginUser);

        try {
            OrderDispatchResultDto result = outboundService.dispatchOrder(orderId, userId, userName);
            redirectAttributes.addFlashAttribute("successMessage", result.getSummaryMessage());
            redirectAttributes.addFlashAttribute("dispatchResult", result);
            return "redirect:/admin/outbound";
        } catch (BusinessRuleException e) {
            // 재고 부족 등 업무 규칙 위반 → 상세 화면으로 되돌려 원인을 보여준다
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/admin/outbound/orders/" + orderId;
        }
    }

    /* ------------------------------------------------------------------
     * 직접 출고 (주문과 무관한 출고)
     * ------------------------------------------------------------------ */

    @GetMapping("/direct")
    public String directForm(Model model) {
        model.addAttribute("outboundForm", new OutboundForm());
        prepareDirectForm(model);
        return DIRECT_VIEW;
    }

    @PostMapping("/direct")
    public String dispatchDirect(@Valid @ModelAttribute("outboundForm") OutboundForm outboundForm,
                                 BindingResult bindingResult,
                                 @AuthenticationPrincipal LoginUser loginUser,
                                 Model model,
                                 RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            prepareDirectForm(model);
            return DIRECT_VIEW;
        }

        Long userId = LoginUser.idOf(loginUser);
        String userName = LoginUser.nameOf(loginUser);

        try {
            OutboundResultDto result = outboundService.dispatch(outboundForm, userId, userName);
            redirectAttributes.addFlashAttribute("successMessage", result.getSummaryMessage());
            redirectAttributes.addFlashAttribute("outboundResult", result);
        } catch (BusinessRuleException e) {
            bindingResult.reject("businessRule", e.getMessage());
            prepareDirectForm(model);
            return DIRECT_VIEW;
        }

        return "redirect:/admin/outbound/direct";
    }

    private void prepareDirectForm(Model model) {
        model.addAttribute("products", productService.getActiveProducts());
        model.addAttribute("subMenu", "direct");
    }
}
