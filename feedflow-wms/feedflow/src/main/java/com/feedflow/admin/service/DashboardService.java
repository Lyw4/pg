package com.feedflow.admin.service;

import com.feedflow.common.util.Numbers;
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
 * 관리자 대시보드 조회 전용 서비스 (경고 시스템).
 *
 * <h3>경고 규칙</h3>
 * <ul>
 *     <li><b>재고 부족</b> : 사용 중인 품목 중 totalStock &lt; safetyStock 인 품목</li>
 *     <li><b>유통기한</b> : 오늘 + N일(기본 30일) 이내에 만료되는 로트 <b>및 이미 만료된 로트</b></li>
 * </ul>
 *
 * <p>모든 통계는 Repository 의 JPQL 집계(count / sum / group by)를 사용하고,
 * Entity 는 밖으로 내보내지 않고 DTO 로 변환해서 반환한다.
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final DateTimeFormatter CHART_LABEL_FORMATTER = DateTimeFormatter.ofPattern("MM/dd");
    private static final int MAX_CHART_DAYS = 90;

    /** 매출 집계에서 제외하는 주문 상태 (취소 주문은 매출이 아니다) */
    private static final OrderStatus EXCLUDED_FROM_SALES = OrderStatus.CANCELED;

    private final ProductRepository productRepository;
    private final ProductLotRepository productLotRepository;
    private final OrderRepository orderRepository;

    /** 유통기한 임박 기준일수 */
    private final int expirationAlertDays;

    /** 매출 추이 차트 기본 조회 일수 */
    private final int chartDefaultDays;

    /**
     * 설정값을 생성자로 주입한다.
     * (필드 주입 대신 생성자 주입을 사용해 단위 테스트에서 그대로 생성할 수 있게 한다)
     */
    public DashboardService(ProductRepository productRepository,
                            ProductLotRepository productLotRepository,
                            OrderRepository orderRepository,
                            @Value("${feedflow.dashboard.expiration-alert-days:30}") int expirationAlertDays,
                            @Value("${feedflow.dashboard.chart-default-days:7}") int chartDefaultDays) {
        this.productRepository = productRepository;
        this.productLotRepository = productLotRepository;
        this.orderRepository = orderRepository;
        this.expirationAlertDays = expirationAlertDays;
        this.chartDefaultDays = chartDefaultDays;
    }

    /* ------------------------------------------------------------------
     * 경고 - 공통 노출 (STAFF, ADMIN)
     * ------------------------------------------------------------------ */

    /** 재고 부족 경고 : 사용 중인 품목 중 totalStock < safetyStock */
    public List<SafetyStockAlertDto> getSafetyStockAlerts() {
        return productRepository.findSafetyStockAlerts().stream()
                .map(SafetyStockAlertDto::from)
                .toList();
    }

    /**
     * 유통기한 경고 : 오늘부터 N일(기본 30일) 이내에 만료되는 로트 + 이미 만료된 로트.
     * 만료된 로트가 가장 위험하므로 유통기한 오름차순(만료된 것이 먼저)으로 반환된다.
     */
    public List<ExpiringLotDto> getExpiringLots() {
        LocalDate today = LocalDate.now();

        return productLotRepository.findExpiringLots(expirationLimit(today)).stream()
                .map(lot -> ExpiringLotDto.of(lot, today))
                .toList();
    }

    /** 오늘의 할 일 + 경고 건수 요약 */
    public TodayTaskDto getTodayTask() {
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.atTime(LocalTime.MAX);

        return TodayTaskDto.builder()
                .newOrderCount(orderRepository.countByStatusAndCreatedAtBetween(OrderStatus.PAID, start, end))
                .readyToShipCount(orderRepository.countByStatus(OrderStatus.READY))
                .safetyStockAlertCount(productRepository.countSafetyStockAlerts())
                .expiringLotCount(productLotRepository.countExpiringLots(expirationLimit(today)))
                .expiredLotCount(productLotRepository.countExpiredLots(today))
                .build();
    }

    /* ------------------------------------------------------------------
     * 매출 - 책임자 전용 (ADMIN Only)
     * ------------------------------------------------------------------ */

    /** 매출 통계: 오늘 / 이번 달 총 매출액 */
    public SalesSummaryDto getSalesSummary() {
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime todayEnd = today.atTime(LocalTime.MAX);
        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();

        long todaySales = Numbers.orZero(
                orderRepository.sumSalesBetween(EXCLUDED_FROM_SALES, todayStart, todayEnd));
        long monthSales = Numbers.orZero(
                orderRepository.sumSalesBetween(EXCLUDED_FROM_SALES, monthStart, todayEnd));

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
        int targetDays = (days == null || days <= 0) ? chartDefaultDays : Math.min(days, MAX_CHART_DAYS);

        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(targetDays - 1L);

        List<DailySalesRow> rows =
                orderRepository.findDailySales(EXCLUDED_FROM_SALES, from.atStartOfDay());

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

    /* ------------------------------------------------------------------
     * 내부 헬퍼
     * ------------------------------------------------------------------ */

    /** 유통기한 경고 상한일 (오늘 + 기준일수) */
    private LocalDate expirationLimit(LocalDate today) {
        return today.plusDays(expirationAlertDays);
    }
}
