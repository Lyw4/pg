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
    public String demandPlan(Model model) {
        model.addAttribute("plan", demandPlanService.plan(LocalDate.now()));
        model.addAttribute("menu", "demandPlan");
        model.addAttribute("pageTitle", "수요 계획");
        return "admin/demand-plan";
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
