package com.ex.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.ex.entity.WarehouseAllocation;
import com.ex.repository.WarehouseAllocationRepository;

import lombok.RequiredArgsConstructor;

/** 협력 농장 수요 계산과 실제 창고 재고 조정을 한 번에 실행한다. */
@Service
@RequiredArgsConstructor
public class WarehouseInventoryRebalanceService {

    public record RebalanceReport(
            WarehousePlanSeeder.DemandRecommendationResult recommendation,
            List<String> results,
            List<String> failures) {

        public boolean successful() {
            return failures.isEmpty();
        }
    }

    private final WarehousePlanSeeder warehousePlanSeeder;
    private final WarehouseAllocationRepository allocationRepository;
    private final WmsOperationsService wmsOperationsService;
    private final WarehouseCapacityPlanningService capacityPlanningService;

    public RebalanceReport rebalanceAll(String operatorName) {
        // 계획 계산 전에 LOT와 구역 장부를 먼저 일치시켜 위치가 빠진 재고도
        // 어느 창고의 현재고인지 확정한 뒤 적정 수량 조정에 포함한다.
        wmsOperationsService.synchronizeAll(operatorName);
        var recommendation = warehousePlanSeeder
                .recalculateAllFromFarmDemand();

        List<WarehouseAllocation> allocations = allocationRepository
                .findAllByOrderByWarehouseDisplayOrderAscProductAnimalTypeAscProductNameAsc()
                .stream()
                .filter(allocation -> allocation.getWarehouse().isActive())
                .filter(allocation -> allocation.getProduct().isActive())
                .toList();
        allocations.stream()
                .map(allocation -> allocation.getWarehouse().getWarehouseId())
                .distinct()
                .forEach(capacityPlanningService::resizeForWarehouse);
        List<Long> allocationIds = allocations.stream()
                .map(WarehouseAllocation::getAllocationId)
                .toList();
        List<String> results = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        for (Long allocationId : allocationIds) {
            try {
                var result = wmsOperationsService.balanceAllocationToTarget(
                        allocationId, operatorName);
                if (result.changedQuantity() != 0) {
                    results.add(result.warehouseName() + " · "
                            + result.productName() + " · "
                            + result.actionLabel() + " "
                            + Math.abs(result.changedQuantity()) + "포 ("
                            + result.previousQuantity() + " → "
                            + result.targetQuantity() + ")");
                }
            } catch (RuntimeException exception) {
                failures.add("항목 " + allocationId + " · "
                        + exception.getMessage());
            }
        }
        return new RebalanceReport(
                recommendation,
                List.copyOf(results),
                List.copyOf(failures));
    }
}
