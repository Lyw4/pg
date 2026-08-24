package com.ex.service;

import com.ex.entity.BinInventory;
import com.ex.entity.BinPurpose;
import com.ex.entity.FarmCustomer;
import com.ex.entity.FarmCustomer.CustomerStatus;
import com.ex.entity.Warehouse;
import com.ex.repository.BinInventoryRepository;
import com.ex.repository.FarmCustomerRepository;
import com.ex.repository.WarehouseRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 농장 월 수요와 창고의 실제 출고 가능 재고를 창고·축종별로 대조한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DemandPlanService {

    /** 농장 월 사료 사용량 대비 영양제 예상 사용 비율. */
    public static final int SUPPLEMENT_DEMAND_PERCENT = 5;

    public enum CoverageStatus {
        SHORTAGE("부족", "bg-danger"),
        TIGHT("빠듯", "bg-warning text-dark"),
        ADEQUATE("적정", "bg-success"),
        SURPLUS("과다", "bg-info text-dark"),
        NO_DEMAND("수요 없음", "bg-light text-dark");

        private final String label;
        private final String badgeClass;

        CoverageStatus(String label, String badgeClass) {
            this.label = label;
            this.badgeClass = badgeClass;
        }

        public String getLabel() { return label; }
        public String getBadgeClass() { return badgeClass; }
        public boolean isNeedsAction() { return this == SHORTAGE || this == TIGHT; }

        static CoverageStatus of(int demand, int rate) {
            if (demand <= 0) return NO_DEMAND;
            if (rate >= 300) return SURPLUS;
            if (rate >= 120) return ADEQUATE;
            if (rate >= 100) return TIGHT;
            return SHORTAGE;
        }
    }

    public record CoverageRow(
            String animalType,
            int demandQuantity,
            int supplyQuantity,
            int coverageRate,
            int shortageQuantity,
            int barWidth,
            CoverageStatus status) {
        static CoverageRow of(String animalType, int demand, int supply) {
            int rate = demand <= 0 ? 0 : (int) Math.round(supply * 100.0 / demand);
            return new CoverageRow(
                    animalType,
                    demand,
                    supply,
                    rate,
                    Math.max(0, demand - supply),
                    Math.min(100, rate),
                    CoverageStatus.of(demand, rate));
        }

        public boolean needsAction() { return status.isNeedsAction(); }
        public boolean hasDemand() { return demandQuantity > 0; }

        /** 월 수요의 120%를 확보하기 위해 추가로 입고할 권장 수량이다. */
        public int recommendedInboundQuantity() {
            if (!needsAction() || demandQuantity <= 0) return 0;
            return Math.max(0, (int) Math.ceil(demandQuantity * 1.2) - supplyQuantity);
        }
    }

    public record WarehousePlan(
            Warehouse warehouse,
            List<CoverageRow> rows,
            int totalDemand,
            int totalSupply,
            int actionCount,
            int shortageQuantity,
            int coverageRate) {
        static WarehousePlan of(Warehouse warehouse, List<CoverageRow> rows) {
            int demand = rows.stream().mapToInt(CoverageRow::demandQuantity).sum();
            int supply = rows.stream().mapToInt(CoverageRow::supplyQuantity).sum();
            int action = (int) rows.stream().filter(CoverageRow::needsAction).count();
            int shortage = rows.stream().mapToInt(CoverageRow::shortageQuantity).sum();
            int rate = demand <= 0 ? 0 : (int) Math.round(supply * 100.0 / demand);
            return new WarehousePlan(warehouse, rows, demand, supply, action, shortage, rate);
        }

        public boolean needsAction() { return actionCount > 0; }
    }

    public record DeliveryFarm(
            Long farmCustomerId,
            String farmName,
            String warehouseName,
            String animalType,
            int quantity) {
        static DeliveryFarm of(FarmCustomer farm) {
            return new DeliveryFarm(
                    farm.getFarmCustomerId(),
                    farm.getFarmName(),
                    farm.getAssignedWarehouse().getName(),
                    normalizeAnimal(farm.getAnimalType()),
                    farm.getMonthlyFeedQuantity());
        }
    }

    public record DeliverySchedule(
            int day,
            long farmCount,
            int amount,
            List<DeliveryFarm> farms) {
        public String label() { return "매월 " + day + "일"; }
    }

    public record DemandPlan(
            List<WarehousePlan> warehouses,
            List<DeliverySchedule> schedule,
            int totalDemand,
            int totalSupply,
            int totalCoverageRate,
            int warehousesNeedingAction,
            int animalsNeedingAction,
            int totalShortage,
            DeliverySchedule peakDeliveryDay) {
        public boolean hasAction() { return animalsNeedingAction > 0; }
    }

    private record CoverageKey(Long warehouseId, String animalType) {
    }

    private final WarehouseRepository warehouseRepository;
    private final FarmCustomerRepository farmCustomerRepository;
    private final BinInventoryRepository binInventoryRepository;
    private final SellableStockQuery sellableStockQuery;

    public DemandPlan plan(LocalDate today) {
        List<Warehouse> warehouses = warehouseRepository
                .findAllByActiveTrueOrderByDisplayOrderAsc();
        List<FarmCustomer> farms = farmCustomerRepository
                .findAllByOrderByAssignedWarehouseDisplayOrderAscFarmNameAsc()
                .stream()
                .filter(DemandPlanService::isOperationalFarm)
                .toList();
        List<BinInventory> inventories = binInventoryRepository.findAllByOrderByBinBinCodeAsc();

        Map<CoverageKey, Integer> demand = new LinkedHashMap<>();
        farms.stream()
                .forEach(farm -> {
                    Long warehouseId = farm.getAssignedWarehouse().getWarehouseId();
                    demand.merge(
                            new CoverageKey(warehouseId,
                                    normalizeAnimal(farm.getAnimalType())),
                            farm.getMonthlyFeedQuantity(),
                            Integer::sum);
                    demand.merge(
                            new CoverageKey(warehouseId, "영양제"),
                            supplementDemand(farm.getMonthlyFeedQuantity()),
                            Integer::sum);
                });

        Map<CoverageKey, Integer> supply = new LinkedHashMap<>();
        Map<String, Integer> availableByWarehouseProduct = sellableStockQuery
                .sellableByWarehouseAndProductIds(inventories.stream()
                        .map(inventory -> inventory.getLot().getProduct().getProductId())
                        .distinct().toList());
        inventories.stream()
                .filter(inventory -> inventory.getQuantity() > 0)
                .filter(inventory -> inventory.getBin().isActive())
                .filter(inventory -> inventory.getBin().getPurpose() == BinPurpose.STORAGE
                        || inventory.getBin().getPurpose() == BinPurpose.SHIPPING)
                .filter(inventory -> inventory.getLot().getExpirationDate() == null
                        || !inventory.getLot().getExpirationDate().isBefore(
                                today.plusDays(SellableStockQuery.MINIMUM_SELLABLE_DAYS)))
                .collect(java.util.stream.Collectors.toMap(
                        inventory -> sellableStockQuery.stockKey(
                                inventory.getBin().getWarehouse().getWarehouseId(),
                                inventory.getLot().getProduct().getProductId()),
                        inventory -> inventory,
                        (left, right) -> left,
                        LinkedHashMap::new))
                .values().stream()
                .forEach(inventory -> supply.merge(
                        new CoverageKey(
                                inventory.getBin().getWarehouse().getWarehouseId(),
                                normalizeAnimal(inventory.getLot().getProduct().getAnimalType())),
                        availableByWarehouseProduct.getOrDefault(
                                sellableStockQuery.stockKey(
                                        inventory.getBin().getWarehouse().getWarehouseId(),
                                        inventory.getLot().getProduct().getProductId()),
                                0),
                        Integer::sum));

        List<WarehousePlan> warehousePlans = new ArrayList<>();
        for (Warehouse warehouse : warehouses) {
            Set<String> animals = new LinkedHashSet<>();
            for (CoverageKey key : demand.keySet()) {
                if (key.warehouseId().equals(warehouse.getWarehouseId())) animals.add(key.animalType());
            }
            for (CoverageKey key : supply.keySet()) {
                if (key.warehouseId().equals(warehouse.getWarehouseId())) animals.add(key.animalType());
            }
            List<CoverageRow> rows = animals.stream()
                    .sorted(Comparator.comparingInt(this::animalOrder))
                    .map(animal -> {
                        CoverageKey key = new CoverageKey(warehouse.getWarehouseId(), animal);
                        return CoverageRow.of(
                                animal,
                                demand.getOrDefault(key, 0),
                                supply.getOrDefault(key, 0));
                    })
                    .toList();
            warehousePlans.add(WarehousePlan.of(warehouse, rows));
        }

        List<DeliverySchedule> schedule = farms.stream()
                .filter(farm -> farm.getRecurringDeliveryDay() > 0)
                .collect(java.util.stream.Collectors.groupingBy(
                        FarmCustomer::getRecurringDeliveryDay,
                        java.util.TreeMap::new,
                        java.util.stream.Collectors.toList()))
                .entrySet().stream()
                .map(entry -> new DeliverySchedule(
                        entry.getKey(),
                        entry.getValue().size(),
                        entry.getValue().stream()
                                .mapToInt(FarmCustomer::getMonthlyFeedQuantity)
                                .sum(),
                        entry.getValue().stream()
                                .sorted(Comparator.comparing(
                                        FarmCustomer::getFarmName))
                                .map(DeliveryFarm::of)
                                .toList()))
                .toList();

        int totalDemand = warehousePlans.stream().mapToInt(WarehousePlan::totalDemand).sum();
        int totalSupply = warehousePlans.stream().mapToInt(WarehousePlan::totalSupply).sum();
        int rate = totalDemand <= 0 ? 0 : (int) Math.round(totalSupply * 100.0 / totalDemand);
        int warehouseActions = (int) warehousePlans.stream().filter(WarehousePlan::needsAction).count();
        int animalActions = warehousePlans.stream().mapToInt(WarehousePlan::actionCount).sum();
        int shortage = warehousePlans.stream().mapToInt(WarehousePlan::shortageQuantity).sum();
        DeliverySchedule peak = schedule.stream()
                .max(Comparator.comparingInt(DeliverySchedule::amount))
                .orElse(null);
        return new DemandPlan(
                warehousePlans, schedule, totalDemand, totalSupply, rate,
                warehouseActions, animalActions, shortage, peak);
    }

    private static String normalizeAnimal(String value) {
        String animal = value == null ? "기타" : value.trim();
        if (animal.contains("소") || animal.contains("한우")) return "소";
        if (animal.contains("돼지") || animal.contains("양돈")) return "돼지";
        if (animal.contains("조류") || animal.contains("닭") || animal.contains("오리")
                || animal.contains("육계") || animal.contains("산란")) return "조류(닭/오리)";
        return animal;
    }

    private int animalOrder(String animal) {
        return switch (animal) {
            case "소" -> 1;
            case "돼지" -> 2;
            case "조류(닭/오리)" -> 3;
            case "영양제" -> 4;
            default -> 9;
        };
    }

    /** 자동화 검증용 계정은 실제 협력 농장 수요에 포함하지 않는다. */
    static boolean isOperationalFarm(FarmCustomer farm) {
        if (farm.getStatus() != CustomerStatus.ACTIVE) {
            return false;
        }
        if (farm.getMember() == null || farm.getMember().getEmail() == null) {
            return true;
        }
        return !farm.getMember().getEmail().trim().toLowerCase()
                .endsWith("@feedflow.test");
    }

    public static int supplementDemand(int monthlyFeedQuantity) {
        if (monthlyFeedQuantity <= 0) return 0;
        return Math.max(1, (monthlyFeedQuantity * SUPPLEMENT_DEMAND_PERCENT + 99) / 100);
    }
}
