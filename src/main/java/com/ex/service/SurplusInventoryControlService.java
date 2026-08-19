package com.ex.service;

import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ex.repository.RecurringDeliveryRepository;
import com.ex.repository.WarehouseAllocationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 과잉 재고의 추가 입고를 차단하고 권장 보유량을 월 수요 기준으로 보정한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class SurplusInventoryControlService {

    private static final int TARGET_PERCENT = 120;

    private final DemandPlanService demandPlanService;
    private final WarehouseAllocationRepository allocationRepository;
    private final RecurringDeliveryRepository recurringDeliveryRepository;

    @Scheduled(cron = "${feedflow.surplus-control.cron:0 30 0 * * *}")
    @Transactional
    public void controlSurplusInbound() {
        var plan = demandPlanService.plan(java.time.LocalDate.now());
        var deliveries = recurringDeliveryRepository.findAllByOrderByNextDeliveryDateAsc();
        int controlled = 0;
        for (var warehousePlan : plan.warehouses()) {
            for (var row : warehousePlan.rows()) {
                if (row.status() != DemandPlanService.CoverageStatus.SURPLUS) continue;
                List<com.ex.entity.WarehouseAllocation> allocations = allocationRepository
                        .findByWarehouseWarehouseId(warehousePlan.warehouse().getWarehouseId())
                        .stream()
                        .filter(allocation -> normalizeAnimal(
                                allocation.getProduct().getAnimalType()).equals(row.animalType()))
                        .toList();
                if (allocations.isEmpty()) continue;
                int categoryTarget = (row.demandQuantity() * TARGET_PERCENT + 99) / 100;
                int base = categoryTarget / allocations.size();
                int remainder = categoryTarget % allocations.size();
                for (int index = 0; index < allocations.size(); index++) {
                    var allocation = allocations.get(index);
                    int target = base + (index < remainder ? 1 : 0);
                    allocation.changePlan(
                            Math.max(1, allocation.getMonthlyPlannedQuantity()),
                            Math.max(1, target));
                    deliveries.stream()
                            .filter(delivery -> delivery.isActive())
                            .filter(delivery -> delivery.getWarehouse().getWarehouseId().equals(
                                    warehousePlan.warehouse().getWarehouseId()))
                            .filter(delivery -> delivery.getProduct().getProductId().equals(
                                    allocation.getProduct().getProductId()))
                            .forEach(com.ex.entity.RecurringDelivery::pause);
                }
                controlled++;
            }
        }
        if (controlled > 0) {
            log.info("과잉 재고 자동 제어 적용: {}개 창고·축종의 추가 정기입고 중지", controlled);
        }
    }

    private String normalizeAnimal(String value) {
        String animal = value == null ? "기타" : value.trim();
        if (animal.contains("소") || animal.contains("한우")) return "소";
        if (animal.contains("돼지") || animal.contains("양돈")) return "돼지";
        if (animal.contains("조류") || animal.contains("닭") || animal.contains("오리")
                || animal.contains("육계") || animal.contains("산란")) return "조류(닭/오리)";
        return animal;
    }
}
