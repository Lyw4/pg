package com.feedflow.admin.service;

import com.feedflow.admin.dto.DailySalesRow;
import com.feedflow.admin.dto.ExpiringLotDto;
import com.feedflow.admin.dto.SafetyStockAlertDto;
import com.feedflow.admin.dto.SalesChartDto;
import com.feedflow.admin.dto.SalesSummaryDto;
import com.feedflow.admin.dto.TodayTaskDto;
import com.feedflow.domain.OrderStatus;
import com.feedflow.domain.Product;
import com.feedflow.domain.ProductLot;
import com.feedflow.repository.OrderRepository;
import com.feedflow.repository.ProductLotRepository;
import com.feedflow.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * 대시보드 경고 시스템 서비스 단위 테스트.
 * <p>
 * Repository 를 Mock 으로 대체하여 아래를 검증한다.
 * <ul>
 *     <li>경고 조회 시 Repository 에 <b>정확한 기준일(오늘 + 30일)</b>이 전달되는지</li>
 *     <li>Entity → DTO 변환 (부족 수량 / D-Day / 만료 여부 / 뱃지)</li>
 *     <li>매출 집계 null 처리 및 차트의 빈 날짜 0 채우기</li>
 * </ul>
 * 실제 JPQL 필터링(31일 남은 로트 제외 등)은
 * {@code DashboardAlertRepositoryTest} 에서 H2 로 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DashboardService 경고 시스템 단위 테스트")
class DashboardServiceTest {

    private static final int EXPIRATION_ALERT_DAYS = 30;
    private static final int CHART_DEFAULT_DAYS = 7;

    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductLotRepository productLotRepository;
    @Mock
    private OrderRepository orderRepository;

    private DashboardService dashboardService;

    private final LocalDate today = LocalDate.now();

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(
                productRepository, productLotRepository, orderRepository,
                EXPIRATION_ALERT_DAYS, CHART_DEFAULT_DAYS);
    }

    /* ==================================================================
     * 재고 부족 경고
     * ================================================================== */

    @Test
    @DisplayName("[재고부족] 안전재고 미달 품목을 부족 수량과 함께 반환한다")
    void getSafetyStockAlerts_mapsShortage() {
        // given : 재고 40 / 안전재고 50 → 10 부족
        given(productRepository.findSafetyStockAlerts())
                .willReturn(List.of(product("FD-CT-001", 40, 50), product("FD-CK-001", 80, 120)));

        // when
        List<SafetyStockAlertDto> alerts = dashboardService.getSafetyStockAlerts();

        // then
        assertThat(alerts).hasSize(2);

        SafetyStockAlertDto first = alerts.get(0);
        assertThat(first.getProductCode()).isEqualTo("FD-CT-001");
        assertThat(first.getTotalStock()).isEqualTo(40);
        assertThat(first.getSafetyStock()).isEqualTo(50);
        assertThat(first.getShortage()).isEqualTo(10);
        assertThat(first.getStockRate()).isEqualTo(80);      // 40 / 50 = 80%

        SafetyStockAlertDto second = alerts.get(1);
        assertThat(second.getShortage()).isEqualTo(40);       // 120 - 80
        assertThat(second.getStockRate()).isEqualTo(67);      // 80 / 120 = 66.67 → 67
    }

    /* ==================================================================
     * 유통기한 경고
     * ================================================================== */

    @Test
    @DisplayName("[유통기한] Repository 에 '오늘 + 30일' 을 기준일로 전달한다")
    void getExpiringLots_passesTodayPlus30AsLimit() {
        // given
        given(productLotRepository.findExpiringLots(any(LocalDate.class))).willReturn(List.of());

        // when
        dashboardService.getExpiringLots();

        // then
        ArgumentCaptor<LocalDate> captor = ArgumentCaptor.forClass(LocalDate.class);
        verify(productLotRepository).findExpiringLots(captor.capture());
        assertThat(captor.getValue()).isEqualTo(today.plusDays(30));
    }

    @Test
    @DisplayName("[유통기한] 임박 로트와 이미 만료된 로트의 D-Day / 만료 여부 / 뱃지를 정확히 변환한다")
    void getExpiringLots_mapsDDayAndExpiredFlag() {
        // given : 만료(-5일), 임박(D-3), 오늘 만료(D-0), 여유(D-25)
        given(productLotRepository.findExpiringLots(any(LocalDate.class))).willReturn(List.of(
                lot("LOT-EXPIRED", today.minusDays(5)),
                lot("LOT-D3", today.plusDays(3)),
                lot("LOT-TODAY", today),
                lot("LOT-D25", today.plusDays(25))));

        // when
        List<ExpiringLotDto> lots = dashboardService.getExpiringLots();

        // then
        assertThat(lots).hasSize(4);

        ExpiringLotDto expired = lots.get(0);
        assertThat(expired.getRemainingDays()).isEqualTo(-5L);
        assertThat(expired.isExpired()).isTrue();
        assertThat(expired.getDDayLabel()).isEqualTo("만료 5일 경과");
        assertThat(expired.getBadgeClass()).isEqualTo("bg-dark");

        ExpiringLotDto d3 = lots.get(1);
        assertThat(d3.getRemainingDays()).isEqualTo(3L);
        assertThat(d3.isExpired()).isFalse();
        assertThat(d3.getDDayLabel()).isEqualTo("D-3");
        assertThat(d3.getBadgeClass()).isEqualTo("bg-danger");    // 7일 이내는 위험

        ExpiringLotDto expiringToday = lots.get(2);
        assertThat(expiringToday.getRemainingDays()).isZero();
        assertThat(expiringToday.isExpired())
                .as("만료일 당일은 아직 만료가 아니다")
                .isFalse();
        assertThat(expiringToday.getDDayLabel()).isEqualTo("오늘 만료");

        ExpiringLotDto d25 = lots.get(3);
        assertThat(d25.getBadgeClass()).isEqualTo("bg-warning text-dark");
    }

    /* ==================================================================
     * 오늘의 할 일 / 경고 건수
     * ================================================================== */

    @Test
    @DisplayName("[요약] 신규주문·출고대기·재고부족·유통기한·만료 건수를 각각 집계한다")
    void getTodayTask_aggregatesCounts() {
        // given
        given(orderRepository.countByStatusAndCreatedAtBetween(
                eq(OrderStatus.PAID), any(LocalDateTime.class), any(LocalDateTime.class))).willReturn(2L);
        given(orderRepository.countByStatus(OrderStatus.READY)).willReturn(2L);
        given(productRepository.countSafetyStockAlerts()).willReturn(3L);
        given(productLotRepository.countExpiringLots(today.plusDays(30))).willReturn(4L);
        given(productLotRepository.countExpiredLots(today)).willReturn(1L);

        // when
        TodayTaskDto task = dashboardService.getTodayTask();

        // then
        assertThat(task.getNewOrderCount()).isEqualTo(2L);
        assertThat(task.getReadyToShipCount()).isEqualTo(2L);
        assertThat(task.getSafetyStockAlertCount()).isEqualTo(3L);
        assertThat(task.getExpiringLotCount()).isEqualTo(4L);
        assertThat(task.getExpiredLotCount()).isEqualTo(1L);
        assertThat(task.hasAlert()).isTrue();
        assertThat(task.isAllClear()).isFalse();
    }

    @Test
    @DisplayName("[요약] 경고가 하나도 없으면 hasAlert 가 false 다")
    void getTodayTask_noAlert() {
        given(orderRepository.countByStatusAndCreatedAtBetween(
                eq(OrderStatus.PAID), any(LocalDateTime.class), any(LocalDateTime.class))).willReturn(0L);
        given(orderRepository.countByStatus(OrderStatus.READY)).willReturn(0L);
        given(productRepository.countSafetyStockAlerts()).willReturn(0L);
        given(productLotRepository.countExpiringLots(any(LocalDate.class))).willReturn(0L);
        given(productLotRepository.countExpiredLots(any(LocalDate.class))).willReturn(0L);

        TodayTaskDto task = dashboardService.getTodayTask();

        assertThat(task.hasAlert()).isFalse();
        assertThat(task.isAllClear()).isTrue();
    }

    /* ==================================================================
     * 매출 (ADMIN 전용)
     * ================================================================== */

    @Test
    @DisplayName("[매출] 취소 주문을 제외한 오늘/이번 달 매출을 집계하고 평균 주문액을 계산한다")
    void getSalesSummary_excludesCanceledAndCalculatesAverage() {
        // given : 오늘 1,100,000원 / 3건, 이번 달 6,810,000원
        given(orderRepository.sumSalesBetween(eq(OrderStatus.CANCELED),
                any(LocalDateTime.class), any(LocalDateTime.class)))
                .willReturn(1_100_000L, 6_810_000L);
        given(orderRepository.countByCreatedAtBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .willReturn(3L);

        // when
        SalesSummaryDto summary = dashboardService.getSalesSummary();

        // then
        assertThat(summary.getTodaySales()).isEqualTo(1_100_000L);
        assertThat(summary.getMonthSales()).isEqualTo(6_810_000L);
        assertThat(summary.getTodayOrderCount()).isEqualTo(3L);
        assertThat(summary.getTodayAverageOrderAmount()).isEqualTo(366_666L);   // 1,100,000 / 3
    }

    @Test
    @DisplayName("[매출] 매출이 없어 SUM 결과가 null 이면 0 으로 처리한다")
    void getSalesSummary_nullSumBecomesZero() {
        given(orderRepository.sumSalesBetween(eq(OrderStatus.CANCELED),
                any(LocalDateTime.class), any(LocalDateTime.class)))
                .willReturn(null);
        given(orderRepository.countByCreatedAtBetween(any(LocalDateTime.class), any(LocalDateTime.class)))
                .willReturn(0L);

        SalesSummaryDto summary = dashboardService.getSalesSummary();

        assertThat(summary.getTodaySales()).isZero();
        assertThat(summary.getMonthSales()).isZero();
        assertThat(summary.getTodayAverageOrderAmount())
                .as("0 으로 나누지 않아야 한다")
                .isZero();
    }

    @Test
    @DisplayName("[차트] 최근 7일을 조회하고 매출이 없는 날짜는 0 으로 채운다")
    void getSalesChart_fillsMissingDaysWithZero() {
        // given : 7일 중 오늘과 3일 전에만 매출이 있다
        LocalDate d0 = today;
        LocalDate d3 = today.minusDays(3);

        given(orderRepository.findDailySales(eq(OrderStatus.CANCELED), any(LocalDateTime.class)))
                .willReturn(List.of(
                        new DailySalesRow(d3.getYear(), d3.getMonthValue(), d3.getDayOfMonth(), 500_000L),
                        new DailySalesRow(d0.getYear(), d0.getMonthValue(), d0.getDayOfMonth(), 300_000L)));

        // when : days 를 지정하지 않으면 기본값(7일)
        SalesChartDto chart = dashboardService.getSalesChart(null);

        // then
        assertThat(chart.days()).isEqualTo(7);
        assertThat(chart.labels()).hasSize(7);
        assertThat(chart.sales()).hasSize(7);
        assertThat(chart.totalSales()).isEqualTo(800_000L);

        // 마지막 요소가 오늘, 인덱스 3 이 3일 전
        assertThat(chart.sales().get(6)).isEqualTo(300_000L);
        assertThat(chart.sales().get(3)).isEqualTo(500_000L);
        assertThat(chart.sales().get(0)).isZero();
        assertThat(chart.sales().get(1)).isZero();

        // 조회 시작일은 '오늘 - 6일' 자정
        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(orderRepository).findDailySales(eq(OrderStatus.CANCELED), captor.capture());
        assertThat(captor.getValue()).isEqualTo(today.minusDays(6).atStartOfDay());
    }

    @Test
    @DisplayName("[차트] 조회 일수가 0 이하이면 기본값을, 90 초과면 90 으로 제한한다")
    void getSalesChart_normalizesDays() {
        given(orderRepository.findDailySales(eq(OrderStatus.CANCELED), any(LocalDateTime.class)))
                .willReturn(List.of());

        assertThat(dashboardService.getSalesChart(0).days()).isEqualTo(7);
        assertThat(dashboardService.getSalesChart(-5).days()).isEqualTo(7);
        assertThat(dashboardService.getSalesChart(365).days()).isEqualTo(90);
        assertThat(dashboardService.getSalesChart(14).days()).isEqualTo(14);
    }

    /* ==================================================================
     * 픽스처
     * ================================================================== */

    private Product product(String productCode, int totalStock, int safetyStock) {
        return Product.builder()
                .productId(1L)
                .productCode(productCode)
                .name("테스트 사료")
                .animalType("소")
                .weightKg(25)
                .price(32000L)
                .totalStock(totalStock)
                .safetyStock(safetyStock)
                .shelfLifeDays(180)
                .active(true)
                .build();
    }

    private ProductLot lot(String lotNo, LocalDate expirationDate) {
        return ProductLot.builder()
                .lotId(1L)
                .product(product("FD-CT-001", 40, 50))
                .lotNo(lotNo)
                .manufacturedDate(expirationDate.minusDays(180))
                .expirationDate(expirationDate)
                .lotQuantity(20)
                .build();
    }
}
