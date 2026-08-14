package com.ex.service;

import com.ex.entity.CustomerOrder;
import com.ex.entity.Product;
import com.ex.entity.ProductLot;
import com.ex.entity.StockLog;
import com.ex.entity.WarehouseAllocation;
import com.ex.repository.CustomerOrderRepository;
import com.ex.repository.ProductLotRepository;
import com.ex.repository.ProductRepository;
import com.ex.repository.StockLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashMap;

/**
 * 관리자 통합 대시보드 집계 서비스.
 *
 * <p>팀원의 feedflow-wms-module에 있는 DashboardService와
 * CenterDashboardService의 화면 계약을 루트 프로젝트의 통합 주문·LOT·5거점
 * 모델에 맞게 옮긴 어댑터다. 별도 WMS 데이터베이스를 만들지 않고 실제 판매,
 * 유통, 재고 데이터 한 벌을 집계한다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardService {

    private static final int EXPIRING_DAYS = 30;
    private static final int ACTIVITY_DAYS = 7;

    private final ProductRepository productRepository;
    private final ProductLotRepository productLotRepository;
    private final CustomerOrderRepository orderRepository;
    private final StockLogRepository stockLogRepository;
    private final WarehouseManagementService warehouseManagementService;

    public DashboardSnapshot getDashboard() {
        LocalDate today = LocalDate.now();
        List<Product> products = productRepository
                .findAllByActiveTrueOrderByProductIdAsc();
        List<ProductLot> activeLots = productLotRepository
                .findAllByOrderByExpirationDateAsc()
                .stream()
                .filter(lot -> lot.getProduct().isActive())
                .filter(lot -> lot.getLotQuantity() > 0)
                .filter(lot -> lot.getExpirationDate() != null)
                .toList();
        List<CustomerOrder> orders = orderRepository
                .findAllByOrderByCreatedAtDesc();

        List<LowStockAlert> lowStockAlerts = warehouseManagementService
                .lowStockAllocations().stream()
                .map(LowStockAlert::from)
                .sorted(Comparator.comparingInt(LowStockAlert::shortage)
                        .reversed())
                .toList();

        List<ExpiringLotAlert> expiringLots = activeLots.stream()
                .filter(lot -> lot.getExpirationDate().isBefore(today))
                .map(lot -> ExpiringLotAlert.from(lot, today))
                .toList();

        long newOrderCount = orders.stream()
                .filter(order -> order.getCreatedAt() != null)
                .filter(order -> order.getCreatedAt()
                        .toLocalDate().equals(today))
                .filter(order -> order.getStatus()
                        != CustomerOrder.OrderStatus.CANCELLED)
                .count();
        long readyToShipCount = orders.stream()
                .filter(order -> order.getStatus()
                        == CustomerOrder.OrderStatus.PAID
                        || order.getStatus()
                        == CustomerOrder.OrderStatus.PREPARING)
                .count();
        long expiredLotCount = expiringLots.stream()
                .filter(ExpiringLotAlert::expired)
                .count();

        TodayTask todayTask = new TodayTask(
                newOrderCount,
                readyToShipCount,
                lowStockAlerts.size(),
                expiringLots.size(),
                expiredLotCount);

        return new DashboardSnapshot(
                today,
                todayTask,
                createNetworkOverview(),
                createSalesOverview(orders, today),
                lowStockAlerts,
                expiringLots);
    }

    public AdminMetrics getAdminMetrics() {
        LocalDate today = LocalDate.now();
        List<CustomerOrder> orders = orderRepository.findAllByOrderByCreatedAtDesc();
        List<Product> products = productRepository.findAllByActiveTrueOrderByProductIdAsc();
        List<ProductLot> lots = productLotRepository.findAllByOrderByExpirationDateAsc();
        long paid = orders.stream().filter(o -> o.getStatus() == CustomerOrder.OrderStatus.PAID
                || o.getStatus() == CustomerOrder.OrderStatus.PREPARING
                || o.getStatus() == CustomerOrder.OrderStatus.SHIPPING
                || o.getStatus() == CustomerOrder.OrderStatus.DELIVERED).count();
        long shipping = orders.stream().filter(o -> o.getStatus() == CustomerOrder.OrderStatus.SHIPPING).count();
        long cancelled = orders.stream().filter(o -> o.getStatus() == CustomerOrder.OrderStatus.CANCELLED).count();
        long todayOrders = orders.stream().filter(o -> o.getCreatedAt() != null
                && o.getCreatedAt().toLocalDate().equals(today)).count();
        long revenue = orders.stream().filter(o -> o.getStatus() != CustomerOrder.OrderStatus.CANCELLED)
                .map(CustomerOrder::getFinalPrice).filter(java.util.Objects::nonNull)
                .mapToLong(java.math.BigDecimal::longValue).sum();
        long lowStockLots = warehouseManagementService
                .lowStockAllocationCount();
        long expiringLots = lots.stream()
                .filter(l -> l.getProduct().isActive())
                .filter(l -> l.getLotQuantity() > 0)
                .filter(l -> l.getExpirationDate() != null
                        && l.getExpirationDate().isBefore(today))
                .count();
        long soldOutProducts = products.stream().filter(p -> p.getTotalStock() <= 0).count();
        Map<LocalDate, Long> daily = new LinkedHashMap<>();
        for (int i = ACTIVITY_DAYS - 1; i >= 0; i--) daily.put(today.minusDays(i), 0L);
        orders.stream().filter(o -> o.getStatus() != CustomerOrder.OrderStatus.CANCELLED
                && o.getCreatedAt() != null).forEach(o -> {
            LocalDate date = o.getCreatedAt().toLocalDate();
            if (daily.containsKey(date)) daily.computeIfPresent(date, (k, v) -> v +
                    (o.getFinalPrice() == null ? 0L : o.getFinalPrice().longValue()));
        });
        List<DailyMetric> dailyMetrics = new ArrayList<>();
        long max = daily.values().stream().mapToLong(Long::longValue).max().orElse(0L);
        daily.forEach((date, amount) -> dailyMetrics.add(new DailyMetric(date, amount,
                max == 0 ? 0 : (int) Math.round(amount * 100.0 / max))));
        return new AdminMetrics(orders.size(), todayOrders, paid, shipping, cancelled,
                revenue, products.size(), soldOutProducts, lowStockLots, expiringLots, dailyMetrics);
    }

    private SalesOverview createSalesOverview(
            List<CustomerOrder> orders,
            LocalDate today) {
        List<CustomerOrder> completedSales = orders.stream()
                .filter(order -> order.getCreatedAt() != null)
                .filter(order -> order.getStatus()
                        != CustomerOrder.OrderStatus.CANCELLED)
                .toList();
        long todayOrderCount = completedSales.stream()
                .filter(order -> order.getCreatedAt()
                        .toLocalDate().equals(today))
                .count();
        long todayRevenue = revenueBetween(
                completedSales,
                today,
                today);
        long monthRevenue = revenueBetween(
                completedSales,
                today.withDayOfMonth(1),
                today);
        long recentRevenue = revenueBetween(
                completedSales,
                today.minusDays(ACTIVITY_DAYS - 1L),
                today);
        return new SalesOverview(
                todayRevenue,
                monthRevenue,
                recentRevenue,
                todayOrderCount,
                todayOrderCount == 0
                        ? 0
                        : todayRevenue / todayOrderCount,
                ACTIVITY_DAYS,
                today.withDayOfMonth(1));
    }

    private long revenueBetween(
            List<CustomerOrder> orders,
            LocalDate startDate,
            LocalDate endDate) {
        return orders.stream()
                .filter(order -> {
                    LocalDate orderDate = order.getCreatedAt().toLocalDate();
                    return !orderDate.isBefore(startDate)
                            && !orderDate.isAfter(endDate);
                })
                .map(CustomerOrder::getFinalPrice)
                .filter(java.util.Objects::nonNull)
                .mapToLong(java.math.BigDecimal::longValue)
                .sum();
    }

    private NetworkOverview createNetworkOverview() {
        Map<String, WarehouseManagementService.WarehouseSummary> summaries =
                warehouseManagementService.summaries();
        int nationwideStock = summaries.values().stream()
                .mapToInt(WarehouseManagementService.WarehouseSummary
                        ::currentStockQuantity)
                .sum();

        List<WarehouseCard> centers = summaries.values().stream()
                .map(summary -> WarehouseCard.from(
                        summary,
                        nationwideStock))
                .sorted(Comparator.comparingInt(card ->
                        card.warehouse().getDisplayOrder()))
                .toList();

        int targetStock = centers.stream()
                .mapToInt(WarehouseCard::targetStock)
                .sum();
        int monthlyPlan = centers.stream()
                .mapToInt(WarehouseCard::monthlyPlan)
                .sum();
        ActivitySummary activity = activitySummary();

        return new NetworkOverview(
                centers,
                nationwideStock,
                targetStock,
                monthlyPlan,
                percentage(nationwideStock, targetStock),
                activity.inboundQuantity(),
                activity.outboundQuantity(),
                centers.stream()
                        .max(Comparator.comparingInt(
                                WarehouseCard::currentStock))
                        .orElse(null),
                centers.stream()
                        .filter(center ->
                                center.lowStockProductCount() > 0)
                        .max(Comparator.comparingLong(
                                WarehouseCard::lowStockProductCount))
                        .orElse(null),
                ACTIVITY_DAYS);
    }

    private ActivitySummary activitySummary() {
        LocalDateTime since = LocalDate.now()
                .minusDays(ACTIVITY_DAYS - 1L)
                .atStartOfDay();
        int inbound = 0;
        int outbound = 0;

        for (StockLog log : stockLogRepository.findAll()) {
            if (log.getCreatedAt() == null
                    || log.getCreatedAt().isBefore(since)
                    || log.isCancelled()) {
                continue;
            }
            if (log.getChangeType() == StockLog.ChangeType.INBOUND) {
                inbound += Math.abs(log.getChangedQty());
            } else if (log.getChangeType()
                    == StockLog.ChangeType.OUTBOUND) {
                outbound += Math.abs(log.getChangedQty());
            }
        }
        return new ActivitySummary(inbound, outbound);
    }

    private static int percentage(int value, int base) {
        if (base <= 0) {
            return value > 0 ? 100 : 0;
        }
        return (int) Math.round(value * 100.0 / base);
    }

    public record DashboardSnapshot(
            LocalDate today,
            TodayTask todayTask,
            NetworkOverview network,
            SalesOverview sales,
            List<LowStockAlert> lowStockAlerts,
            List<ExpiringLotAlert> expiringLots) {
    }

    public record SalesOverview(
            long todayRevenue,
            long monthRevenue,
            long recentRevenue,
            long todayOrderCount,
            long todayAverageOrderAmount,
            int activityDays,
            LocalDate monthStart) {
    }

    public record AdminMetrics(long totalOrders, long todayOrders, long paidOrders,
            long shippingOrders, long cancelledOrders, long totalRevenue,
            long totalProducts, long soldOutProducts, long lowStockLots,
            long expiringLots, List<DailyMetric> dailySales) {}

    public record DailyMetric(LocalDate date, long revenue, int percent) {}

    public record TodayTask(
            long newOrderCount,
            long readyToShipCount,
            long safetyStockAlertCount,
            long expiringLotCount,
            long expiredLotCount) {

        public boolean hasAlert() {
            return safetyStockAlertCount > 0
                    || expiringLotCount > 0;
        }
    }

    public record LowStockAlert(
            String warehouseName,
            Long productId,
            String name,
            String animalType,
            int totalStock,
            int safetyStock,
            int shortage,
            int stockRate) {

        private static LowStockAlert from(WarehouseAllocation allocation) {
            Product product = allocation.getProduct();
            int shortage = Math.max(
                    allocation.getTargetStockQuantity()
                            - allocation.getCurrentStockQuantity(),
                    0);
            return new LowStockAlert(
                    allocation.getWarehouse().getName(),
                    product.getProductId(),
                    product.getName(),
                    product.getAnimalType(),
                    allocation.getCurrentStockQuantity(),
                    allocation.getTargetStockQuantity(),
                    shortage,
                    Math.min(percentage(
                            allocation.getCurrentStockQuantity(),
                            allocation.getTargetStockQuantity()), 100));
        }
    }

    public record ExpiringLotAlert(
            Long lotId,
            String lotNo,
            String productName,
            LocalDate expirationDate,
            int quantity,
            long remainingDays,
            boolean expired,
            String dDayLabel,
            String badgeClass) {

        private static ExpiringLotAlert from(
                ProductLot lot,
                LocalDate today) {
            long days = ChronoUnit.DAYS.between(
                    today,
                    lot.getExpirationDate());
            return new ExpiringLotAlert(
                    lot.getLotId(),
                    lot.getLotNo(),
                    lot.getProduct().getName(),
                    lot.getExpirationDate(),
                    lot.getLotQuantity(),
                    days,
                    days < 0,
                    days < 0
                            ? "만료 " + Math.abs(days) + "일 경과"
                            : days == 0 ? "오늘 만료" : "D-" + days,
                    days < 0 ? "bg-dark"
                            : days <= 7 ? "bg-danger"
                            : "bg-warning text-dark");
        }
    }

    public record WarehouseCard(
            com.ex.entity.Warehouse warehouse,
            long productCount,
            int monthlyPlan,
            int targetStock,
            int currentStock,
            long lowStockProductCount,
            int stockRate,
            int sharePercent,
            String statusLabel,
            String statusClass) {

        private static WarehouseCard from(
                WarehouseManagementService.WarehouseSummary summary,
                int nationwideStock) {
            int rate = percentage(
                    summary.currentStockQuantity(),
                    summary.targetStockQuantity());
            String statusLabel = summary.lowStockProductCount() > 0
                    ? "보충 필요"
                    : rate > 120 ? "과잉" : "적정";
            String statusClass = summary.lowStockProductCount() > 0
                    ? "bg-danger"
                    : rate > 120
                            ? "bg-warning text-dark"
                            : "bg-success";
            return new WarehouseCard(
                    summary.warehouse(),
                    summary.productCount(),
                    summary.monthlyPlannedQuantity(),
                    summary.targetStockQuantity(),
                    summary.currentStockQuantity(),
                    summary.lowStockProductCount(),
                    rate,
                    percentage(
                            summary.currentStockQuantity(),
                            nationwideStock),
                    statusLabel,
                    statusClass);
        }

        public int cappedStockRate() {
            return Math.min(stockRate, 100);
        }
    }

    public record NetworkOverview(
            List<WarehouseCard> centers,
            int totalStock,
            int targetStock,
            int monthlyPlan,
            int stockRate,
            int inboundQuantity,
            int outboundQuantity,
            WarehouseCard busiestCenter,
            WarehouseCard mostUrgentCenter,
            int activityDays) {

        public int centerCount() {
            return centers.size();
        }

        public int stockGap() {
            return totalStock - targetStock;
        }

        /**
         * 센터별 재고 비중을 CSS conic-gradient가 바로 사용할 수 있는
         * 조각 목록으로 변환합니다. 외부 차트 라이브러리 없이도 같은
         * 데이터를 원형·도넛형으로 보여주기 위한 화면 전용 값입니다.
         */
        public String distributionGradient() {
            String[] colors = {
                    "#328b61", "#2f6fb0", "#b7791f", "#c05621", "#6b46c1"
            };
            int start = 0;
            StringBuilder gradient = new StringBuilder();
            for (int index = 0; index < centers.size(); index++) {
                WarehouseCard center = centers.get(index);
                int end = Math.min(100, start + Math.max(0, center.sharePercent()));
                if (gradient.length() > 0) {
                    gradient.append(", ");
                }
                gradient.append(colors[index % colors.length])
                        .append(' ')
                        .append(start)
                        .append('%')
                        .append(' ')
                        .append(end)
                        .append('%');
                start = end;
            }
            if (gradient.length() == 0) {
                return "#dfe6ed 0% 100%";
            }
            if (start < 100) {
                gradient.append(", #e8edf2 ")
                        .append(start)
                        .append("% 100%");
            }
            return gradient.toString();
        }
    }

    private record ActivitySummary(
            int inboundQuantity,
            int outboundQuantity) {
    }
}
