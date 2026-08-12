package com.ex.controller;

import java.math.BigDecimal;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ex.service.AdminFeedModelService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class AdminFeedModelController {

    private final AdminFeedModelService adminFeedModelService;

    @GetMapping("/admin/feed-model")
    public String page(Model model) {
        model.addAttribute("policies", adminFeedModelService.policies());
        model.addAttribute("transfers",
                adminFeedModelService.transferRecommendations());
        model.addAttribute("analytics", adminFeedModelService.analytics());
        model.addAttribute("menu", "feedModel");
        return "admin/feed-model";
    }

    @PostMapping("/admin/feed-model/policies/{policyId}")
    public String updatePolicy(
            @PathVariable("policyId") Long policyId,
            @RequestParam("bagsPerHead") BigDecimal bagsPerHead,
            @RequestParam("preferredFeedWeight") int preferredFeedWeight,
            @RequestParam("warehouseStockWeight") int warehouseStockWeight,
            @RequestParam(name = "excludedProductIds", required = false)
            String excludedProductIds,
            RedirectAttributes redirectAttributes) {
        try {
            adminFeedModelService.updatePolicy(
                    policyId, bagsPerHead, preferredFeedWeight,
                    warehouseStockWeight, excludedProductIds);
            redirectAttributes.addFlashAttribute("modelMessage",
                    "추천 모델 기준과 버전을 갱신했습니다.");
        } catch (RuntimeException exception) {
            redirectAttributes.addFlashAttribute("modelError",
                    exception.getMessage());
        }
        return "redirect:/admin/feed-model";
    }

    @PostMapping("/admin/feed-model/transfers")
    public String executeTransfer(
            @RequestParam("productId") Long productId,
            @RequestParam("sourceWarehouseId") Long sourceWarehouseId,
            @RequestParam("destinationWarehouseId") Long destinationWarehouseId,
            @RequestParam("quantity") int quantity,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        try {
            adminFeedModelService.executeTransfer(
                    productId, sourceWarehouseId, destinationWarehouseId,
                    quantity, authentication.getName());
            redirectAttributes.addFlashAttribute("modelMessage",
                    "LOT 구역 재고와 센터 운영 재고를 함께 이동했습니다.");
        } catch (RuntimeException exception) {
            redirectAttributes.addFlashAttribute("modelError",
                    exception.getMessage());
        }
        return "redirect:/admin/feed-model";
    }
}
