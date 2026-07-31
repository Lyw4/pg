package com.feedflow.admin.controller;

import com.feedflow.admin.dto.CenterStockChartDto;
import com.feedflow.admin.dto.SalesChartDto;
import com.feedflow.admin.service.CenterDashboardService;
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
    private final CenterDashboardService centerDashboardService;

    /**
     * 최근 N일 일별 매출 추이 (기본 7일). 책임자 전용.
     * GET /api/admin/chart?days=7
     */
    @GetMapping("/chart")
    @PreAuthorize("hasRole('ADMIN')")
    public SalesChartDto salesChart(@RequestParam(name = "days", required = false) Integer days) {
        return dashboardService.getSalesChart(days);
    }

    /**
     * 센터별 재고 분포 (도넛 차트).
     * GET /api/admin/center-stock
     * <p>
     * <b>매출 차트와 달리 ADMIN 으로 제한하지 않는다.</b> 이 데이터는 매출이 아니라 재고이고,
     * 창고 담당자(STAFF)가 "다른 센터에 재고가 있는지" 를 모르면 불필요한 발주를 낸다.
     * ({@code /admin/**} 는 SecurityConfig 에서 이미 STAFF · ADMIN 으로 제한된다)
     */
    @GetMapping("/center-stock")
    public CenterStockChartDto centerStock() {
        return centerDashboardService.getStockChart();
    }
}
