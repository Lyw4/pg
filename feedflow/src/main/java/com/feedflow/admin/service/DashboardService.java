package com.feedflow.admin.service;

import com.feedflow.admin.dto.DailySalesRow;
import com.feedflow.admin.dto.ExpiringLotDto;
import com.feedflow.admin.dto.SafetyStockAlertDto;
import com.feedflow.admin.dto.SalesChartDto;
import com.feedflow.admin.dto.SalesSummaryDto;
import com.feedflow.admin.dto.TodayTaskDto;
import com.feedflow.domain.OrderStatus;
import com.feedflow.repository.OrderRepository;
import com.feedflow.repository.ProductLotRepository;
import com.feedflow.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 관리자 대시보드 조회 전용 서비스.
 * - 모든 통계는 Repository 의 JPQL 집계(count / sum / group by)를 사용한다.
 * - Entity 는 밖으로 내보내지 않고 DTO 로 변환해서 반환한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private static final DateTimeFormatter CHART_LABEL_FORMATTER = DateTimeFormatter.ofPattern("MM/dd");

    private final ProductRepository productRepository;
    private final ProductLotRepository productLotRepository;
    private final OrderRepository orderRepository;

    @Value("${feedflow.dashboard.expiration-alert-days:30}")
    private int expirationAlertDays;

    @Value("${feedflow.dashboard.chart-default-days:7}")
    private int chartDefaultDays;

    /* ------------------------------------------------------------------
     * 공통 노출 (STAFF, ADMIN)
     * ------------------------------------------------------------------ */

    /** 안전재고 알림: totalStock < safetyStock 인 상품 목록 */
    public List<SafetyStockAlertDto> getSafetyStockAlerts() {
        return productRepository.findSafetyStockAlerts().stream()
                .map(SafetyStockAlertDto::from)
                .toList();
    }

    /** 유통기한 임박 알림: 오늘부터 N일(기본 30일) 이내에 만료되는 로트 목록 */
    public List<ExpiringLotDto> getExpiringLots() {
        LocalDate today = LocalDate.now();
        LocalDate limit = today.plusDays(expirationAlertDays);

        return productLotRepository.findExpiringLots(today, limit).stream()
                .map(lot -> ExpiringLotDto.of(lot, today))
                .toList();
    }

    /** 오늘의 할 일: 신규 주문 건수 / 출고 대기 건수 (+ 알림 건수) */
    public TodayTaskDto getTodayTask() {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.atTime(LocalTime.MAX);
        LocalDate expirationLimit = today.plusDays(expirationAlertDays);

        return TodayTaskDto.builder()
                .newOrderCount(orderRepository.countByStatusAndCreatedAtBetween(OrderStatus.PAID, start, end))
                .readyToShipCount(orderRepository.countByStatus(OrderStatus.READY))
                .safetyStockAlertCount(productRepository.countSafetyStockAlerts())
                .expiringLotCount(productLotRepository.countExpiringLots(today, expirationLimit))
                .build();
    }

    /* ------------------------------------------------------------------
     * 책임자 전용 (ADMIN Only)
     * ------------------------------------------------------------------ */

    /** 매출 통계: 오늘 / 이번 달 총 매출액 */
    public SalesSummaryDto getSalesSummary() {
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime todayEnd = today.atTime(LocalTime.MAX);
        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();

        long todaySales = nullSafe(
                orderRepository.sumSalesBetween(OrderStatus.CANCELED, todayStart, todayEnd));
        long monthSales = nullSafe(
                orderRepository.sumSalesBetween(OrderStatus.CANCELED, monthStart, todayEnd));

        return SalesSummaryDto.builder()
                .todaySales(todaySales)
                .monthSales(monthSales)
                .todayOrderCount(orderRepository.countByCreatedAtBetween(todayStart, todayEnd))
                .build();
    }

    /**
     * 최근 N일 일별 매출 추이 (Chart.js 용).
     * 매출이 없는 날짜도 0 으로 채워서 그래프가 끊기지 않게 한다.
     */
    public SalesChartDto getSalesChart(Integer days) {
        int targetDays = (days == null || days <= 0) ? chartDefaultDays : Math.min(days, 90);

        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(targetDays - 1L);

        List<DailySalesRow> rows =
                orderRepository.findDailySales(OrderStatus.CANCELED, from.atStartOfDay());

        Map<LocalDate, Long> salesByDate = new HashMap<>();
        for (DailySalesRow row : rows) {
            salesByDate.put(row.saleDate(), row.amount());
        }

        List<String> labels = new ArrayList<>(targetDays);
        List<Long> sales = new ArrayList<>(targetDays);
        long total = 0L;

        for (int i = 0; i < targetDays; i++) {
            LocalDate date = from.plusDays(i);
            long amount = salesByDate.getOrDefault(date, 0L);

            labels.add(date.format(CHART_LABEL_FORMATTER));
            sales.add(amount);
            total += amount;
        }

        return new SalesChartDto(labels, sales, total, targetDays);
    }

    private long nullSafe(Long value) {
        return value == null ? 0L : value;
    }
}
