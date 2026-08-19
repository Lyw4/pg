package com.ex.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ex.entity.BinPurpose;
import com.ex.entity.WarehouseAllocation;
import com.ex.entity.WarehouseBin;
import com.ex.entity.Product;
import com.ex.repository.BinInventoryRepository;
import com.ex.repository.WarehouseAllocationRepository;
import com.ex.repository.WarehouseBinRepository;

import lombok.RequiredArgsConstructor;

/** 월 예상 수요와 권장 재고를 기준으로 축종별 창고 설계 용량을 조정한다. */
@Service
@RequiredArgsConstructor
public class WarehouseCapacityPlanningService {

    public static final int CAPACITY_BUFFER_PERCENT = 130;
    /** 영양제는 유통기한이 길어 세 달치까지 보관할 수 있도록 별도 설계한다. */
    public static final int SUPPLEMENT_DEMAND_CAPACITY_PERCENT = 300;
    /** 영양제 권장 재고가 늘어나도 두 배까지 보관할 수 있도록 확보한다. */
    public static final int SUPPLEMENT_STOCK_CAPACITY_PERCENT = 200;
    /** 현재 영양제와 보충 예정량을 모두 넣은 뒤에도 50%의 작업 여유를 둔다. */
    public static final int SUPPLEMENT_INBOUND_HEADROOM_PERCENT = 150;

    private final WarehouseAllocationRepository allocationRepository;
    private final WarehouseBinRepository binRepository;
    private final BinInventoryRepository inventoryRepository;
    private final SellableStockQuery sellableStockQuery;

    @Transactional
    public void resizeForWarehouse(Long warehouseId) {
        List<WarehouseAllocation> allocations = allocationRepository
                .findByWarehouseWarehouseId(warehouseId)
                .stream()
                .filter(allocation -> allocation.getProduct().isActive())
                .toList();
        Map<String, Integer> monthlyDemandByZone = allocations.stream()
                .collect(Collectors.groupingBy(
                        allocation -> zoneFor(
                                allocation.getProduct().getAnimalType()),
                        Collectors.summingInt(
                                WarehouseAllocation::getMonthlyPlannedQuantity)));
        Map<String, Integer> recommendedStockByZone = allocations.stream()
                .collect(Collectors.groupingBy(
                        allocation -> zoneFor(
                                allocation.getProduct().getAnimalType()),
                        Collectors.summingInt(
                                WarehouseAllocation::getTargetStockQuantity)));

        Map<String, List<WarehouseBin>> binsByZone = binRepository
                .findByWarehouseWarehouseIdAndActiveTrueOrderByBinCodeAsc(
                        warehouseId)
                .stream()
                .filter(bin -> bin.getPurpose() == BinPurpose.STORAGE)
                .collect(Collectors.groupingBy(bin -> normalizeZone(
                        bin.getZone())));

        java.util.stream.Stream.concat(
                        monthlyDemandByZone.keySet().stream(),
                        recommendedStockByZone.keySet().stream())
                .distinct()
                .filter(zone -> monthlyDemandByZone.getOrDefault(zone, 0) > 0
                        || recommendedStockByZone.getOrDefault(zone, 0) > 0)
                .filter(zone -> binsByZone.getOrDefault(zone, List.of()).isEmpty())
                .findFirst()
                .ifPresent(zone -> {
                    throw new IllegalStateException(
                            "재고 계획이 있는 " + zone + " 보관 구역이 없습니다.");
                });

        binsByZone.forEach((zone, bins) -> {
            int monthlyDemand = monthlyDemandByZone.getOrDefault(zone, 0);
            int recommendedStock = recommendedStockByZone.getOrDefault(zone, 0);
            int currentQuantity = bins.stream()
                    .mapToInt(this::quantityInBin)
                    .sum();
            int replenishmentShortage = allocations.stream()
                    .filter(allocation -> zone.equals(zoneFor(
                            allocation.getProduct().getAnimalType())))
                    .mapToInt(allocation -> Math.max(0,
                            allocation.getTargetStockQuantity()
                                    - sellableStockQuery.sellableAtWarehouse(
                                            warehouseId,
                                            allocation.getProduct().getProductId())))
                    .sum();
            if (monthlyDemand <= 0 && recommendedStock <= 0
                    && currentQuantity <= 0 && replenishmentShortage <= 0) {
                return;
            }

            int demandBuffer = "COLD".equals(zone)
                    ? SUPPLEMENT_DEMAND_CAPACITY_PERCENT
                    : CAPACITY_BUFFER_PERCENT;
            int stockBuffer = "COLD".equals(zone)
                    ? SUPPLEMENT_STOCK_CAPACITY_PERCENT
                    : CAPACITY_BUFFER_PERCENT;
            int plannedCapacity = Math.max(
                    ceilPercent(monthlyDemand, demandBuffer),
                    ceilPercent(
                            recommendedStock,
                            stockBuffer));
            int replenishmentCapacity = currentQuantity
                    + replenishmentShortage;
            if ("COLD".equals(zone)) {
                // 이미 적재된 영양제를 유지하면서 부족분을 모두 넣고도
                // 후속 입출고 작업을 위한 50% 여유 공간을 남긴다.
                replenishmentCapacity = ceilPercent(
                        replenishmentCapacity,
                        SUPPLEMENT_INBOUND_HEADROOM_PERCENT);
            }
            int requiredCapacity = Math.max(
                    plannedCapacity, replenishmentCapacity);
            int effectivePerBin = ceilDivide(
                    requiredCapacity, bins.size());

            bins.forEach(bin -> {
                int binRequired = Math.max(
                        effectivePerBin, quantityInBin(bin));
                int floorCapacity = ceilDivide(
                        binRequired,
                        WarehouseBin.VERTICAL_STACKING_LEVELS);
                bin.changeCapacity(floorCapacity);
            });
        });
    }

    /** 특정 상품의 즉시 입고 물량이 전부 출고 가능한 보관 구역에 들어가도록 확장한다. */
    @Transactional
    public void ensureProductInboundCapacity(
            Long warehouseId, Product product, int inboundQuantity) {
        if (inboundQuantity <= 0) return;
        String zone = zoneFor(product.getAnimalType());
        List<WarehouseBin> bins = binRepository
                .findByWarehouseWarehouseIdAndActiveTrueOrderByBinCodeAsc(warehouseId)
                .stream()
                .filter(bin -> bin.getPurpose() == BinPurpose.STORAGE)
                .filter(bin -> zone.equals(normalizeZone(bin.getZone())))
                .toList();
        if (bins.isEmpty()) {
            throw new IllegalStateException(product.getName()
                    + "을 보관할 활성 창고 구역이 없습니다.");
        }
        int requiredCapacity = bins.stream().mapToInt(this::quantityInBin).sum()
                + inboundQuantity;
        int effectivePerBin = ceilDivide(requiredCapacity, bins.size());
        bins.forEach(bin -> bin.changeCapacity(ceilDivide(
                Math.max(effectivePerBin, quantityInBin(bin)),
                WarehouseBin.VERTICAL_STACKING_LEVELS)));
    }

    private int quantityInBin(WarehouseBin bin) {
        return inventoryRepository.findByBinBinId(bin.getBinId())
                .stream()
                .mapToInt(inventory -> inventory.getQuantity())
                .sum();
    }

    private int ceilPercent(int quantity, int percent) {
        long result = ((long) quantity * percent + 99) / 100;
        if (result > Integer.MAX_VALUE) {
            throw new IllegalStateException("계획 창고 용량이 허용 범위를 초과했습니다.");
        }
        return (int) result;
    }

    private int ceilDivide(int value, int divisor) {
        return Math.max(1, (value + divisor - 1) / divisor);
    }

    private String zoneFor(String animalType) {
        return WmsZonePolicy.zoneFor(animalType);
    }

    private String normalizeZone(String zone) {
        return zone == null ? "" : zone.trim().toUpperCase();
    }
}
