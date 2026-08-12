package com.ex.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ex.entity.CustomerOrder;
import com.ex.entity.FeedModelPolicy;
import com.ex.entity.Warehouse;
import com.ex.entity.WarehouseAllocation;
import com.ex.repository.CustomerOrderRepository;
import com.ex.repository.FarmCustomerRepository;
import com.ex.repository.FeedModelPolicyRepository;
import com.ex.repository.WarehouseAllocationRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminFeedModelService {

    public record TransferRecommendation(
            Long productId,
            String productName,
            Long sourceWarehouseId,
            String sourceWarehouseName,
            Long destinationWarehouseId,
            String destinationWarehouseName,
            int quantity,
            double distanceKm,
            long estimatedCost,
            String reason) {
    }

    public record ChartPoint(String label, long value, int percent) {}

    public record InventoryImpact(
            String warehouseName,
            int currentStock,
            int targetStock,
            int gap) {
    }

    public record FarmMapPoint(
            String farmName,
            String animalType,
            String warehouseName,
            double latitude,
            double longitude,
            int xPercent,
            int yPercent) {
    }

    public record PresentationAnalytics(
            List<FarmMapPoint> farmMapPoints,
            List<ChartPoint> animalDistribution,
            List<ChartPoint> warehouseDemand,
            List<ChartPoint> monthlyOrders,
            List<InventoryImpact> inventoryImpacts) {
    }

    private final FeedModelPolicyRepository policyRepository;
    private final WarehouseAllocationRepository allocationRepository;
    private final FarmCustomerRepository farmCustomerRepository;
    private final CustomerOrderRepository orderRepository;
    private final WmsStockCoordinator wmsStockCoordinator;

    @Transactional
    public List<FeedModelPolicy> policies() {
        ensurePolicy("소", 3.8);
        ensurePolicy("돼지", .72);
        ensurePolicy("조류(닭/오리)", .05);
        return policyRepository.findAllByOrderByAnimalTypeAsc();
    }

    @Transactional
    public void updatePolicy(
            Long policyId,
            BigDecimal bagsPerHead,
            int preferredFeedWeight,
            int warehouseStockWeight,
            String excludedProductIds) {
        FeedModelPolicy policy = policyRepository.findById(policyId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "추천 모델 기준을 찾을 수 없습니다."));
        policy.update(bagsPerHead, preferredFeedWeight,
                warehouseStockWeight, excludedProductIds);
    }

    @Transactional(readOnly = true)
    public List<TransferRecommendation> transferRecommendations() {
        List<WarehouseAllocation> allocations = allocationRepository
                .findAllByOrderByWarehouseDisplayOrderAscProductAnimalTypeAscProductNameAsc();
        List<TransferRecommendation> result = new ArrayList<>();
        allocations.stream()
                .filter(WarehouseAllocation::isLowStock)
                .forEach(shortage -> allocations.stream()
                        .filter(donor -> donor.getProduct().getProductId()
                                .equals(shortage.getProduct().getProductId()))
                        .filter(donor -> !donor.getWarehouse().getWarehouseId()
                                .equals(shortage.getWarehouse().getWarehouseId()))
                        .filter(donor -> donor.getCurrentStockQuantity()
                                > donor.getTargetStockQuantity())
                        .map(donor -> recommendation(donor, shortage))
                        .min(Comparator
                                .comparingDouble(TransferRecommendation::distanceKm)
                                .thenComparingLong(TransferRecommendation::estimatedCost))
                        .ifPresent(result::add));
        return result.stream()
                .sorted(Comparator.comparingLong(
                        TransferRecommendation::estimatedCost))
                .toList();
    }

    @Transactional
    public void executeTransfer(
            Long productId,
            Long sourceWarehouseId,
            Long destinationWarehouseId,
            int quantity,
            String operatorName) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("이동 수량은 1포대 이상이어야 합니다.");
        }
        WarehouseAllocation source = allocationRepository
                .findByWarehouseWarehouseIdAndProductProductId(
                        sourceWarehouseId, productId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "출발 센터 재고를 찾을 수 없습니다."));
        WarehouseAllocation destination = allocationRepository
                .findByWarehouseWarehouseIdAndProductProductId(
                        destinationWarehouseId, productId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "도착 센터 재고를 찾을 수 없습니다."));
        int excess = source.getCurrentStockQuantity()
                - source.getTargetStockQuantity();
        int shortage = destination.getTargetStockQuantity()
                - destination.getCurrentStockQuantity();
        if (quantity > excess || quantity > shortage) {
            throw new IllegalStateException(
                    "현재 과잉·부족 수량 범위 안에서만 자동 이동할 수 있습니다.");
        }
        wmsStockCoordinator.transferProduct(
                productId, source.getWarehouse(), destination.getWarehouse(),
                quantity, operatorName == null ? "관리자" : operatorName);
        source.adjustCurrentStock(source.getCurrentStockQuantity() - quantity);
        destination.adjustCurrentStock(
                destination.getCurrentStockQuantity() + quantity);
    }

    @Transactional(readOnly = true)
    public PresentationAnalytics analytics() {
        var farms = farmCustomerRepository
                .findAllByOrderByAssignedWarehouseDisplayOrderAscFarmNameAsc();
        Map<String, Long> animals = farms.stream()
                .collect(Collectors.groupingBy(
                        farm -> farm.getAnimalType(),
                        LinkedHashMap::new, Collectors.counting()));
        Map<String, Long> demand = farms.stream()
                .collect(Collectors.groupingBy(
                        farm -> farm.getAssignedWarehouse().getName(),
                        LinkedHashMap::new,
                        Collectors.summingLong(
                                farm -> farm.getMonthlyFeedQuantity())));
        Map<YearMonth, Long> monthly = new LinkedHashMap<>();
        YearMonth current = YearMonth.now();
        for (int index = 5; index >= 0; index--) {
            monthly.put(current.minusMonths(index), 0L);
        }
        orderRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(order -> order.getCreatedAt() != null)
                .filter(order -> order.getStatus()
                        != CustomerOrder.OrderStatus.CANCELLED)
                .forEach(order -> monthly.computeIfPresent(
                        YearMonth.from(order.getCreatedAt()),
                        (key, value) -> value + 1));
        List<WarehouseAllocation> allocations = allocationRepository
                .findAllByOrderByWarehouseDisplayOrderAscProductAnimalTypeAscProductNameAsc();
        Map<String, List<WarehouseAllocation>> byWarehouse = allocations.stream()
                .collect(Collectors.groupingBy(
                        allocation -> allocation.getWarehouse().getName(),
                        LinkedHashMap::new, Collectors.toList()));
        List<InventoryImpact> impacts = byWarehouse.entrySet().stream()
                .map(entry -> new InventoryImpact(
                        entry.getKey(),
                        entry.getValue().stream().mapToInt(
                                WarehouseAllocation::getCurrentStockQuantity).sum(),
                        entry.getValue().stream().mapToInt(
                                WarehouseAllocation::getTargetStockQuantity).sum(),
                        entry.getValue().stream().mapToInt(
                                WarehouseAllocation::getStockGapQuantity).sum()))
                .toList();
        return new PresentationAnalytics(
                spreadMapPoints(farms.stream()
                        .filter(farm -> farm.getLatitude() != null
                                && farm.getLongitude() != null)
                        .map(farm -> new FarmMapPoint(
                                farm.getFarmName(),
                                farm.getAnimalType(),
                                farm.getAssignedWarehouse().getName(),
                                farm.getLatitude(),
                                farm.getLongitude(),
                                clamp((int) Math.round(
                                        (farm.getLongitude() - 125.5)
                                                / 4.5 * 100)),
                                clamp((int) Math.round(
                                        (38.5 - farm.getLatitude())
                                                / 5.5 * 100))))
                        .toList()),
                chartPoints(animals),
                chartPoints(demand),
                chartPoints(monthly.entrySet().stream().collect(
                        Collectors.toMap(
                                entry -> entry.getKey().toString(),
                                Map.Entry::getValue,
                                (left, right) -> left,
                                LinkedHashMap::new))),
                impacts);
    }

    private int clamp(int value) {
        return Math.max(4, Math.min(96, value));
    }

    /** 가까운 좌표의 마커가 완전히 겹치지 않도록 화면 좌표만 조금 분산한다. */
    private List<FarmMapPoint> spreadMapPoints(List<FarmMapPoint> source) {
        List<FarmMapPoint> placed = new ArrayList<>();
        for (FarmMapPoint point : source) {
            int x = point.xPercent();
            int y = point.yPercent();
            int attempt = 0;
            while (hasNearbyMarker(placed, x, y) && attempt < 8) {
                int direction = attempt % 4;
                int distance = 4 + (attempt / 4) * 3;
                x = clamp(point.xPercent()
                        + (direction == 0 ? distance : direction == 1 ? -distance : 0));
                y = clamp(point.yPercent()
                        + (direction == 2 ? distance : direction == 3 ? -distance : 0));
                attempt++;
            }
            placed.add(new FarmMapPoint(point.farmName(), point.animalType(),
                    point.warehouseName(), point.latitude(), point.longitude(), x, y));
        }
        return placed;
    }

    private boolean hasNearbyMarker(List<FarmMapPoint> placed, int x, int y) {
        return placed.stream().anyMatch(existing ->
                Math.abs(existing.xPercent() - x) < 4
                        && Math.abs(existing.yPercent() - y) < 4);
    }

    private TransferRecommendation recommendation(
            WarehouseAllocation donor,
            WarehouseAllocation shortage) {
        int quantity = Math.min(
                donor.getCurrentStockQuantity()
                        - donor.getTargetStockQuantity(),
                shortage.getTargetStockQuantity()
                        - shortage.getCurrentStockQuantity());
        double distance = distanceKm(
                donor.getWarehouse(), shortage.getWarehouse());
        int vehicleCount = Math.max(1, (int) Math.ceil(quantity / 400.0));
        long cost = Math.round(distance * 1_200 * vehicleCount);
        return new TransferRecommendation(
                donor.getProduct().getProductId(),
                donor.getProduct().getName(),
                donor.getWarehouse().getWarehouseId(),
                donor.getWarehouse().getName(),
                shortage.getWarehouse().getWarehouseId(),
                shortage.getWarehouse().getName(),
                quantity,
                distance,
                cost,
                "목표 초과 재고를 부족 센터로 이동 · 400포/차량 가정");
    }

    private List<ChartPoint> chartPoints(Map<String, Long> values) {
        long max = values.values().stream().mapToLong(Long::longValue)
                .max().orElse(0);
        return values.entrySet().stream()
                .map(entry -> new ChartPoint(
                        entry.getKey(), entry.getValue(),
                        max == 0 ? 0 : (int) Math.round(
                                entry.getValue() * 100.0 / max)))
                .toList();
    }

    private void ensurePolicy(String animalType, double bagsPerHead) {
        if (policyRepository.findByAnimalType(animalType).isEmpty()) {
            policyRepository.save(new FeedModelPolicy(
                    animalType, BigDecimal.valueOf(bagsPerHead), 30, 20));
        }
    }

    private double distanceKm(Warehouse from, Warehouse to) {
        if (!from.hasCoordinates() || !to.hasCoordinates()) return 0;
        double latitude = Math.toRadians(to.getLatitude() - from.getLatitude());
        double longitude = Math.toRadians(to.getLongitude() - from.getLongitude());
        double value = Math.sin(latitude / 2) * Math.sin(latitude / 2)
                + Math.cos(Math.toRadians(from.getLatitude()))
                * Math.cos(Math.toRadians(to.getLatitude()))
                * Math.sin(longitude / 2) * Math.sin(longitude / 2);
        value = Math.min(1, Math.max(0, value));
        return 6371.0088 * 2 * Math.atan2(
                Math.sqrt(value), Math.sqrt(1 - value));
    }
}
