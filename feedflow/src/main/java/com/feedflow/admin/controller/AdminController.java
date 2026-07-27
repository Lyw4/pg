package com.feedflow.admin.controller;

import com.feedflow.admin.service.DashboardService;
import com.feedflow.admin.service.EmployeeService;
import com.feedflow.domain.Role;
import com.feedflow.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;

/**
 * 관리자 화면(HTML) 렌더링 전용 컨트롤러.
 * JSON 응답은 {@link AdminRestController} 가 담당한다.
 */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final DashboardService dashboardService;
    private final EmployeeService employeeService;

    /** 모든 관리자 화면 공통 - 로그인 사용자 정보 */
    @ModelAttribute("loginUser")
    public LoginUser loginUser(@AuthenticationPrincipal LoginUser loginUser) {
        return loginUser;
    }

    /**
     * 대시보드.
     * 공통 영역(안전재고 / 유통기한 / 오늘의 할 일)은 STAFF·ADMIN 모두,
     * 매출 통계는 ADMIN 에게만 모델에 담아 내려준다. (뷰단 + 백엔드 이중 차단)
     */
    @GetMapping({"", "/", "/dashboard"})
    public String dashboard(@AuthenticationPrincipal LoginUser loginUser, Model model) {
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("todayTask", dashboardService.getTodayTask());
        model.addAttribute("safetyStockAlerts", dashboardService.getSafetyStockAlerts());
        model.addAttribute("expiringLots", dashboardService.getExpiringLots());

        // 책임자 전용 데이터 - 일반 사원 응답에는 아예 포함되지 않는다.
        if (loginUser != null && loginUser.isAdmin()) {
            model.addAttribute("salesSummary", dashboardService.getSalesSummary());
        }

        model.addAttribute("menu", "dashboard");
        return "admin/dashboard";
    }

    /** 사원 목록 (책임자 전용) */
    @GetMapping("/employees")
    @PreAuthorize("hasRole('ADMIN')")
    public String employees(@AuthenticationPrincipal LoginUser loginUser, Model model) {
        Long loginUserId = loginUser == null ? null : loginUser.getUserId();

        model.addAttribute("employees", employeeService.getEmployees(loginUserId));
        model.addAttribute("roles", Role.values());
        model.addAttribute("menu", "employees");
        return "admin/employees";
    }

    /**
     * 사원 권한 변경 (책임자 전용).
     * 화면에서 메뉴를 숨기는 것과 별개로, 백엔드에서 @PreAuthorize 로 철저히 차단한다.
     */
    @PostMapping("/employees/{userId}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public String changeRole(@PathVariable Long userId,
                             @RequestParam Role role,
                             @AuthenticationPrincipal LoginUser loginUser,
                             RedirectAttributes redirectAttributes) {

        Long loginUserId = loginUser == null ? null : loginUser.getUserId();

        try {
            String changedName = employeeService.changeRole(userId, role, loginUserId);
            redirectAttributes.addFlashAttribute("successMessage",
                    changedName + " 님의 권한을 " + role.getDescription() + "(" + role.name() + ") 으로 변경했습니다.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/admin/employees";
    }
}
