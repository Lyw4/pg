package com.ex.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ex.entity.Product;
import com.ex.entity.Warehouse;
import com.ex.entity.WarehouseAllocation;
import com.ex.repository.ProductRepository;
import com.ex.repository.WarehouseAllocationRepository;
import com.ex.repository.WarehouseRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WarehousePlanSeeder {

    private record WarehouseSeed(
            String code,
            String name,
            String address,
            String serviceArea,
            String operationFocus,
            double latitude,
            double longitude,
            int displayOrder) {
    }

    private static final List<WarehouseSeed> WAREHOUSE_SEEDS = List.of(
            new WarehouseSeed(
                    "W01",
                    "예산 고덕 창고",
                    "충남 예산군 고덕면 몽곡리 667 일대",
                    "충남 서북부",
                    "양계·양돈 중심",
                    36.7339,
                    126.6995,
                    1),
            new WarehouseSeed(
                    "W02",
                    "김제 서흥 창고",
                    "전북 김제시 흥사동 서흥농공단지 외곽",
                    "전북 서부·새만금권",
                    "닭·오리·돼지 중심",
                    35.8289,
                    126.8795,
                    2),
            new WarehouseSeed(
                    "W03",
                    "안동·의성 창고",
                    "경북 의성군 단촌면 세촌리 국도 5호선 축",
                    "안동·의성·경북 북부",
                    "소·돼지·조류 균형형",
                    36.4247,
                    128.6886,
                    3),
            new WarehouseSeed(
                    "W04",
                    "안성 미양 창고",
                    "경기 안성시 미양면 계륵리·구수리 일대",
                    "경기 남부·충북 서부",
                    "소·돼지 강화형",
                    36.9684,
                    127.2154,
                    4),
            new WarehouseSeed(
                    "W05",
                    "나주 문평 창고",
                    "전남 나주시 문평면 옥당리 문평IC 인근",
                    "전남 중서부",
                    "닭·오리 최우선",
                    35.0459,
                    126.8447,
                    5));

    private static final Map<String, int[]> MONTHLY_PLANS =
            createMonthlyPlans();

    private final WarehouseRepository warehouseRepository;
    private final WarehouseAllocationRepository allocationRepository;
    private final ProductRepository productRepository;

    @Transactional
    public void seed() {
        List<Warehouse> warehouses = WAREHOUSE_SEEDS.stream()
                .map(this::findOrCreateWarehouse)
                .toList();

        MONTHLY_PLANS.forEach((productName, quantities) -> {
            Product product = productRepository.findByName(productName)
                    .orElseThrow(() -> new IllegalStateException(
                            "창고 배치 대상 상품을 찾을 수 없습니다: "
                                    + productName));

            for (int index = 0; index < warehouses.size(); index++) {
                Warehouse warehouse = warehouses.get(index);
                var existingAllocation = allocationRepository
                        .findByWarehouseWarehouseIdAndProductProductId(
                                warehouse.getWarehouseId(),
                                product.getProductId());
                if (existingAllocation.isPresent()) {
                    existingAllocation.get()
                            .initializeCurrentStockIfMissing();
                    continue;
                }

                int monthlyQuantity = quantities[index];
                int targetStockQuantity =
                        (monthlyQuantity * 22 + 29) / 30;

                allocationRepository.save(
                        new WarehouseAllocation(
                                warehouse,
                                product,
                                monthlyQuantity,
                                targetStockQuantity));
            }
        });
    }

    /**
     * 판매 홈페이지에서 추가된 상품도 5개 거점 창고의 재고로 사용할 수 있게
     * 현재 총재고를 창고 수에 맞춰 균등 배치한다.
     */
    @Transactional
    public void ensureAllocationsForAllProducts() {
        List<Warehouse> warehouses =
                warehouseRepository
                        .findAllByActiveTrueOrderByDisplayOrderAsc();
        if (warehouses.isEmpty()) {
            return;
        }

        productRepository
                .findAllByActiveTrueOrderByProductIdAsc()
                .forEach(product ->
                        ensureProductAllocations(product, warehouses));
    }

    private void ensureProductAllocations(
            Product product,
            List<Warehouse> warehouses) {
        int baseQuantity =
                product.getTotalStock() / warehouses.size();
        int remainder =
                product.getTotalStock() % warehouses.size();

        for (int index = 0; index < warehouses.size(); index++) {
            Warehouse warehouse = warehouses.get(index);
            var existing = allocationRepository
                    .findByWarehouseWarehouseIdAndProductProductId(
                            warehouse.getWarehouseId(),
                            product.getProductId());
            if (existing.isPresent()) {
                existing.get().initializeCurrentStockIfMissing();
                continue;
            }

            int targetStockQuantity =
                    baseQuantity + (index < remainder ? 1 : 0);
            int monthlyPlannedQuantity =
                    (targetStockQuantity * 30 + 21) / 22;
            allocationRepository.save(
                    new WarehouseAllocation(
                            warehouse,
                            product,
                            monthlyPlannedQuantity,
                            targetStockQuantity));
        }
    }

    private Warehouse findOrCreateWarehouse(WarehouseSeed seed) {
        Warehouse warehouse = warehouseRepository.findByCode(seed.code())
                .orElseGet(() -> new Warehouse(
                        seed.code(),
                        seed.name(),
                        seed.address(),
                        seed.serviceArea(),
                        seed.operationFocus(),
                        seed.latitude(),
                        seed.longitude(),
                        seed.displayOrder()));

        warehouse.updateDetails(
                seed.name(),
                seed.address(),
                seed.serviceArea(),
                seed.operationFocus(),
                seed.latitude(),
                seed.longitude(),
                seed.displayOrder());

        return warehouseRepository.save(warehouse);
    }

    private static Map<String, int[]> createMonthlyPlans() {
        Map<String, int[]> plans = new LinkedHashMap<>();

        plans.put("한우 송아지 스타터", values(435, 361, 728, 701, 441));
        plans.put("한우 성장 플러스", values(407, 339, 683, 657, 413));
        plans.put("한우 비육 전기", values(489, 406, 819, 788, 496));
        plans.put("한우 비육 후기", values(380, 316, 637, 613, 386));
        plans.put("낙농 송아지 케어", values(430, 67, 50, 650, 368));
        plans.put("젖소 착유우 밸런스", values(509, 79, 59, 768, 435));
        plans.put("번식우 컨디션", values(326, 271, 546, 526, 331));
        plans.put("육우 고효율 사료", values(462, 384, 774, 745, 468));
        plans.put("반추위 안정화 사료", values(295, 194, 377, 467, 287));
        plans.put("한우 프리미엄 마블", values(245, 203, 410, 394, 248));

        plans.put("자돈 스타터 1호", values(713, 802, 612, 1052, 527));
        plans.put("자돈 스타터 2호", values(673, 758, 578, 993, 498));
        plans.put("육성돈 그로우", values(871, 980, 748, 1285, 644));
        plans.put("비육돈 피니셔", values(792, 891, 680, 1168, 586));
        plans.put("모돈 임신기 케어", values(436, 490, 374, 643, 322));
        plans.put("모돈 포유기 파워", values(476, 535, 408, 701, 352));
        plans.put("웅돈 컨디션 플러스", values(317, 357, 272, 468, 235));
        plans.put("양돈 장건강 프로", values(515, 580, 442, 760, 381));
        plans.put("양돈 저단백 밸런스", values(594, 669, 510, 876, 439));
        plans.put("비육돈 프리미엄 골드", values(396, 446, 340, 584, 293));

        plans.put("병아리 초이 사료", values(800, 1645, 1610, 1754, 2299));
        plans.put("육계 전기 사료", values(866, 1782, 1744, 1900, 2491));
        plans.put("육계 후기 사료", values(833, 1714, 1677, 1827, 2395));
        plans.put("산란계 육성 사료", values(633, 1303, 1275, 1389, 1821));
        plans.put("산란계 산란 피크", values(666, 1371, 1342, 1462, 1916));
        plans.put("토종닭 건강 사료", values(533, 1097, 1073, 1169, 1533));
        plans.put("오리 새끼 스타터", values(1, 267, 406, 0, 2213));
        plans.put("육용오리 그로워", values(1, 312, 474, 0, 2581));
        plans.put("조류 면역 밸런스", values(351, 773, 785, 769, 1436));
        plans.put("가금 프리미엄 믹스", values(301, 663, 673, 659, 1231));

        return Map.copyOf(plans);
    }

    private static int[] values(
            int warehouse1,
            int warehouse2,
            int warehouse3,
            int warehouse4,
            int warehouse5) {
        return new int[] {
            warehouse1,
            warehouse2,
            warehouse3,
            warehouse4,
            warehouse5
        };
    }
}
