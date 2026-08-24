package com.ex.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ex.entity.Warehouse;
import com.ex.entity.WarehouseAllocation;
import com.ex.repository.WarehouseAllocationRepository;
import com.ex.repository.WarehouseRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WarehouseManagementService {

    public record WarehouseSummary(
            Warehouse warehouse,
            long productCount,
            int monthlyPlannedQuantity,
            int targetStockQuantity,
            int currentStockQuantity,
            long lowStockProductCount) {
    }

    private final WarehouseRepository warehouseRepository;
    private final WarehouseAllocationRepository allocationRepository;
    private final WarehouseCapacityPlanningService capacityPlanningService;

    public List<Warehouse> warehouses() {
        return warehouseRepository
                .findAllByActiveTrueOrderByDisplayOrderAsc();
    }

    public List<WarehouseAllocation> allocations() {
        return allocationRepository
                .findAllByOrderByWarehouseDisplayOrderAscProductAnimalTypeAscProductNameAsc()
                .stream()
                .filter(allocation -> allocation.getWarehouse().isActive())
                .filter(allocation -> allocation.getProduct().isActive())
                .toList();
    }

    public Map<String, WarehouseSummary> summaries() {
        Map<String, List<WarehouseAllocation>> grouped =
                allocations().stream().collect(Collectors.groupingBy(
                        allocation -> allocation.getWarehouse().getCode(),
                        LinkedHashMap::new,
                        Collectors.toList()));

        Map<String, WarehouseSummary> result = new LinkedHashMap<>();
        warehouses().forEach(warehouse -> {
            List<WarehouseAllocation> rows =
                    grouped.getOrDefault(warehouse.getCode(), List.of());
            result.put(
                    warehouse.getCode(),
                    new WarehouseSummary(
                            warehouse,
                            rows.size(),
                            rows.stream()
                                    .mapToInt(WarehouseAllocation::getMonthlyPlannedQuantity)
                                    .sum(),
                            rows.stream()
                                    .mapToInt(WarehouseAllocation::getTargetStockQuantity)
                                    .sum(),
                            rows.stream()
                                    .mapToInt(WarehouseAllocation::getCurrentStockQuantity)
                                    .sum(),
                            rows.stream()
                                    .filter(WarehouseAllocation::isLowStock)
                                    .count()));
        });
        return result;
    }

    public int totalMonthlyPlannedQuantity() {
        return allocations().stream()
                .mapToInt(WarehouseAllocation::getMonthlyPlannedQuantity)
                .sum();
    }

    public int totalTargetStockQuantity() {
        return allocations().stream()
                .mapToInt(WarehouseAllocation::getTargetStockQuantity)
                .sum();
    }

    public int totalCurrentStockQuantity() {
        return allocations().stream()
                .mapToInt(WarehouseAllocation::getCurrentStockQuantity)
                .sum();
    }

    public long lowStockAllocationCount() {
        return allocations().stream()
                .filter(WarehouseAllocation::isLowStock)
                .count();
    }

    public List<WarehouseAllocation> lowStockAllocations() {
        return allocations().stream()
                .filter(WarehouseAllocation::isLowStock)
                .toList();
    }

    @Transactional
    public void updateAllocation(
            Long allocationId,
            int monthlyPlannedQuantity,
            int targetStockQuantity) {
        // 월 배치량·권장 보유량은 재계산할 수 없는 관리자 입력값이라
        // 동시 수정 시 한쪽이 조용히 덮이면 안 됩니다. 행을 잠그고 읽습니다.
        WarehouseAllocation allocation = allocationRepository
                .findByIdForUpdate(allocationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "창고 상품 배치 계획을 찾을 수 없습니다."));
        allocation.changePlan(
                monthlyPlannedQuantity,
                targetStockQuantity);
        capacityPlanningService.resizeForWarehouse(
                allocation.getWarehouse().getWarehouseId());
    }

    @Transactional
    public void adjustCurrentStock(
            Long allocationId,
            int currentStockQuantity) {
        // 관리자가 직접 입력한 실사 수량이라 자동 재계산과 경쟁합니다.
        WarehouseAllocation allocation = allocationRepository
                .findByIdForUpdate(allocationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "창고 상품 재고를 찾을 수 없습니다."));
        allocation.adjustCurrentStock(currentStockQuantity);
    }
}
