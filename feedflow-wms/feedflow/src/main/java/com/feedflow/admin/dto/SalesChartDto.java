package com.feedflow.admin.dto;

import java.util.List;

/**
 * Chart.js 연동용 응답 (ADMIN 전용 REST API).
 * HTML 렌더링 시 넘기지 않고 /api/admin/chart 에서 JSON 으로만 제공한다.
 *
 * @param labels     x축 라벨 (MM/dd)
 * @param sales      일별 매출액
 * @param totalSales 조회 구간 총 매출액
 * @param days       조회 일수
 */
public record SalesChartDto(
        List<String> labels,
        List<Long> sales,
        long totalSales,
        int days
) {
}
