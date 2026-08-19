package com.ex.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.ex.entity.CustomerOrder;
import com.ex.entity.FarmCustomer;
import com.ex.entity.FarmCustomer.CustomerStatus;
import com.ex.entity.Product;
import com.ex.entity.ProductLot;
import com.ex.repository.CustomerOrderRepository;
import com.ex.repository.FarmCustomerRepository;
import com.ex.repository.ProductLotRepository;
import com.ex.repository.ProductRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FarmDeliveryAutomationService {

    public enum PreviewStatus {
        READY("처리 가능", "success"),
        COMPLETED("처리 완료", "secondary"),
        REQUESTED("입고 요청 완료", "primary"),
        BLOCKED("확인 필요", "danger");

        private final String label;
        private final String badgeClass;

        PreviewStatus(String label, String badgeClass) {
            this.label = label;
            this.badgeClass = badgeClass;
        }

        public String getLabel() { return label; }
        public String getBadgeClass() { return badgeClass; }
    }

    public record PreviewRow(
            Long farmCustomerId,
            Long warehouseId,
            Long productId,
            String farmName,
            String warehouseName,
            String productName,
            int quantity,
            int availableQuantity,
            PreviewStatus status,
            String message,
            Long orderId) {

        public int shortageQuantity() {
            return Math.max(0, quantity - availableQuantity);
        }

        public boolean canReplenish() {
            return productId != null && shortageQuantity() > 0;
        }
    }

    public record Preview(
            LocalDate referenceDate,
            List<PreviewRow> rows,
            long readyCount,
            long completedCount,
            long blockedCount) {

        /** 화면의 조치 목록에는 아직 해결되지 않은 항목만 표시한다. */
        public List<PreviewRow> actionRows() {
            return rows.stream()
                    .filter(row -> row.status() == PreviewStatus.BLOCKED)
                    .toList();
        }
    }

    public record ExecutionResult(
            LocalDate referenceDate,
            int createdCount,
            int skippedCount,
            int failedCount,
            List<String> messages) {
    }

    private final FarmCustomerRepository farmCustomerRepository;
    private final ProductRepository productRepository;
    private final ProductLotRepository productLotRepository;
    private final CustomerOrderRepository orderRepository;
    private final SellableStockQuery sellableStockQuery;
    private final DistributionService distributionService;
    private final ShipmentService shipmentService;
    private final RecurringDeliveryService recurringDeliveryService;
    private final InventoryService inventoryService;
    private final WarehouseCapacityPlanningService capacityPlanningService;
    private final com.ex.repository.RecurringDeliveryRepository recurringDeliveryRepository;
    private final PlatformTransactionManager transactionManager;

    @Transactional(readOnly = true)
    public Preview preview(LocalDate referenceDate) {
        LocalDate date = requireDate(referenceDate);
        List<PreviewRow> rows = dueFarms(date).stream()
                .map(farm -> previewFarm(farm, date))
                .toList();
        return new Preview(
                date,
                rows,
                rows.stream().filter(row -> row.status() == PreviewStatus.READY).count(),
                rows.stream().filter(row -> row.status() == PreviewStatus.COMPLETED
                        || row.status() == PreviewStatus.REQUESTED).count(),
                rows.stream().filter(row -> row.status() == PreviewStatus.BLOCKED).count());
    }

    public ExecutionResult execute(LocalDate referenceDate, String trigger) {
        LocalDate date = requireDate(referenceDate);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        List<Long> farmIds = dueFarms(date).stream()
                .map(FarmCustomer::getFarmCustomerId)
                .toList();
        List<String> messages = new ArrayList<>();
        int created = 0;
        int skipped = 0;
        int failed = 0;

        for (Long farmId : farmIds) {
            try {
                String result = transaction.execute(status ->
                        executeFarm(farmId, date, trigger));
                if (result != null && result.startsWith("SKIPPED:")) {
                    skipped++;
                    messages.add(result.substring("SKIPPED:".length()));
                } else {
                    created++;
                    messages.add(result);
                }
            } catch (RuntimeException exception) {
                failed++;
                messages.add(exception.getMessage());
            }
        }
        return new ExecutionResult(date, created, skipped, failed, messages);
    }

    private String executeFarm(Long farmId, LocalDate date, String trigger) {
        FarmCustomer farm = farmCustomerRepository.findByIdForUpdate(farmId)
                .orElseThrow(() -> new IllegalArgumentException("농장 고객사를 찾을 수 없습니다."));
        if (!isDue(farm, date)) {
            return "SKIPPED:" + farm.getFarmName() + " · 실행 대상이 아닙니다.";
        }
        var existing = orderRepository
                .findByFarmCustomerFarmCustomerIdAndScheduledDeliveryDate(farmId, date);
        if (existing.isPresent()) {
            return "SKIPPED:" + farm.getFarmName() + " · 이미 처리된 정기 납품입니다.";
        }

        Product product = productRepository.findByName(farm.getPreferredFeed())
                .filter(Product::isActive)
                .orElseThrow(() -> new IllegalStateException(
                        farm.getFarmName() + " · 선호 제품을 찾을 수 없습니다: " + farm.getPreferredFeed()));
        int quantity = farm.getMonthlyFeedQuantity();
        ProductLot lot = chooseLot(product, farm, quantity)
                .orElseThrow(() -> new IllegalStateException(
                        farm.getFarmName() + " · " + product.getName()
                                + " 가용 재고가 " + quantity + "포 미만입니다."));

        Long orderId = distributionService.createDemoOrder(
                farmId,
                lot.getLotId(),
                quantity,
                BigDecimal.ZERO,
                farm.getRepresentativeName(),
                farm.getPhone(),
                farm.getPostalCode(),
                farm.getAddress(),
                null,
                null,
                farm.getLatitude(),
                farm.getLongitude(),
                "정기 납품 자동 생성 (기준일 " + date + ")");
        CustomerOrder order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalStateException("생성된 주문을 찾을 수 없습니다."));
        order.markScheduledDelivery(date, normalizeTrigger(trigger));
        orderRepository.flush();
        shipmentService.create(orderId, "자동 배차", "농장 정기 납품 " + date);
        return farm.getFarmName() + " · 주문 #" + orderId + " 및 출고 지시 생성";
    }

    private PreviewRow previewFarm(FarmCustomer farm, LocalDate date) {
        var existing = orderRepository
                .findByFarmCustomerFarmCustomerIdAndScheduledDeliveryDate(
                        farm.getFarmCustomerId(), date);
        if (existing.isPresent()) {
            return row(farm, farm.getPreferredFeed(), 0, PreviewStatus.COMPLETED,
                    "주문 #" + existing.get().getOrderId() + " 생성 완료",
                    existing.get().getOrderId());
        }
        var product = productRepository.findByName(farm.getPreferredFeed())
                .filter(Product::isActive);
        if (product.isEmpty()) {
            return row(farm, farm.getPreferredFeed(), 0, PreviewStatus.BLOCKED,
                    "운영 중인 선호 제품을 찾을 수 없습니다.", null);
        }
        int available = sellableStockQuery.sellableAtWarehouse(
                farm.getAssignedWarehouse().getWarehouseId(),
                product.get().getProductId());
        if (farm.getMonthlyFeedQuantity() <= 0) {
            return row(farm, product.get().getName(), available, PreviewStatus.BLOCKED,
                    "월 예상 사료량을 먼저 입력해 주세요.", null);
        }
        if (available < farm.getMonthlyFeedQuantity()
                && recurringDeliveryRepository.existsByNotes(
                        inboundRequestKey(farm.getFarmCustomerId(), date))) {
            return row(farm, product.get().getName(), available, PreviewStatus.REQUESTED,
                    "부족분 정기입고 요청이 생성되었습니다.", null);
        }
        PreviewStatus status = available >= farm.getMonthlyFeedQuantity()
                ? PreviewStatus.READY : PreviewStatus.BLOCKED;
        String message = status == PreviewStatus.READY
                ? "주문·재고 예약·출고 지시를 생성할 수 있습니다."
                : "가용 재고가 " + (farm.getMonthlyFeedQuantity() - available) + "포 부족합니다.";
        return row(farm, product.get().getName(), available, status, message, null);
    }

    private PreviewRow row(
            FarmCustomer farm, String productName, int available,
            PreviewStatus status, String message, Long orderId) {
        return new PreviewRow(
                farm.getFarmCustomerId(),
                farm.getAssignedWarehouse().getWarehouseId(),
                productName == null ? null : productRepository.findByName(productName)
                        .map(Product::getProductId).orElse(null),
                farm.getFarmName(),
                farm.getAssignedWarehouse().getName(), productName,
                farm.getMonthlyFeedQuantity(), available, status, message, orderId);
    }

    @Transactional
    public String createInboundRequest(Long farmCustomerId, LocalDate referenceDate) {
        ReplenishmentTarget target = replenishmentTarget(farmCustomerId, referenceDate);
        String requestKey = inboundRequestKey(farmCustomerId, referenceDate);
        if (recurringDeliveryRepository.existsByNotes(requestKey)) {
            return target.farm().getFarmName() + " · 이미 생성된 입고 요청입니다.";
        }
        recurringDeliveryService.create(
                target.farm().getAssignedWarehouse().getWarehouseId(),
                target.product().getProductId(),
                target.shortageQuantity(),
                Math.min(28, referenceDate.getDayOfMonth()),
                requestKey);
        return target.farm().getFarmName() + " · " + target.product().getName()
                + " " + target.shortageQuantity() + "포의 정기입고 요청을 생성했습니다.";
    }

    @Transactional
    public String receiveInboundImmediately(Long farmCustomerId, LocalDate referenceDate) {
        ReplenishmentTarget target = replenishmentTarget(farmCustomerId, referenceDate);
        LocalDate manufacturedDate = LocalDate.now();
        capacityPlanningService.ensureProductInboundCapacity(
                target.farm().getAssignedWarehouse().getWarehouseId(),
                target.product(), target.shortageQuantity());
        inventoryService.receive(
                target.product().getProductId(),
                inventoryService.createAutomaticLotNo(
                        target.product().getProductId(), manufacturedDate),
                manufacturedDate,
                manufacturedDate.plusMonths(target.product().getEffectiveShelfLifeMonths()),
                target.shortageQuantity(),
                "정기 납품 부족분 즉시 입고 · " + target.farm().getFarmName(),
                target.farm().getAssignedWarehouse());
        return target.farm().getFarmName() + " · " + target.product().getName()
                + " " + target.shortageQuantity() + "포를 즉시 입고했습니다.";
    }

    private ReplenishmentTarget replenishmentTarget(
            Long farmCustomerId, LocalDate referenceDate) {
        FarmCustomer farm = farmCustomerRepository.findById(farmCustomerId)
                .orElseThrow(() -> new IllegalArgumentException("농장 고객사를 찾을 수 없습니다."));
        if (!isDue(farm, requireDate(referenceDate))) {
            throw new IllegalStateException("선택한 기준일의 정기 납품 대상 농장이 아닙니다.");
        }
        Product product = productRepository.findByName(farm.getPreferredFeed())
                .filter(Product::isActive)
                .orElseThrow(() -> new IllegalStateException("운영 중인 선호 제품을 찾을 수 없습니다."));
        int available = sellableStockQuery.sellableAtWarehouse(
                farm.getAssignedWarehouse().getWarehouseId(), product.getProductId());
        int shortage = farm.getMonthlyFeedQuantity() - available;
        if (shortage <= 0) {
            throw new IllegalStateException("이미 정기 납품에 필요한 가용 재고를 충족합니다.");
        }
        return new ReplenishmentTarget(farm, product, shortage);
    }

    private record ReplenishmentTarget(
            FarmCustomer farm, Product product, int shortageQuantity) {
    }

    private String inboundRequestKey(Long farmCustomerId, LocalDate referenceDate) {
        return "FARM-INBOUND:" + farmCustomerId + ":" + referenceDate;
    }

    private java.util.Optional<ProductLot> chooseLot(
            Product product, FarmCustomer farm, int quantity) {
        List<ProductLot> lots = productLotRepository
                .findByProductProductIdOrderByExpirationDateAsc(product.getProductId());
        Map<Long, Integer> available = sellableStockQuery.sellablePerLot(
                lots.stream().map(ProductLot::getLotId).toList(),
                farm.getAssignedWarehouse().getWarehouseId());
        return lots.stream()
                .filter(lot -> !lot.getExpirationDate().isBefore(
                        LocalDate.now().plusDays(SellableStockQuery.MINIMUM_SELLABLE_DAYS)))
                .filter(lot -> available.getOrDefault(lot.getLotId(), 0) >= quantity)
                .findFirst();
    }

    private List<FarmCustomer> dueFarms(LocalDate date) {
        return farmCustomerRepository
                .findAllByOrderByAssignedWarehouseDisplayOrderAscFarmNameAsc()
                .stream()
                .filter(farm -> isDue(farm, date))
                .toList();
    }

    private boolean isDue(FarmCustomer farm, LocalDate date) {
        return farm.getStatus() == CustomerStatus.ACTIVE
                && farm.getRecurringDeliveryDay() == date.getDayOfMonth();
    }

    private LocalDate requireDate(LocalDate date) {
        if (date == null) throw new IllegalArgumentException("기준일을 선택해 주세요.");
        return date;
    }

    private String normalizeTrigger(String trigger) {
        return "SCHEDULED".equalsIgnoreCase(trigger) ? "SCHEDULED" : "MANUAL";
    }
}
