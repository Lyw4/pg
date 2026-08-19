package com.ex.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ex.entity.EmployeeRole;
import com.ex.service.EmployeeManagementService;
import com.ex.service.DemandPlanService;
import com.ex.service.FarmDeliveryAutomationService;
import com.ex.service.SurplusInventoryControlService;

import java.time.LocalDate;

import lombok.RequiredArgsConstructor;

/**
 * 조원 WMS 모듈의 관리자 URL과 사원 관리 기능을 통합 프로젝트에 연결합니다.
 */
@Controller
@RequiredArgsConstructor
public class AdminFeatureController {

    private final EmployeeManagementService employeeManagementService;
    private final DemandPlanService demandPlanService;
    private final FarmDeliveryAutomationService farmDeliveryAutomationService;
    private final SurplusInventoryControlService surplusInventoryControlService;

    @GetMapping("/admin/employees")
    public String employees(Authentication authentication, Model model) {
        model.addAttribute(
                "employees",
                employeeManagementService.employees(authentication.getName()));
        model.addAttribute("roles", EmployeeRole.values());
        model.addAttribute("menu", "employees");
        model.addAttribute("pageTitle", "사원 관리");
        model.addAttribute("sectionTitle", "시스템 관리");
        return "admin/employees";
    }

    @PostMapping("/admin/employees/{employeeId}/role")
    public String changeEmployeeRole(
            @PathVariable(name = "employeeId") Long employeeId,
            @RequestParam(name = "role") EmployeeRole role,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        try {
            String employeeName = employeeManagementService.changeRole(
                    employeeId,
                    role,
                    authentication.getName());
            redirectAttributes.addFlashAttribute(
                    "employeeMessage",
                    employeeName + " 님의 권한을 "
                            + role.getLabel() + "으로 변경했습니다.");
        } catch (RuntimeException exception) {
            redirectAttributes.addFlashAttribute(
                    "employeeError",
                    exception.getMessage());
        }
        return "redirect:/admin/employees";
    }

    @GetMapping("/admin/farm-customers")
    public String farmCustomers() {
        return "redirect:/distribution?view=farms";
    }

    @GetMapping("/admin/bins")
    public String bins() {
        return "redirect:/admin/wms?view=bins";
    }

    @GetMapping("/admin/inventory")
    public String inventory() {
        return "redirect:/inventory?view=stock";
    }

    @GetMapping("/admin/demand-plan")
    public String demandPlan(
            @RequestParam(name = "referenceDate", required = false) LocalDate referenceDate,
            Model model) {
        LocalDate selectedDate = referenceDate == null ? LocalDate.now() : referenceDate;
        model.addAttribute("plan", demandPlanService.plan(LocalDate.now()));
        model.addAttribute("deliveryPreview", farmDeliveryAutomationService.preview(selectedDate));
        model.addAttribute("referenceDate", selectedDate);
        model.addAttribute("menu", "demandPlan");
        model.addAttribute("pageTitle", "수요 계획");
        return "admin/demand-plan";
    }

    @PostMapping("/admin/demand-plan/farm-deliveries/execute")
    public String executeFarmDeliveries(
            @RequestParam(name = "referenceDate") LocalDate referenceDate,
            RedirectAttributes redirectAttributes) {
        var result = farmDeliveryAutomationService.execute(referenceDate, "MANUAL");
        redirectAttributes.addFlashAttribute(
                result.failedCount() == 0 ? "deliveryAutomationMessage" : "deliveryAutomationError",
                "기준일 " + referenceDate + " · 생성 " + result.createdCount()
                        + "건, 중복 제외 " + result.skippedCount()
                        + "건, 실패 " + result.failedCount() + "건");
        return "redirect:/admin/demand-plan?referenceDate=" + referenceDate;
    }

    @PostMapping("/admin/demand-plan/farm-deliveries/inbound-request")
    public String createFarmInboundRequest(
            @RequestParam(name = "farmCustomerId") Long farmCustomerId,
            @RequestParam(name = "referenceDate") LocalDate referenceDate,
            RedirectAttributes redirectAttributes) {
        try {
            redirectAttributes.addFlashAttribute("deliveryAutomationMessage",
                    farmDeliveryAutomationService.createInboundRequest(
                            farmCustomerId, referenceDate));
        } catch (RuntimeException exception) {
            redirectAttributes.addFlashAttribute("deliveryAutomationError", exception.getMessage());
        }
        return "redirect:/admin/demand-plan?referenceDate=" + referenceDate;
    }

    @PostMapping("/admin/demand-plan/farm-deliveries/immediate-inbound")
    public String receiveFarmInboundImmediately(
            @RequestParam(name = "farmCustomerId") Long farmCustomerId,
            @RequestParam(name = "referenceDate") LocalDate referenceDate,
            RedirectAttributes redirectAttributes) {
        try {
            redirectAttributes.addFlashAttribute("deliveryAutomationMessage",
                    farmDeliveryAutomationService.receiveInboundImmediately(
                            farmCustomerId, referenceDate));
        } catch (RuntimeException exception) {
            redirectAttributes.addFlashAttribute("deliveryAutomationError", exception.getMessage());
        }
        return "redirect:/admin/demand-plan?referenceDate=" + referenceDate;
    }

    @PostMapping("/admin/demand-plan/surplus-control")
    public String controlSurplusInventory(RedirectAttributes redirectAttributes) {
        surplusInventoryControlService.controlSurplusInbound();
        redirectAttributes.addFlashAttribute("deliveryAutomationMessage",
                "과잉 제품의 권장 보유량을 월 수요 120%로 조정하고 추가 정기입고를 중지했습니다. 현재 재고는 기존 주문 출고로 정상 소진됩니다.");
        return "redirect:/admin/demand-plan";
    }

    @GetMapping("/admin/inventory/inbound")
    public String inbound() {
        return "redirect:/admin/wms?view=inbound";
    }

    @GetMapping("/admin/inventory/move")
    public String move() {
        return "redirect:/admin/wms?view=move";
    }

    @GetMapping("/admin/defects")
    public String defects() {
        return "redirect:/inventory?view=defects";
    }

    @GetMapping("/admin/inventory/disposal")
    public String disposal() {
        return "redirect:/admin/wms?view=disposal";
    }

    @GetMapping("/admin/inventory/movements")
    public String movements() {
        return "redirect:/admin/wms?view=movements";
    }

    @GetMapping("/admin/traceability")
    public String traceability() {
        return "redirect:/admin/wms?view=traceability";
    }

    @GetMapping("/admin/inventory/sync")
    public String sync() {
        return "redirect:/admin/wms?view=sync";
    }

    @GetMapping("/admin/outbound")
    public String outbound() {
        return "redirect:/inventory?view=shipments";
    }
}
