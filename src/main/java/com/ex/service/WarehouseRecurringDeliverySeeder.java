package com.ex.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ex.entity.RecurringDelivery;
import com.ex.entity.WarehouseAllocation;
import com.ex.repository.RecurringDeliveryRepository;
import com.ex.repository.WarehouseAllocationRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WarehouseRecurringDeliverySeeder {

    private static final Map<String, int[]> DELIVERY_DAYS = Map.of(
            "W01", new int[] {1, 15},
            "W02", new int[] {3, 17},
            "W03", new int[] {5, 19},
            "W04", new int[] {8, 22},
            "W05", new int[] {10, 24});

    private final WarehouseAllocationRepository allocationRepository;
    private final RecurringDeliveryRepository recurringDeliveryRepository;

    @Transactional
    public void seed() {
        /*
         * 창고 도입 전에 만들어진 협력사 단위 일정은 다른 테이블에서
         * 참조하지 않으므로 제거하고 창고 기준 일정으로 교체합니다.
         */
        recurringDeliveryRepository.deleteByWarehouseIsNull();

        allocationRepository
                .findAllByOrderByWarehouseDisplayOrderAscProductAnimalTypeAscProductNameAsc()
                .stream()
                .filter(allocation -> allocation.getWarehouse().isActive())
                .filter(allocation -> allocation.getProduct().isActive())
                .forEach(this::seedAllocationSchedules);
    }

    private void seedAllocationSchedules(WarehouseAllocation allocation) {
        int[] days = DELIVERY_DAYS.get(allocation.getWarehouse().getCode());
        if (days == null) {
            return;
        }

        createIfMissing(
                allocation,
                1,
                days[0],
                allocation.getFirstDeliveryQuantity());
        createIfMissing(
                allocation,
                2,
                days[1],
                allocation.getSecondDeliveryQuantity());
    }

    private void createIfMissing(
            WarehouseAllocation allocation,
            int sequence,
            int deliveryDay,
            int quantity) {
        if (recurringDeliveryRepository
                .existsByWarehouseWarehouseIdAndProductProductIdAndDeliveryDay(
                        allocation.getWarehouse().getWarehouseId(),
                        allocation.getProduct().getProductId(),
                        deliveryDay)) {
            return;
        }

        recurringDeliveryRepository.save(new RecurringDelivery(
                allocation.getWarehouse(),
                allocation.getProduct().getManufacturer(),
                allocation.getProduct(),
                quantity,
                deliveryDay,
                sequence,
                nextDeliveryDate(deliveryDay),
                "창고 월 배치 계획 "
                        + sequence
                        + "차 자동 일정"));
    }

    private LocalDate nextDeliveryDate(int deliveryDay) {
        LocalDate today = LocalDate.now();
        YearMonth targetMonth = YearMonth.from(today);
        LocalDate candidate = targetMonth.atDay(deliveryDay);
        if (candidate.isBefore(today)) {
            candidate = targetMonth.plusMonths(1).atDay(deliveryDay);
        }
        return candidate;
    }
}
