package com.feedflow.admin.controller;

import com.feedflow.admin.dto.SalesChartDto;
import com.feedflow.admin.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 JSON API.
 * 대시보드 차트는 HTML 렌더링 시 데이터를 넘기지 않고, 이 엔드포인트를 fetch() 로 호출한다.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminRestController {

    private final DashboardService dashboardService;

    /**
     * 최근 N일 일별 매출 추이 (기본 7일). 책임자 전용.
     * GET /api/admin/chart?days=7
     */
    @GetMapping("/chart")
    @PreAuthorize("hasRole('ADMIN')")
    public SalesChartDto salesChart(@RequestParam(name = "days", required = false) Integer days) {
        return dashboardService.getSalesChart(days);
    }
}
