package com.ex.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ex.entity.BinInventory;
import com.ex.entity.BinPurpose;
import com.ex.entity.ProductLot;
import com.ex.entity.Warehouse;
import com.ex.entity.WarehouseBin;
import com.ex.repository.BinInventoryRepository;
import com.ex.repository.WarehouseBinRepository;
import com.ex.repository.WarehouseRepository;
import com.ex.repository.WarehouseStockMovementRepository;
import com.ex.service.WmsOperationsService;

import lombok.RequiredArgsConstructor;

/**
 * 조원 WMS 모듈의 26 x 14 창고 도면 기준 데이터를 통합 프로젝트에 이식한다.
 * 기존 단순 구역(A-01 등)은 엔티티 자체를 새 코드로 전환해 연결된 재고를 보존한다.
 */
@Component
@Order(300)
@RequiredArgsConstructor
public class WmsDataInitializer implements ApplicationRunner {

    private record BinSeed(
            String code,
            String zone,
            BinPurpose purpose,
            String rack,
            Integer level,
            int capacity,
            int x,
            int y,
            int width,
            int height,
            boolean active,
            String memo) {
    }

    private static final Map<String, List<BinSeed>> CENTER_PLANS =
            createCenterPlans();

    private static final List<String> LEGACY_CODES = List.of(
            "A-01", "A-02", "IN-01", "OUT-01", "Q-01");

    private static final String SHOWCASE_STOCK_MEMO =
            "창고 도면 시연 재고 자동 배치";

    private static final int[] SHOWCASE_USAGE_RATES = {
        96, 88, 94, 82, 98, 91, 86, 95
    };

    private final WarehouseRepository warehouseRepository;
    private final WarehouseBinRepository binRepository;
    private final BinInventoryRepository binInventoryRepository;
    private final WarehouseStockMovementRepository movementRepository;
    private final WmsOperationsService wmsOperationsService;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Warehouse> warehouses = warehouseRepository
                .findAllByActiveTrueOrderByDisplayOrderAsc();
        for (Warehouse warehouse : warehouses) {
            List<BinSeed> plan = CENTER_PLANS.get(warehouse.getCode());
            if (plan == null) {
                continue;
            }
            migrateLegacyBins(warehouse, plan);
            plan.forEach(seed -> upsertBin(warehouse, seed));
            ensureTransitBin(warehouse);
            rebalanceOverCapacity(warehouse);
        }
        wmsOperationsService.synchronizeAll("WMS 도면 기준 위치 배치");
        fillShowcaseStock(warehouses);
    }

    /**
     * 발표·시연 화면에서 각 축종 구역의 제품 배치가 한눈에 보이도록 한 번만 채운다.
     * 서비스의 정상 입고 경로를 사용하므로 상품·LOT·구역·센터 재고와 이력이 함께 증가한다.
     */
    private void fillShowcaseStock(List<Warehouse> warehouses) {
        if (movementRepository.existsByMemo(SHOWCASE_STOCK_MEMO)) {
            return;
        }

        List<ProductLot> allLots = wmsOperationsService.lots();
        Map<String, List<ProductLot>> lotsByZone = Map.of(
                "CT", lotsForCategory(allLots, "소"),
                "PG", lotsForCategory(allLots, "돼지"),
                "PL", lotsForCategory(allLots, "조류(닭/오리)"),
                "COLD", lotsForCategory(allLots, "영양제"));

        for (Warehouse warehouse : warehouses) {
            List<WarehouseBin> storageBins = binRepository
                    .findByWarehouseWarehouseIdAndActiveTrueOrderByBinCodeAsc(
                            warehouse.getWarehouseId())
                    .stream()
                    .filter(bin -> bin.getPurpose() == BinPurpose.STORAGE)
                    .toList();
            for (int index = 0; index < storageBins.size(); index++) {
                WarehouseBin bin = storageBins.get(index);
                List<ProductLot> candidates = lotsByZone.getOrDefault(
                        bin.getZone(), allLots);
                if (candidates.isEmpty()) {
                    continue;
                }
                int rate = SHOWCASE_USAGE_RATES[
                        index % SHOWCASE_USAGE_RATES.length];
                int targetQuantity = (int) Math.round(
                        bin.getMaxCapacity() * rate / 100.0);
                int addition = targetQuantity - quantityInBin(bin);
                if (addition <= 0) {
                    continue;
                }
                ProductLot lot = candidates.get(Math.floorMod(
                        warehouse.getDisplayOrder() * 3 + index,
                        candidates.size()));
                wmsOperationsService.receive(
                        lot.getLotId(),
                        null,
                        null,
                        null,
                        null,
                        addition,
                        bin.getBinId(),
                        SHOWCASE_STOCK_MEMO,
                        "데이터 초기화");
            }
        }
    }

    private List<ProductLot> lotsForCategory(
            List<ProductLot> lots,
            String category) {
        return lots.stream()
                .filter(lot -> category.equals(
                        lot.getProduct().getAnimalType()))
                .toList();
    }

    private void migrateLegacyBins(
            Warehouse warehouse,
            List<BinSeed> plan) {
        List<BinSeed> migrationTargets = List.of(
                plan.get(0),
                plan.get(1),
                plan.stream().filter(seed ->
                        seed.purpose() == BinPurpose.RECEIVING)
                        .findFirst().orElseThrow(),
                plan.stream().filter(seed ->
                        seed.purpose() == BinPurpose.SHIPPING)
                        .findFirst().orElseThrow(),
                plan.stream().filter(seed -> "COLD".equals(seed.zone()))
                        .findFirst().orElse(plan.get(2)));

        for (int index = 0; index < LEGACY_CODES.size(); index++) {
            String legacyCode = LEGACY_CODES.get(index);
            BinSeed target = migrationTargets.get(index);
            if (binRepository.findByWarehouseWarehouseIdAndBinCode(
                    warehouse.getWarehouseId(), target.code()).isPresent()) {
                continue;
            }
            binRepository.findByWarehouseWarehouseIdAndBinCode(
                            warehouse.getWarehouseId(), legacyCode)
                    .ifPresent(bin -> applySeed(bin, target));
        }
    }

    private void upsertBin(Warehouse warehouse, BinSeed seed) {
        WarehouseBin bin = binRepository
                .findByWarehouseWarehouseIdAndBinCode(
                        warehouse.getWarehouseId(), seed.code())
                .orElseGet(() -> binRepository.save(new WarehouseBin(
                        warehouse,
                        seed.code(),
                        seed.zone(),
                        seed.purpose(),
                        seed.x(),
                        seed.y(),
                        seed.width(),
                        seed.height(),
                        seed.capacity(),
                        seed.memo())));
        applySeed(bin, seed);
    }

    private void applySeed(WarehouseBin bin, BinSeed seed) {
        bin.update(
                seed.code(),
                seed.zone(),
                seed.purpose(),
                seed.capacity(),
                seed.memo());
        bin.updateLocation(seed.rack(), seed.level());
        bin.updateLayout(
                seed.x(), seed.y(), seed.width(), seed.height());
        bin.changeActive(seed.active());
    }

    private void ensureTransitBin(Warehouse warehouse) {
        String transitCode = "TRANSIT-" + warehouse.getCode();
        binRepository.findByWarehouseWarehouseIdAndBinCode(
                        warehouse.getWarehouseId(), transitCode)
                .orElseGet(() -> binRepository.save(new WarehouseBin(
                        warehouse,
                        transitCode,
                        "센터 간 이동",
                        BinPurpose.IN_TRANSIT,
                        1,
                        1,
                        1,
                        1,
                        0,
                        "센터 간 이관 중 재고를 보존하는 시스템 구역")));
    }

    /**
     * 이전 단순 도면의 대형 구역에 몰려 있던 재고를 새 랙의 수용량에 맞춰 나눈다.
     * 수량은 바뀌지 않고 같은 센터의 활성 보관 구역 사이에서 위치만 변경된다.
     */
    private void rebalanceOverCapacity(Warehouse warehouse) {
        List<WarehouseBin> storageBins = binRepository
                .findByWarehouseWarehouseIdAndActiveTrueOrderByBinCodeAsc(
                        warehouse.getWarehouseId())
                .stream()
                .filter(bin -> bin.getPurpose() == BinPurpose.STORAGE)
                .toList();

        for (WarehouseBin source : storageBins) {
            int overflow = quantityInBin(source)
                    - source.getEffectiveMaxCapacity();
            if (overflow <= 0) {
                continue;
            }
            for (BinInventory sourceInventory :
                    binInventoryRepository.findByBinBinId(source.getBinId())) {
                while (overflow > 0 && sourceInventory.getQuantity() > 0) {
                    WarehouseBin destination = storageBins.stream()
                            .filter(bin -> !bin.getBinId()
                                    .equals(source.getBinId()))
                            .filter(bin -> quantityInBin(bin)
                                    < bin.getEffectiveMaxCapacity())
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException(
                                    warehouse.getName()
                                            + "의 새 도면에 재고를 옮길 여유 공간이 없습니다."));
                    int available = destination.getEffectiveMaxCapacity()
                            - quantityInBin(destination);
                    int moving = Math.min(
                            overflow,
                            Math.min(sourceInventory.getQuantity(), available));
                    BinInventory destinationInventory = binInventoryRepository
                            .findByLotLotIdAndBinBinId(
                                    sourceInventory.getLot().getLotId(),
                                    destination.getBinId())
                            .orElseGet(() -> binInventoryRepository.save(
                                    new BinInventory(
                                            sourceInventory.getLot(),
                                            destination,
                                            0)));
                    sourceInventory.subtract(moving);
                    destinationInventory.add(moving);
                    overflow -= moving;
                }
                if (overflow == 0) {
                    break;
                }
            }
        }
    }

    private int quantityInBin(WarehouseBin bin) {
        return binInventoryRepository.findByBinBinId(bin.getBinId())
                .stream()
                .mapToInt(BinInventory::getQuantity)
                .sum();
    }

    private static Map<String, List<BinSeed>> createCenterPlans() {
        Map<String, List<BinSeed>> plans = new LinkedHashMap<>();
        plans.put("W01", List.of(
                storage("YS-PL-01", "PL", "01", 1, 200, 6, 1, 5, 5, true),
                storage("YS-PL-02", "PL", "01", 2, 200, 6, 6, 5, 5, true),
                storage("YS-PL-03", "PL", "02", 1, 200, 12, 1, 5, 5, true),
                storage("YS-PL-04", "PL", "02", 2, 200, 12, 6, 5, 5, true),
                storage("YS-PG-01", "PG", "03", 1, 250, 18, 1, 4, 5, true),
                storage("YS-PG-02", "PG", "03", 2, 250, 18, 6, 4, 5, true),
                storage("YS-PG-03", "PG", "04", 1, 250, 23, 1, 4, 5, true),
                storage("YS-PG-04", "PG", "04", 2, 250, 23, 6, 4, 5, true),
                storage("YS-COLD-01", "COLD", "05", 1, 200, 6, 11, 5, 3, true),
                waiting("YS-R-01", "R", BinPurpose.RECEIVING, 300, 1, 3, 4, 3),
                waiting("YS-S-01", "S", BinPurpose.SHIPPING, 400, 1, 6, 4, 3)));
        plans.put("W02", List.of(
                storage("GJ-PL-01", "PL", "01", 1, 200, 6, 1, 5, 5, true),
                storage("GJ-PL-02", "PL", "01", 2, 200, 6, 6, 5, 5, true),
                storage("GJ-PL-03", "PL", "02", 1, 200, 12, 1, 5, 5, true),
                storage("GJ-PL-04", "PL", "02", 2, 200, 12, 6, 5, 5, true),
                storage("GJ-PG-01", "PG", "03", 1, 250, 18, 1, 4, 5, true),
                storage("GJ-PG-02", "PG", "03", 2, 250, 18, 6, 4, 5, true),
                storage("GJ-COLD-01", "COLD", "05", 1, 250, 6, 11, 5, 3, true),
                waiting("GJ-R-01", "R", BinPurpose.RECEIVING, 300, 1, 3, 4, 3),
                waiting("GJ-S-01", "S", BinPurpose.SHIPPING, 400, 1, 6, 4, 3)));
        plans.put("W03", List.of(
                storage("US-CT-01", "CT", "01", 1, 200, 6, 1, 5, 5, true),
                storage("US-CT-02", "CT", "01", 2, 200, 6, 6, 5, 5, true),
                storage("US-PG-01", "PG", "02", 1, 300, 12, 1, 5, 5, true),
                storage("US-PG-02", "PG", "02", 2, 300, 12, 6, 5, 5, true),
                storage("US-PL-01", "PL", "03", 1, 200, 18, 1, 4, 5, true),
                storage("US-PL-02", "PL", "03", 2, 200, 18, 6, 4, 5, false),
                storage("US-COLD-01", "COLD", "05", 1, 750, 6, 11, 5, 3, true),
                waiting("US-R-01", "R", BinPurpose.RECEIVING, 300, 1, 3, 4, 3),
                waiting("US-S-01", "S", BinPurpose.SHIPPING, 400, 1, 6, 4, 3)));
        plans.put("W04", List.of(
                storage("AS-CT-01", "CT", "01", 1, 400, 6, 1, 5, 5, true),
                storage("AS-CT-02", "CT", "01", 2, 400, 6, 6, 5, 5, true),
                storage("AS-CT-03", "CT", "02", 1, 400, 12, 1, 5, 5, true),
                storage("AS-CT-04", "CT", "02", 2, 400, 12, 6, 5, 5, true),
                storage("AS-PG-01", "PG", "03", 1, 250, 18, 1, 4, 5, true),
                storage("AS-PG-02", "PG", "03", 2, 250, 18, 6, 4, 5, true),
                storage("AS-PG-03", "PG", "04", 1, 250, 23, 1, 4, 5, true),
                storage("AS-PG-04", "PG", "04", 2, 250, 23, 6, 4, 5, true),
                storage("AS-COLD-01", "COLD", "05", 1, 500, 6, 11, 5, 3, true),
                waiting("AS-R-01", "R", BinPurpose.RECEIVING, 300, 1, 3, 4, 3),
                waiting("AS-S-01", "S", BinPurpose.SHIPPING, 400, 1, 6, 4, 3)));
        plans.put("W05", List.of(
                storage("NJ-PL-01", "PL", "01", 1, 250, 6, 1, 5, 5, true),
                storage("NJ-PL-02", "PL", "01", 2, 250, 6, 6, 5, 5, true),
                storage("NJ-PL-03", "PL", "02", 1, 250, 12, 1, 5, 5, true),
                storage("NJ-PL-04", "PL", "02", 2, 250, 12, 6, 5, 5, true),
                waiting("NJ-R-01", "R", BinPurpose.RECEIVING, 300, 1, 3, 4, 3),
                waiting("NJ-S-01", "S", BinPurpose.SHIPPING, 400, 1, 6, 4, 3)));
        return Map.copyOf(plans);
    }

    private static BinSeed storage(
            String code,
            String zone,
            String rack,
            int level,
            int capacity,
            int x,
            int y,
            int width,
            int height,
            boolean active) {
        String memo = "COLD".equals(zone)
                ? "저온 보관(첨가제) 구역"
                : zone + " 배합사료 보관 구역";
        return new BinSeed(
                code, zone, BinPurpose.STORAGE, rack, level,
                capacity, x, y, width, height, active, memo);
    }

    private static BinSeed waiting(
            String code,
            String zone,
            BinPurpose purpose,
            int capacity,
            int x,
            int y,
            int width,
            int height) {
        return new BinSeed(
                code, zone, purpose, "01", 1,
                capacity, x, y, width, height, true,
                purpose == BinPurpose.RECEIVING
                        ? "입고 검수 전 대기 구역"
                        : "출고 확정 재고 대기 구역");
    }
}
