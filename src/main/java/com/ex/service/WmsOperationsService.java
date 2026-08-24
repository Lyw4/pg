package com.ex.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ex.entity.BinInventory;
import com.ex.entity.BinPurpose;
import com.ex.entity.DisposalReason;
import com.ex.entity.MovementType;
import com.ex.entity.Product;
import com.ex.entity.ProductLot;
import com.ex.entity.StockLog;
import com.ex.entity.StockLog.ChangeType;
import com.ex.entity.Warehouse;
import com.ex.entity.WarehouseAllocation;
import com.ex.entity.WarehouseBin;
import com.ex.entity.WarehouseStockMovement;
import com.ex.repository.BinInventoryRepository;
import com.ex.repository.CustomerOrderRepository;
import com.ex.repository.ProductLotRepository;
import com.ex.repository.ProductRepository;
import com.ex.repository.StockLogRepository;
import com.ex.repository.WarehouseAllocationRepository;
import com.ex.repository.WarehouseBinRepository;
import com.ex.repository.WarehouseRepository;
import com.ex.repository.WarehouseStockMovementRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WmsOperationsService {

    // 한 격자 셀에 수직 적재로 보관할 수 있는 기준 수량이다.
    // 예정 수량에 따라 가로·세로 셀 수를 계산해 실제 도면 면적을 정한다.
    private static final int AUTO_BIN_CAPACITY_PER_CELL = 500;
    // 창고 2D 도면(.ff-floor)의 실제 격자와 동일하게 유지한다.
    // 기존에 16행으로 계산하던 로직은 화면에 존재하지 않는 15~16행을
    // 선택하거나 고정 시설과 겹치는 위치를 선택할 수 있었다.
    private static final int FLOOR_COLUMNS = 26;
    private static final int FLOOR_ROWS = 14;

    public record Overview(
            int warehouseCount,
            int binCount,
            int occupiedBinCount,
            int physicalStock,
            long expiringLotCount,
            long inconsistentProductCount) {
    }

    public List<WarehouseAllocation> warehouseAllocations() {
        return allocationRepository
                .findAllByOrderByWarehouseDisplayOrderAscProductAnimalTypeAscProductNameAsc();
    }

    public record ConsistencyRow(
            Product product,
            int productStock,
            int lotStock,
            int binStock) {

        public boolean consistent() {
            return productStock == lotStock && lotStock == binStock;
        }

        public int difference() {
            return binStock - lotStock;
        }
    }

    public record Traceability(
            ProductLot lot,
            List<BinInventory> inventories,
            List<WarehouseStockMovement> movements,
            int locatedQuantity) {
    }

    public record ScanResult(
            String scanValue,
            String type,
            String title,
            String detail,
            Long lotId,
            Long binId,
            Long productId,
            String productCode,
            Product product,
            ProductLot lot,
            List<BinInventory> inventories,
            int quantity,
            int locationCount) {

        public boolean found() {
            return !"UNKNOWN".equals(type);
        }
    }

    public record LotLabel(
            ProductLot lot,
            String code,
            String productCode,
            String qrDataUri,
            long daysRemaining) {
    }

    public record ProductLabel(
            Product product,
            String code,
            String qrDataUri) {
    }

    public record DirectOutboundProduct(
            Product product,
            int availableQuantity,
            int locationCount,
            LocalDate nearestExpirationDate) {
    }

    public record BinMapItem(
            WarehouseBin bin,
            int quantity,
            int usageRate,
            long nearestExpiryDays,
            List<BinInventory> inventories) {

        public int remainingCapacity() {
            return Math.max(0, bin.getEffectiveMaxCapacity() - quantity);
        }

        public int lotCount() {
            return inventories.size();
        }

        public int productCount() {
            return (int) inventories.stream()
                    .map(inventory -> inventory.getLot().getProduct()
                            .getProductId())
                    .distinct()
                    .count();
        }

        /** 도면 타일에 표시할 한글 상품명(재고가 없으면 자동 생성 메모의 상품명) */
        public String productLabel() {
            String names = inventories.stream()
                    .map(inventory -> inventory.getLot().getProduct().getName())
                    .filter(name -> name != null && !name.isBlank())
                    .distinct()
                    .sorted()
                    .collect(Collectors.joining(" · "));
            if (!names.isBlank()) {
                return names;
            }
            String memo = bin.getMemo();
            if (memo != null && memo.startsWith("취급 상품:")) {
                String name = memo.substring("취급 상품:".length())
                        .split(" · 계획 수량:", 2)[0]
                        .trim();
                if (!name.isBlank()) {
                    return name;
                }
            }
            return displayName();
        }

        public boolean hasExpiryWarning() {
            return nearestExpiryDays != Long.MAX_VALUE
                    && nearestExpiryDays <= 30;
        }

        public int usageRateCapped() {
            return Math.max(0, Math.min(100, usageRate));
        }

        public String gridArea() {
            return bin.getPosY() + " / " + bin.getPosX()
                    + " / span " + bin.getPosHeight()
                    + " / span " + bin.getPosWidth();
        }

        /** 내부 구역 코드는 유지하고, 관리자 화면에만 이해하기 쉬운 이름을 표시한다. */
        public String displayName() {
            String zone = bin.getZone();
            return switch (bin.getZone() == null ? "" : bin.getZone().toUpperCase()) {
                case "CT" -> "소 사료 보관";
                case "PG" -> "돼지 사료 보관";
                case "PL" -> "조류 사료 보관";
                case "COLD" -> "저온 보관";
                case "R" -> "입고 대기·검수";
                case "S" -> "출고 대기·배송";
                default -> zone == null || zone.isBlank() ? "기타 구역" : zone;
            };
        }

        public boolean narrow() {
            return bin.getPosWidth() <= 2;
        }

        public boolean flat() {
            return bin.getPosHeight() <= 1;
        }

        public String expiryLabel() {
            if (nearestExpiryDays == Long.MAX_VALUE) {
                return "";
            }
            return nearestExpiryDays < 0
                    ? "만료 " + (-nearestExpiryDays) + "일"
                    : "D-" + nearestExpiryDays;
        }

        public String statusLabel() {
            if (!bin.isActive()) {
                return "사용 중지";
            }
            if (bin.getPurpose() != BinPurpose.STORAGE) {
                return bin.getPurpose().getLabel();
            }
            if (quantity == 0) {
                return "비어있음";
            }
            if (usageRate >= 90) {
                return "포화";
            }
            return usageRate >= 60 ? "보통" : "여유";
        }

        public String statusClass() {
            if (!bin.isActive()) {
                return "ff-bin-inactive";
            }
            return switch (bin.getPurpose()) {
                case RECEIVING -> "ff-bin-receiving";
                case SHIPPING -> "ff-bin-shipping";
                case INSPECTION -> "ff-bin-inspection";
                case IN_TRANSIT -> "ff-bin-inactive";
                case STORAGE -> quantity == 0
                        ? "ff-bin-empty"
                        : (usageRate >= 90
                                ? "ff-bin-full"
                                : (usageRate >= 60
                                        ? "ff-bin-normal"
                                        : "ff-bin-spare"));
            };
        }

        public String statusBadgeClass() {
            if (!bin.isActive()) {
                return "text-bg-secondary";
            }
            if (bin.getPurpose() == BinPurpose.RECEIVING) {
                return "text-bg-info";
            }
            if (bin.getPurpose() == BinPurpose.SHIPPING) {
                return "text-bg-warning";
            }
            if (quantity == 0) {
                return "text-bg-light border text-dark";
            }
            return usageRate >= 90
                    ? "text-bg-danger"
                    : (usageRate >= 60
                            ? "text-bg-warning"
                            : "text-bg-success");
        }
    }

    public record ZoneSummary(
            String zone,
            int binCount,
            int quantity,
            int capacity,
            int usageRate,
            int posX,
            int posY,
            int posWidth,
            int posHeight) {

        public String gridArea() {
            return posY + " / " + posX
                    + " / span " + posHeight
                    + " / span " + posWidth;
        }

        public String areaClass() {
            return "COLD".equalsIgnoreCase(zone)
                    ? "ff-zone-area-cold"
                    : "ff-zone-area-normal";
        }

        public String displayName() {
            return switch (zone == null ? "" : zone.toUpperCase()) {
                case "CT" -> "소 사료 보관";
                case "PG" -> "돼지 사료 보관";
                case "PL" -> "조류 사료 보관";
                case "COLD" -> "저온 보관";
                case "R" -> "입고 대기·검수";
                case "S" -> "출고 대기·배송";
                default -> zone == null || zone.isBlank() ? "기타 구역" : zone;
            };
        }

        public int usageRateCapped() {
            return Math.max(0, Math.min(100, usageRate));
        }
    }

    public record FacilityItem(
            String label,
            String cssClass,
            int posX,
            int posY,
            int posWidth,
            int posHeight) {

        public String gridArea() {
            return posY + " / " + posX
                    + " / span " + posHeight
                    + " / span " + posWidth;
        }
    }

    public record WarehouseMapSummary(
            Warehouse warehouse,
            List<BinMapItem> binItems,
            int storageQuantity,
            int storageCapacity,
            int storageUsageRate,
            int waitingQuantity,
            int remainingCapacity,
            long saturatedBinCount,
            long expiringBinCount,
            long storageBinCount,
            long waitingBinCount,
            long inactiveBinCount,
            List<FacilityItem> facilities,
            List<ZoneSummary> zones) {
    }

    public record BinInventoryDetail(
            String productCode,
            String productName,
            String lotNo,
            LocalDate expirationDate,
            long remainingDays,
            String dDayLabel,
            String dDayBadgeClass,
            boolean expired,
            int quantity) {
    }

    public record BinDetailSummary(
            Long binId,
            Long warehouseId,
            String binCode,
            String locationLabel,
            String statusLabel,
            String statusBadgeClass,
            int loadedQuantity,
            int maxCapacity,
            int usageRate,
            int remainingCapacity,
            boolean deletable,
            String deleteBlockedReason) {
    }

    public record BinDetail(
            BinDetailSummary bin,
            List<BinInventoryDetail> inventories,
            boolean hasExpired) {
    }

    private final WarehouseRepository warehouseRepository;
    private final WarehouseBinRepository binRepository;
    private final BinInventoryRepository binInventoryRepository;
    private final WarehouseStockMovementRepository movementRepository;
    private final ProductRepository productRepository;
    private final ProductLotRepository lotRepository;
    private final WarehouseAllocationRepository allocationRepository;
    private final StockLogRepository stockLogRepository;
    private final CustomerOrderRepository customerOrderRepository;
    private final QrCodeService qrCodeService;
    private final InventoryService inventoryService;
    private final SellableStockQuery sellableStockQuery;
    private final WarehouseCapacityPlanningService capacityPlanningService;

    public List<Warehouse> warehouses() {
        return warehouseRepository
                .findAllByActiveTrueOrderByDisplayOrderAsc();
    }

    public List<Product> products() {
        return productRepository
                .findAllByActiveTrueOrderByProductIdAsc();
    }

    public List<ProductLot> lots() {
        return lotRepository.findAllByOrderByExpirationDateAsc()
                .stream()
                .filter(lot -> lot.getProduct().isActive())
                .toList();
    }

    public List<WarehouseBin> bins() {
        return binRepository
                .findAllByOrderByWarehouseDisplayOrderAscBinCodeAsc();
    }

    public List<WarehouseBin> bins(Long warehouseId) {
        if (warehouseId == null) {
            return bins();
        }
        return binRepository
                .findByWarehouseWarehouseIdOrderByBinCodeAsc(warehouseId);
    }

    public List<WarehouseBin> selectableBins() {
        return bins().stream()
                .filter(WarehouseBin::isActive)
                .filter(bin -> !bin.getPurpose().isSystemManaged())
                .toList();
    }

    public List<BinInventory> inventories(Long warehouseId) {
        List<BinInventory> rows = warehouseId == null
                ? binInventoryRepository.findAllByOrderByBinBinCodeAsc()
                : binInventoryRepository
                        .findByBinWarehouseWarehouseIdOrderByBinBinCodeAsc(
                                warehouseId);
        return rows.stream()
                .filter(inventory -> inventory.getQuantity() > 0)
                .filter(inventory -> inventory.getLot().getProduct().isActive())
                .sorted(Comparator
                        .comparing((BinInventory inventory) ->
                                inventory.getBin().getWarehouse()
                                        .getDisplayOrder())
                        .thenComparing(inventory ->
                                inventory.getBin().getBinCode())
                        .thenComparing(inventory ->
                                inventory.getLot().getExpirationDate()))
                .toList();
    }

    public List<DirectOutboundProduct> directOutboundProducts() {
        LocalDate sellableFrom = LocalDate.now()
                .plusDays(SellableStockQuery.MINIMUM_SELLABLE_DAYS);
        Map<Long, Integer> reservedByLot = inventoryService.reservedStockByLot();
        Map<Product, List<BinInventory>> byProduct =
                binInventoryRepository.findAllByOrderByBinBinCodeAsc()
                        .stream()
                        .filter(inventory -> inventory.getQuantity() > 0)
                        .filter(inventory -> WmsAllocationPolicy.isAllocatable(
                                inventory.getBin()))
                        .filter(inventory -> inventory.getLot().getProduct()
                                .isActive())
                        .filter(inventory -> !inventory.getLot()
                                .getExpirationDate().isBefore(sellableFrom))
                        .collect(Collectors.groupingBy(
                                inventory -> inventory.getLot().getProduct(),
                                LinkedHashMap::new,
                                Collectors.toList()));
        return byProduct.entrySet().stream()
                .map(entry -> new DirectOutboundProduct(
                        entry.getKey(),
                        directAvailableQuantity(entry.getValue(), reservedByLot),
                        entry.getValue().size(),
                        entry.getValue().stream()
                                .map(inventory -> inventory.getLot()
                                        .getExpirationDate())
                                .min(LocalDate::compareTo)
                                .orElse(null)))
                .sorted(Comparator.comparing(item ->
                        item.product().getName()))
                .toList();
    }

    private int directAvailableQuantity(
            List<BinInventory> inventories,
            Map<Long, Integer> reservedByLot) {
        return inventories.stream()
                .collect(Collectors.groupingBy(
                        inventory -> inventory.getLot().getLotId()))
                .values().stream()
                .mapToInt(locations -> {
                    ProductLot lot = locations.get(0).getLot();
                    int located = locations.stream()
                            .mapToInt(BinInventory::getQuantity).sum();
                    return Math.min(
                            located,
                            Math.max(0, lot.getLotQuantity()
                                    - reservedByLot.getOrDefault(lot.getLotId(), 0)));
                })
                .sum();
    }

    public List<WarehouseStockMovement> movements() {
        return movementRepository.findTop200ByOrderByCreatedAtDesc();
    }

    public Map<Long, String> movementBuyerNames(
            List<WarehouseStockMovement> movements) {
        List<Long> orderIds = movements.stream()
                .map(WarehouseStockMovement::getOrderId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        return customerOrderRepository.findAllById(orderIds).stream()
                .collect(Collectors.toMap(
                        order -> order.getOrderId(),
                        order -> order.getCustomerName() == null
                                || order.getCustomerName().isBlank()
                                        ? "구매자 미등록"
                                        : order.getCustomerName(),
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    public Map<Long, Integer> binQuantities() {
        return binInventoryRepository.findAllByOrderByBinBinCodeAsc()
                .stream()
                .filter(inventory -> inventory.getLot().getProduct().isActive())
                .collect(Collectors.groupingBy(
                        inventory -> inventory.getBin().getBinId(),
                        LinkedHashMap::new,
                        Collectors.summingInt(BinInventory::getQuantity)));
    }

    public Map<Long, Integer> warehousePhysicalQuantities() {
        return binInventoryRepository.findAllByOrderByBinBinCodeAsc()
                .stream()
                .filter(inventory -> inventory.getQuantity() > 0)
                .filter(inventory -> inventory.getLot().getProduct().isActive())
                .collect(Collectors.groupingBy(
                        inventory -> inventory.getBin().getWarehouse()
                                .getWarehouseId(),
                        LinkedHashMap::new,
                        Collectors.summingInt(BinInventory::getQuantity)));
    }

    public Overview overview() {
        Map<Long, Integer> binQuantities = binQuantities();
        List<ConsistencyRow> consistency = consistencyRows();
        List<WarehouseBin> activeBins = bins().stream()
                .filter(WarehouseBin::isActive)
                .toList();
        return new Overview(
                warehouses().size(),
                activeBins.size(),
                (int) activeBins.stream()
                        .filter(bin -> binQuantities.getOrDefault(
                                bin.getBinId(), 0) > 0)
                        .count(),
                activeBins.stream()
                        .mapToInt(bin -> binQuantities.getOrDefault(
                                bin.getBinId(), 0))
                        .sum(),
                lots().stream()
                        .filter(lot -> !lot.getExpirationDate()
                                .isAfter(LocalDate.now().plusDays(30)))
                        .count(),
                consistency.stream()
                        .filter(row -> !row.consistent())
                        .count());
    }

    public WarehouseMapSummary warehouseMap(Long warehouseId) {
        Warehouse warehouse = requiredWarehouse(warehouseId);
        List<WarehouseBin> allPhysicalBins = bins(warehouseId).stream()
                .filter(bin -> bin.getPurpose().isPhysicalSpace())
                .toList();
        // 비활성화된 빈 구역은 이력 보존을 위해 DB에 남기되 도면에서는 제외합니다.
        List<WarehouseBin> warehouseBins = allPhysicalBins.stream()
                .filter(WarehouseBin::isActive)
                .toList();
        Map<Long, List<BinInventory>> inventoriesByBin =
                inventories(warehouseId).stream()
                        .collect(Collectors.groupingBy(
                                inventory -> inventory.getBin().getBinId(),
                                LinkedHashMap::new,
                                Collectors.toList()));
        LocalDate today = LocalDate.now();
        List<BinMapItem> binItems = warehouseBins.stream()
                .map(bin -> {
                    List<BinInventory> binInventories = inventoriesByBin
                            .getOrDefault(bin.getBinId(), List.of());
                    int quantity = binInventories.stream()
                            .mapToInt(BinInventory::getQuantity)
                            .sum();
                    int effectiveCapacity = bin.getEffectiveMaxCapacity();
                    int usageRate = effectiveCapacity == 0
                            ? 0
                            : (int) Math.round(quantity * 100.0
                                    / effectiveCapacity);
                    long nearestExpiryDays = binInventories.stream()
                            .mapToLong(inventory -> ChronoUnit.DAYS.between(
                                    today,
                                    inventory.getLot().getExpirationDate()))
                            .min()
                            .orElse(Long.MAX_VALUE);
                    return new BinMapItem(
                            bin,
                            quantity,
                            usageRate,
                            nearestExpiryDays,
                            binInventories);
                })
                .toList();

        List<BinMapItem> storageBins = binItems.stream()
                .filter(item -> item.bin().getPurpose()
                        == BinPurpose.STORAGE)
                .toList();
        int storageQuantity = storageBins.stream()
                .mapToInt(BinMapItem::quantity)
                .sum();
        int storageCapacity = storageBins.stream()
                .mapToInt(item -> item.bin().getEffectiveMaxCapacity())
                .sum();
        int storageUsageRate = storageCapacity == 0
                ? 0
                : (int) Math.round(storageQuantity * 100.0
                        / storageCapacity);
        int waitingQuantity = binItems.stream()
                .filter(item -> item.bin().getPurpose()
                        != BinPurpose.STORAGE)
                .mapToInt(BinMapItem::quantity)
                .sum();

        Map<String, List<BinMapItem>> byZone = binItems.stream()
                .collect(Collectors.groupingBy(
                        item -> item.bin().getZone(),
                        LinkedHashMap::new,
                        Collectors.toList()));
        List<ZoneSummary> zones = byZone.entrySet().stream()
                .map(entry -> {
                    int quantity = entry.getValue().stream()
                            .mapToInt(BinMapItem::quantity)
                            .sum();
                    int capacity = entry.getValue().stream()
                            .mapToInt(item -> item.bin().getEffectiveMaxCapacity())
                            .sum();
                    int rate = capacity == 0
                            ? 0
                            : (int) Math.round(quantity * 100.0 / capacity);
                    int minX = entry.getValue().stream()
                            .mapToInt(item -> item.bin().getPosX())
                            .min().orElse(1);
                    int minY = entry.getValue().stream()
                            .mapToInt(item -> item.bin().getPosY())
                            .min().orElse(1);
                    int maxX = entry.getValue().stream()
                            .mapToInt(item -> item.bin().getPosX()
                                    + item.bin().getPosWidth())
                            .max().orElse(minX + 1);
                    int maxY = entry.getValue().stream()
                            .mapToInt(item -> item.bin().getPosY()
                                    + item.bin().getPosHeight())
                            .max().orElse(minY + 1);
                    return new ZoneSummary(
                            entry.getKey(),
                            entry.getValue().size(),
                            quantity,
                            capacity,
                            rate,
                            minX,
                            minY,
                            maxX - minX,
                            maxY - minY);
                })
                .toList();

        return new WarehouseMapSummary(
                warehouse,
                binItems,
                storageQuantity,
                storageCapacity,
                storageUsageRate,
                waitingQuantity,
                Math.max(0, storageCapacity - storageQuantity),
                storageBins.stream()
                        .filter(item -> item.usageRate() >= 90)
                        .count(),
                binItems.stream()
                        .filter(BinMapItem::hasExpiryWarning)
                        .count(),
                storageBins.size(),
                binItems.stream()
                        .filter(item -> item.bin().getPurpose()
                                != BinPurpose.STORAGE)
                        .count(),
                allPhysicalBins.stream()
                        .filter(bin -> !bin.isActive())
                        .count(),
                warehouseFacilities(),
                zones);
    }

    public BinDetail binDetail(Long binId) {
        WarehouseBin bin = requiredBin(binId);
        List<BinInventory> inventories = binInventoryRepository
                .findByBinBinId(binId)
                .stream()
                .filter(inventory -> inventory.getQuantity() > 0)
                .sorted(Comparator.comparing(inventory ->
                        inventory.getLot().getExpirationDate()))
                .toList();
        int quantity = inventories.stream()
                .mapToInt(BinInventory::getQuantity)
                .sum();
        int effectiveCapacity = bin.getEffectiveMaxCapacity();
        int usageRate = effectiveCapacity == 0
                ? 0
                : (int) Math.round(quantity * 100.0
                        / effectiveCapacity);
        long nearestExpiryDays = inventories.stream()
                .mapToLong(inventory -> ChronoUnit.DAYS.between(
                        LocalDate.now(),
                        inventory.getLot().getExpirationDate()))
                .min()
                .orElse(Long.MAX_VALUE);
        BinMapItem mapItem = new BinMapItem(
                bin,
                quantity,
                usageRate,
                nearestExpiryDays,
                inventories);
        List<BinInventoryDetail> details = inventories.stream()
                .map(inventory -> {
                    long remainingDays = ChronoUnit.DAYS.between(
                            LocalDate.now(),
                            inventory.getLot().getExpirationDate());
                    String dDayLabel = remainingDays < 0
                            ? "만료 " + (-remainingDays) + "일 경과"
                            : (remainingDays == 0
                                    ? "오늘 만료"
                                    : "D-" + remainingDays);
                    String badgeClass = remainingDays < 0
                            ? "text-bg-dark"
                            : (remainingDays <= 7
                                    ? "text-bg-danger"
                                    : (remainingDays <= 30
                                            ? "text-bg-warning"
                                            : "text-bg-light border text-dark"));
                    Product product = inventory.getLot().getProduct();
                    return new BinInventoryDetail(
                            productCode(product),
                            product.getName(),
                            inventory.getLot().getLotNo(),
                            inventory.getLot().getExpirationDate(),
                            remainingDays,
                            dDayLabel,
                            badgeClass,
                            remainingDays < 0,
                            inventory.getQuantity());
                })
                .toList();
        return new BinDetail(
                new BinDetailSummary(
                        bin.getBinId(),
                        bin.getWarehouse().getWarehouseId(),
                        bin.getBinCode(),
                        bin.getLocationLabel(),
                        mapItem.statusLabel(),
                        mapItem.statusBadgeClass(),
                        quantity,
                        effectiveCapacity,
                        usageRate,
                        mapItem.remainingCapacity(),
                        !bin.getPurpose().isSystemManaged() && quantity == 0,
                        bin.getPurpose().isSystemManaged()
                                ? "시스템 관리 구역은 삭제할 수 없습니다."
                                : (quantity > 0
                                        ? "재고를 다른 구역으로 이동하거나 출고한 뒤 삭제할 수 있습니다."
                                        : null)),
                details,
                details.stream().anyMatch(BinInventoryDetail::expired));
    }

    private List<FacilityItem> warehouseFacilities() {
        return List.of(
                new FacilityItem(
                        "입고 출입구", "ff-facility-door",
                        1, 1, 4, 2),
                new FacilityItem(
                        "하역장 벽", "ff-facility-wall",
                        5, 1, 1, 14),
                new FacilityItem(
                        "출고 출입구", "ff-facility-door",
                        1, 10, 4, 2),
                new FacilityItem(
                        "검수실", "ff-facility-inspection",
                        1, 12, 4, 3));
    }

    public List<ConsistencyRow> consistencyRows() {
        Map<Long, Integer> lotStocks = lots().stream()
                .collect(Collectors.groupingBy(
                        lot -> lot.getProduct().getProductId(),
                        Collectors.summingInt(ProductLot::getLotQuantity)));
        Map<Long, Integer> binStocks = binInventoryRepository
                .findAllByOrderByBinBinCodeAsc()
                .stream()
                .filter(inventory -> inventory.getLot().getProduct().isActive())
                .collect(Collectors.groupingBy(
                        inventory -> inventory.getLot().getProduct()
                                .getProductId(),
                        Collectors.summingInt(BinInventory::getQuantity)));
        return products().stream()
                .map(product -> new ConsistencyRow(
                        product,
                        product.getTotalStock(),
                        lotStocks.getOrDefault(product.getProductId(), 0),
                        binStocks.getOrDefault(product.getProductId(), 0)))
                .toList();
    }

    public Traceability traceability(Long lotId) {
        ProductLot lot = requiredLot(lotId);
        List<BinInventory> locations = binInventoryRepository
                .findByLotLotIdAndQuantityGreaterThanOrderByBinBinCodeAsc(
                        lotId, 0);
        return new Traceability(
                lot,
                locations,
                movementRepository.findByLotLotIdOrderByCreatedAtDesc(lotId),
                locations.stream()
                        .mapToInt(BinInventory::getQuantity)
                        .sum());
    }

    public List<LotLabel> lotLabels() {
        LocalDate today = LocalDate.now();
        return lots().stream()
                .map(lot -> new LotLabel(
                        lot,
                        lot.getLotNo(),
                        productCode(lot.getProduct()),
                        qrCodeService.qrCodeDataUri(
                                "LOT:" + lot.getLotNo()),
                        ChronoUnit.DAYS.between(
                                today, lot.getExpirationDate())))
                .toList();
    }

    public List<ProductLabel> productLabels() {
        return products().stream()
                .map(product -> {
                    String code = productCode(product);
                    return new ProductLabel(
                            product,
                            code,
                            qrCodeService.qrCodeDataUri(
                                    "PRODUCT:" + code));
                })
                .toList();
    }

    public String productCode(Product product) {
        String prefix = switch (product.getAnimalType()) {
            case "소" -> "FD-CT";
            case "돼지" -> "FD-PG";
            case "조류(닭/오리)" -> "FD-PL";
            default -> "SP";
        };
        return "%s-%03d".formatted(prefix, product.getProductId());
    }

    public ScanResult lookupScanInput(String scanValue) {
        if (scanValue == null || scanValue.isBlank()) {
            throw new IllegalArgumentException(
                    "QR 코드를 스캔하거나 제품명을 입력해 주세요.");
        }

        String normalized = scanValue.trim();
        int separatorIndex = normalized.indexOf(':');
        if (separatorIndex < 0) {
            List<Product> nameMatches = products().stream()
                    .filter(product -> product.getName()
                            .equalsIgnoreCase(normalized))
                    .toList();
            if (nameMatches.size() == 1) {
                return productScanResult(scanValue, nameMatches.getFirst());
            }
            if (nameMatches.size() > 1) {
                return unknownScan(
                        scanValue,
                        "같은 이름의 제품이 여러 개입니다. PRODUCT QR 코드를 이용해 주세요.");
            }
            return unknownScan(
                    scanValue,
                    "정확한 제품명 또는 QR 라벨의 LOT·PRODUCT 코드를 확인해 주세요.");
        }
        if (separatorIndex <= 0
                || separatorIndex == normalized.length() - 1) {
            return unknownScan(
                    scanValue,
                    "QR 라벨의 LOT 또는 PRODUCT 코드를 확인해 주세요.");
        }

        String type = normalized.substring(0, separatorIndex)
                .trim()
                .toUpperCase(Locale.ROOT);
        String value = normalized.substring(separatorIndex + 1)
                .trim()
                .toUpperCase(Locale.ROOT);

        if ("LOT".equals(type)) {
            Optional<ProductLot> lot = lotRepository.findByLotNo(value);
            if (lot.isEmpty()) {
                return unknownScan(
                        scanValue,
                        "QR 라벨의 LOT 번호를 확인해 주세요.");
            }
            ProductLot found = lot.get();
            List<BinInventory> inventories = binInventoryRepository
                    .findByLotLotIdAndQuantityGreaterThanOrderByBinBinCodeAsc(
                            found.getLotId(), 0);
            int located = inventories.stream()
                    .mapToInt(BinInventory::getQuantity)
                    .sum();
            return new ScanResult(
                    scanValue,
                    "LOT",
                    found.getLotNo(),
                    "LOT 재고 " + found.getLotQuantity() + "포대",
                    found.getLotId(),
                    null,
                    found.getProduct().getProductId(),
                    productCode(found.getProduct()),
                    found.getProduct(),
                    found,
                    inventories,
                    located,
                    inventories.size());
        }

        if ("PRODUCT".equals(type)) {
            Optional<Product> product = products().stream()
                    .filter(item -> productCode(item).equalsIgnoreCase(value))
                    .findFirst();
            if (product.isEmpty()) {
                return unknownScan(
                        scanValue,
                        "QR 라벨의 PRODUCT 품목 코드를 확인해 주세요.");
            }
            return productScanResult(scanValue, product.get());
        }

        return unknownScan(
                scanValue,
                "QR 라벨의 LOT 또는 PRODUCT 코드를 확인해 주세요.");
    }

    private ScanResult productScanResult(
            String scanValue,
            Product product) {
        List<BinInventory> inventories = binInventoryRepository
                .findByLotProductProductIdAndQuantityGreaterThanOrderByBinBinCodeAsc(
                        product.getProductId(), 0);
        return new ScanResult(
                scanValue,
                "PRODUCT",
                productCode(product),
                product.getName() + " · 전체 재고 "
                        + product.getTotalStock() + "포대",
                null,
                null,
                product.getProductId(),
                productCode(product),
                product,
                null,
                inventories,
                product.getTotalStock(),
                inventories.stream()
                        .map(inventory -> inventory.getBin().getBinId())
                        .distinct()
                        .toList()
                        .size());
    }

    private ScanResult unknownScan(String scanValue, String detail) {
        return new ScanResult(
                scanValue,
                "UNKNOWN",
                "일치하는 제품 또는 QR 코드 없음",
                detail,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                0,
                0);
    }

    @Transactional
    public void createBin(
            Long warehouseId,
            String binCode,
            String zone,
            BinPurpose purpose,
            int maxCapacity,
            int posX,
            int posY,
            int posWidth,
            int posHeight,
            String memo) {
        Warehouse warehouse = requiredWarehouse(warehouseId);
        if (purpose == null || purpose.isSystemManaged()) {
            throw new IllegalArgumentException(
                    "등록 가능한 구역 용도를 선택해 주세요.");
        }
        String normalized = binCode.trim().toUpperCase(Locale.ROOT);
        if (binRepository.findByWarehouseWarehouseIdAndBinCode(
                warehouseId, normalized).isPresent()) {
            throw new IllegalArgumentException(
                    "같은 창고에 이미 존재하는 구역 코드입니다.");
        }
        int[] layout = findAvailableLayout(warehouseId, posWidth, posHeight);
        binRepository.save(new WarehouseBin(
                warehouse,
                normalized,
                zone,
                purpose,
                layout[0],
                layout[1],
                posWidth,
                posHeight,
                maxCapacity,
                memo));
    }

    @Transactional
    public WarehouseBin createAutomaticProductBin(
            Long warehouseId,
            Long productId,
            int plannedQuantity,
            String memo) {
        return createAutomaticProductBin(
                warehouseId, productId, plannedQuantity, null, null, memo);
    }

    @Transactional
    public WarehouseBin createAutomaticProductBin(
            Long warehouseId,
            Long productId,
            int plannedQuantity,
            Integer preferredPosX,
            Integer preferredPosY,
            String memo) {
        return createAutomaticProductBin(
                warehouseId, productId, plannedQuantity,
                preferredPosX, preferredPosY, null, null, memo);
    }

    @Transactional
    public WarehouseBin createAutomaticProductBin(
            Long warehouseId,
            Long productId,
            int plannedQuantity,
            Integer preferredPosX,
            Integer preferredPosY,
            Integer preferredWidth,
            Integer preferredHeight,
            String memo) {
        if (plannedQuantity < 1) {
            throw new IllegalArgumentException("보관 예정 수량은 1포 이상 입력해 주세요.");
        }
        Warehouse warehouse = requiredWarehouse(warehouseId);
        WarehouseAllocation allocation = allocationRepository
                .findByWarehouseWarehouseIdAndProductProductId(
                        warehouseId, productId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "선택한 창고에서 취급 중인 상품만 구역에 배정할 수 있습니다."));
        Product product = allocation.getProduct();
        String zone = zoneCodeFor(product.getAnimalType());
        String binCode = nextAutomaticBinCode(warehouse, zone);

        int requiredCells = Math.max(1, (int) Math.ceil(
                plannedQuantity / (double) AUTO_BIN_CAPACITY_PER_CELL));
        int width = Math.min(FLOOR_COLUMNS,
                (int) Math.ceil(Math.sqrt(requiredCells)));
        int height = (int) Math.ceil(requiredCells / (double) width);
        boolean selectedByUser = preferredPosX != null || preferredPosY != null;
        int[] layout;
        if (selectedByUser) {
            if (preferredPosX == null || preferredPosY == null) {
                throw new IllegalArgumentException(
                        "도면 위치의 X·Y 좌표를 모두 선택해 주세요.");
            }
            if (preferredWidth != null || preferredHeight != null) {
                if (preferredWidth == null || preferredHeight == null
                        || preferredWidth < 1 || preferredHeight < 1) {
                    throw new IllegalArgumentException(
                            "선택한 도면 구역의 가로·세로 크기를 확인해 주세요.");
                }
                // 도면은 운영 위치를 보여주는 개념도이며 실제 수용량은
                // maxCapacity와 수직 적재 단수로 별도 계산한다. 주변 공간에
                // 맞춰 표시 크기가 작아져도 계획 수량은 그대로 유지된다.
                width = preferredWidth;
                height = preferredHeight;
            }
            if (!isLayoutAvailable(
                    warehouseId, preferredPosX, preferredPosY,
                    width, height)) {
                throw new IllegalArgumentException(
                        "선택한 도면 위치는 기존 구역·시설과 겹치거나 도면 범위를 벗어납니다. 빈 공간을 다시 선택해 주세요.");
            }
            layout = new int[] {preferredPosX, preferredPosY};
        } else {
            try {
                layout = findAvailableLayout(warehouseId, width, height);
            } catch (IllegalStateException exception) {
                if (width == height) {
                    throw exception;
                }
                layout = findAvailableLayout(warehouseId, height, width);
                int originalWidth = width;
                width = height;
                height = originalWidth;
            }
        }

        int floorCapacity = (int) Math.ceil(
                plannedQuantity / (double) WarehouseBin.VERTICAL_STACKING_LEVELS);
        String productMemo = "취급 상품: " + product.getName()
                + " · 계획 수량: " + plannedQuantity + "포"
                + (memo == null || memo.isBlank() ? "" : " · " + memo.trim());
        return binRepository.save(new WarehouseBin(
                warehouse,
                binCode,
                zone,
                BinPurpose.STORAGE,
                layout[0],
                layout[1],
                width,
                height,
                floorCapacity,
                productMemo));
    }

    private String zoneCodeFor(String animalType) {
        return WmsZonePolicy.zoneFor(animalType);
    }

    private String nextAutomaticBinCode(Warehouse warehouse, String zone) {
        String prefix = warehouse.getCode() + "-" + zone + "-";
        int sequence = 1;
        while (binRepository.findByWarehouseWarehouseIdAndBinCode(
                warehouse.getWarehouseId(),
                prefix + String.format("%02d", sequence)).isPresent()) {
            sequence++;
        }
        return prefix + String.format("%02d", sequence);
    }

    @Transactional
    public void deleteBin(Long binId) {
        WarehouseBin bin = requiredBin(binId);
        if (bin.getPurpose().isSystemManaged()) {
            throw new IllegalStateException("시스템 관리 구역은 삭제할 수 없습니다.");
        }
        if (quantityInBin(binId) > 0) {
            throw new IllegalStateException("재고가 남아 있는 구역은 삭제할 수 없습니다. 먼저 재고를 이동하거나 출고하세요.");
        }
        // 이동 이력이 참조하는 구역은 물리 삭제하면 FK가 훼손된다.
        // 재고가 0인 구역은 비활성화해 도면/입출고 후보에서만 제외한다.
        bin.changeActive(false);
    }

    private int[] findAvailableLayout(Long warehouseId, int width, int height) {
        if (width < 1 || height < 1 || width > FLOOR_COLUMNS
                || height > FLOOR_ROWS) {
            throw new IllegalArgumentException(
                    "도면 크기는 가로 1~26, 세로 1~14 범위로 입력하세요.");
        }
        List<WarehouseBin> existing = binRepository
                .findByWarehouseWarehouseIdOrderByBinCodeAsc(warehouseId);
        List<FacilityItem> facilities = warehouseFacilities();
        for (int y = 1; y <= FLOOR_ROWS - height + 1; y++) {
            for (int x = 1; x <= FLOOR_COLUMNS - width + 1; x++) {
                if (isLayoutAvailable(
                        x, y, width, height, existing, facilities)) {
                    return new int[] {x, y};
                }
            }
        }
        throw new IllegalStateException(
                "시설·기존 구역을 제외한 빈 도면 공간이 없습니다. "
                        + "구역 크기를 줄이거나 사용하지 않는 구역을 중지해 주세요.");
    }

    private boolean isLayoutAvailable(
            Long warehouseId,
            int x,
            int y,
            int width,
            int height) {
        return isLayoutAvailable(
                x, y, width, height,
                binRepository.findByWarehouseWarehouseIdOrderByBinCodeAsc(
                        warehouseId),
                warehouseFacilities());
    }

    private boolean isLayoutAvailable(
            int x,
            int y,
            int width,
            int height,
            List<WarehouseBin> existing,
            List<FacilityItem> facilities) {
        if (x < 1 || y < 1 || width < 1 || height < 1
                || x + width - 1 > FLOOR_COLUMNS
                || y + height - 1 > FLOOR_ROWS) {
            return false;
        }
        // 도면에 그려지는 구역만 장애물로 봅니다. 센터 간 이동 구역처럼
        // 물리 공간이 아닌 시스템 구역은 모달 도면에서 제외되므로,
        // 여기서 장애물로 세면 화면에 비어 보이는 칸이 저장 시 거부됩니다.
        boolean overlapsBin = existing.stream()
                .anyMatch(bin -> bin.isActive()
                        && bin.getPurpose().isPhysicalSpace()
                        && rectanglesOverlap(x, y, width, height, bin));
        if (overlapsBin) {
            return false;
        }
        return facilities.stream().noneMatch(facility ->
                rectanglesOverlap(x, y, width, height, facility));
    }

    private boolean rectanglesOverlap(int x, int y, int width, int height, WarehouseBin bin) {
        return x < bin.getPosX() + bin.getPosWidth()
                && x + width > bin.getPosX()
                && y < bin.getPosY() + bin.getPosHeight()
                && y + height > bin.getPosY();
    }

    private boolean rectanglesOverlap(
            int x,
            int y,
            int width,
            int height,
            FacilityItem facility) {
        return x < facility.posX() + facility.posWidth()
                && x + width > facility.posX()
                && y < facility.posY() + facility.posHeight()
                && y + height > facility.posY();
    }

    @Transactional
    public void updateBin(
            Long binId,
            String binCode,
            String zone,
            BinPurpose purpose,
            int maxCapacity,
            int posX,
            int posY,
            int posWidth,
            int posHeight,
            String memo,
            boolean active) {
        WarehouseBin bin = requiredBin(binId);
        if (bin.getPurpose().isSystemManaged()) {
            throw new IllegalStateException(
                    "센터 간 이동 구역은 시스템이 관리합니다.");
        }
        int currentQuantity = quantityInBin(binId);
        if ((long) maxCapacity * WarehouseBin.VERTICAL_STACKING_LEVELS
                < currentQuantity) {
            throw new IllegalArgumentException(
                    "최대 적재량을 현재 재고보다 작게 설정할 수 없습니다.");
        }
        bin.update(binCode, zone, purpose, maxCapacity, memo);
        bin.updateLayout(posX, posY, posWidth, posHeight);
        bin.changeActive(active);
    }

    @Transactional
    public ProductLot receive(
            Long existingLotId,
            Long productId,
            String lotNo,
            LocalDate manufacturedDate,
            LocalDate expirationDate,
            int quantity,
            Long binId,
            String memo,
            String operatorName) {
        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "입고 수량은 1포대 이상이어야 합니다.");
        }
        WarehouseBin bin = requiredOperationalBinForUpdate(binId);
        ensureCapacity(bin, quantity);

        ProductLot lot;
        if (existingLotId != null) {
            lot = requiredLot(existingLotId);
            lot.changeQuantity(quantity);
        } else {
            if (lotNo == null || lotNo.isBlank()
                    || lotRepository.existsByLotNo(lotNo.trim())) {
                throw new IllegalArgumentException(
                        "중복되지 않는 신규 LOT 번호를 입력해 주세요.");
            }
            if (manufacturedDate == null || expirationDate == null
                    || !expirationDate.isAfter(manufacturedDate)) {
                throw new IllegalArgumentException(
                        "유통기한은 제조일보다 늦어야 합니다.");
            }
            Product product = requiredProduct(productId);
            lot = lotRepository.save(new ProductLot(
                    product,
                    lotNo.trim().toUpperCase(Locale.ROOT),
                    manufacturedDate,
                    expirationDate,
                    0));
            product.addLot(lot);
            lot.changeQuantity(quantity);
        }
        ensureAnimalZone(bin, lot.getProduct());
        lot.getProduct().changeStock(quantity);
        lot.changeWarehouseLocation(
                bin.getWarehouse().getCode() + "-" + bin.getBinCode());
        addToBin(lot, bin, quantity);
        movementRepository.save(new WarehouseStockMovement(
                MovementType.INBOUND,
                lot,
                null,
                bin,
                quantity,
                null,
                normalizedMemo(memo, "WMS 입고"),
                normalizedOperator(operatorName),
                null));
        stockLogRepository.save(new StockLog(
                lot,
                1L,
                ChangeType.INBOUND,
                quantity,
                normalizedMemo(memo, "WMS 구역 입고")));
        adjustAllocation(
                bin.getWarehouse(), lot.getProduct(), quantity);
        return lot;
    }

    public record DemandInboundResult(
            String warehouseName,
            String animalType,
            String lotNo,
            int quantity,
            List<String> binCodes) {
    }

    public record AllocationInboundResult(
            String warehouseName,
            String productName,
            String lotNo,
            int quantity,
            List<String> binCodes) {
    }

    public record AllocationBalanceResult(
            String warehouseName,
            String productName,
            int previousQuantity,
            int targetQuantity,
            int changedQuantity) {

        public String actionLabel() {
            if (changedQuantity > 0) return "입고";
            if (changedQuantity < 0) return "감축 조정";
            return "유지";
        }
    }

    /** 협력 농장 수요로 계산된 권장 보유량에 실제 판매 가능 재고를 맞춘다. */
    @Transactional
    public AllocationBalanceResult balanceAllocationToTarget(
            Long allocationId,
            String operatorName) {
        WarehouseAllocation allocation = allocationRepository
                .findByIdForUpdate(allocationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "창고 상품 배치 계획을 찾을 수 없습니다."));
        Warehouse warehouse = allocation.getWarehouse();
        Product product = allocation.getProduct();
        int current = sellableStockQuery.sellableAtWarehouse(
                warehouse.getWarehouseId(), product.getProductId());
        int target = allocation.getTargetStockQuantity();
        allocation.refreshCurrentStock(current);

        if (current < target) {
            receiveAllocationReplenishment(
                    allocationId, normalizedOperator(operatorName));
        } else if (current > target) {
            reduceSellableStockToTarget(
                    allocation, current - target,
                    normalizedOperator(operatorName));
        }

        int balanced = sellableStockQuery.sellableAtWarehouse(
                warehouse.getWarehouseId(), product.getProductId());
        allocation.refreshCurrentStock(balanced);
        if (balanced != target) {
            throw new IllegalStateException(
                    "재고 조정 후 권장 보유량과 일치하지 않습니다. 현재 "
                            + balanced + "포 / 권장 " + target + "포");
        }
        return new AllocationBalanceResult(
                warehouse.getName(), product.getName(),
                current, target, target - current);
    }

    private void reduceSellableStockToTarget(
            WarehouseAllocation allocation,
            int quantity,
            String operatorName) {
        Warehouse warehouse = allocation.getWarehouse();
        Product product = allocation.getProduct();
        LocalDate sellableFrom = LocalDate.now().plusDays(
                SellableStockQuery.MINIMUM_SELLABLE_DAYS);
        List<ProductLot> lots = lotRepository
                .findByProductProductIdAndLotQuantityGreaterThanOrderByExpirationDateAsc(
                        product.getProductId(), 0)
                .stream()
                .filter(lot -> lot.getExpirationDate() == null
                        || !lot.getExpirationDate().isBefore(sellableFrom))
                .toList();
        Map<Long, Integer> removableByLot = sellableStockQuery.sellablePerLot(
                lots.stream().map(ProductLot::getLotId).toList(),
                warehouse.getWarehouseId());
        int available = removableByLot.values().stream()
                .mapToInt(Integer::intValue)
                .sum();
        if (available < quantity) {
            throw new IllegalStateException(
                    "주문 예약분을 제외한 조정 가능 재고가 부족합니다. 요청 "
                            + quantity + "포 / 가능 " + available + "포");
        }

        int remaining = quantity;
        for (ProductLot lot : lots) {
            if (remaining == 0) break;
            int lotAdjustment = Math.min(
                    remaining,
                    removableByLot.getOrDefault(lot.getLotId(), 0));
            if (lotAdjustment <= 0) continue;

            int lotRemaining = lotAdjustment;
            List<BinInventory> locations = binInventoryRepository
                    .findByLotLotIdAndQuantityGreaterThanOrderByBinBinCodeAsc(
                            lot.getLotId(), 0)
                    .stream()
                    .filter(location -> location.getBin().isActive())
                    .filter(location -> WmsAllocationPolicy.isAllocatable(
                            location.getBin()))
                    .filter(location -> location.getBin().getWarehouse()
                            .getWarehouseId().equals(warehouse.getWarehouseId()))
                    .toList();
            for (BinInventory location : locations) {
                if (lotRemaining == 0) break;
                int deduction = Math.min(lotRemaining, location.getQuantity());
                if (deduction <= 0) continue;
                location.subtract(deduction);
                movementRepository.save(new WarehouseStockMovement(
                        MovementType.ADJUSTMENT,
                        lot,
                        location.getBin(),
                        null,
                        deduction,
                        null,
                        "협력 농장 월 수요 기준 적정재고 조정",
                        operatorName,
                        null));
                lotRemaining -= deduction;
            }
            if (lotRemaining != 0) {
                throw new IllegalStateException(
                        lot.getLotNo() + " LOT의 구역 재고 조정에 실패했습니다.");
            }
            lot.changeQuantity(-lotAdjustment);
            product.changeStock(-lotAdjustment);
            stockLogRepository.save(new StockLog(
                    lot,
                    1L,
                    ChangeType.ADJUSTMENT,
                    -lotAdjustment,
                    "협력 농장 월 수요 기준 적정재고 조정"));
            remaining -= lotAdjustment;
        }
        if (remaining != 0) {
            throw new IllegalStateException(
                    "적정재고 감축 조정 중 " + remaining + "포를 처리하지 못했습니다.");
        }
    }

    /** 선택한 창고·상품의 판매 가능 재고를 권장 보유량까지 자동 입고한다. */
    @Transactional
    public AllocationInboundResult receiveAllocationReplenishment(
            Long allocationId,
            String operatorName) {
        WarehouseAllocation allocation = allocationRepository
                .findByIdForUpdate(allocationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "부족 재고 항목을 찾을 수 없습니다."));
        Warehouse warehouse = allocation.getWarehouse();
        Product product = allocation.getProduct();
        if (!warehouse.isActive() || !product.isActive()) {
            throw new IllegalStateException(
                    "운영 중인 창고와 판매 중인 상품만 보충할 수 있습니다.");
        }

        int current = sellableStockQuery.sellableAtWarehouse(
                warehouse.getWarehouseId(), product.getProductId());
        allocation.refreshCurrentStock(current);
        int quantity = allocation.getTargetStockQuantity() - current;
        if (quantity <= 0) {
            throw new IllegalStateException("이미 권장 재고를 충족한 상품입니다.");
        }

        // 월 수요·권장재고를 기준으로 고정 설계 용량을 먼저 반영한다.
        capacityPlanningService.resizeForWarehouse(warehouse.getWarehouseId());

        LocalDate sellableFrom = LocalDate.now().plusDays(
                SellableStockQuery.MINIMUM_SELLABLE_DAYS);
        ProductLot lot = lotRepository
                .findByProductProductIdOrderByExpirationDateAsc(
                        product.getProductId())
                .stream()
                .filter(candidate -> candidate.getExpirationDate() == null
                        || !candidate.getExpirationDate().isBefore(sellableFrom))
                .sorted(Comparator
                        .comparing((ProductLot candidate) ->
                                !lotLocatedInWarehouse(
                                        candidate.getLotId(),
                                        warehouse.getWarehouseId()))
                        .thenComparing(ProductLot::getExpirationDate,
                                Comparator.nullsLast(
                                        Comparator.naturalOrder())))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        product.getName()
                                + "에 입고 가능한 기존 LOT가 없습니다. 먼저 LOT를 등록해 주세요."));

        String preferredZone = WmsZonePolicy.zoneFor(product.getAnimalType());
        List<WarehouseBin> candidateSnapshot = binRepository
                .findByWarehouseWarehouseIdAndActiveTrueOrderByBinCodeAsc(
                        warehouse.getWarehouseId())
                .stream()
                .filter(bin -> bin.getPurpose() == BinPurpose.STORAGE)
                .filter(bin -> WmsZonePolicy.matches(bin, product))
                .sorted(Comparator
                        .comparing((WarehouseBin bin) ->
                                !binContainsLot(bin.getBinId(), lot.getLotId()))
                        .thenComparing(bin ->
                                !bin.getZone().equalsIgnoreCase(preferredZone))
                        .thenComparing(WarehouseBin::getBinCode))
                .toList();

        List<WarehouseBin> candidates = binRepository
                .findAllByBinIdInForUpdate(candidateSnapshot.stream()
                        .map(WarehouseBin::getBinId)
                        .toList())
                .stream()
                .sorted(Comparator
                        .comparing((WarehouseBin bin) ->
                                !binContainsLot(bin.getBinId(), lot.getLotId()))
                        .thenComparing(bin ->
                                !bin.getZone().equalsIgnoreCase(preferredZone))
                        .thenComparing(WarehouseBin::getBinCode))
                .toList();

        int available = candidates.stream()
                .mapToInt(bin -> Math.max(0,
                        bin.getEffectiveMaxCapacity()
                                - quantityInBin(bin.getBinId())))
                .sum();
        if (available < quantity) {
            throw new IllegalStateException(
                    warehouse.getName() + "의 " + product.getName()
                            + " 입고 가능 공간이 " + available
                            + "포뿐입니다. 계획 창고 용량을 초과하여 창고 확장이 필요합니다.");
        }

        int remaining = quantity;
        List<String> usedBins = new ArrayList<>();
        for (WarehouseBin bin : candidates) {
            if (remaining <= 0) break;
            int room = Math.max(0, bin.getEffectiveMaxCapacity()
                    - quantityInBin(bin.getBinId()));
            int inbound = Math.min(remaining, room);
            if (inbound <= 0) continue;
            receive(lot.getLotId(), null, null, null, null, inbound,
                    bin.getBinId(), "부족 재고 권장량 일괄 보충", operatorName);
            usedBins.add(bin.getBinCode() + " " + inbound + "포");
            remaining -= inbound;
        }

        return new AllocationInboundResult(
                warehouse.getName(), product.getName(), lot.getLotNo(),
                quantity, usedBins);
    }

    public Long recommendedDemandProductId(Long warehouseId, String animalType) {
        return selectDemandLot(warehouseId, animalType, LocalDate.now())
                .getProduct().getProductId();
    }

    /** 수요 부족분을 기존 LOT와 여유 구역에 자동 분할 입고한다. */
    @Transactional
    public DemandInboundResult receiveDemandReplenishment(
            Long warehouseId,
            String animalType,
            int quantity,
            String operatorName) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("일괄 입고 수량은 1포대 이상이어야 합니다.");
        }
        Warehouse warehouse = requiredWarehouse(warehouseId);
        String normalizedAnimal = normalizeDemandAnimal(animalType);
        LocalDate today = LocalDate.now();

        ProductLot lot = selectDemandLot(warehouseId, normalizedAnimal, today);
        String preferredZone = switch (normalizedAnimal) {
            case "소" -> "CT";
            case "돼지" -> "PG";
            case "조류(닭/오리)" -> "PL";
            default -> "";
        };
        List<WarehouseBin> candidates = binRepository
                .findByWarehouseWarehouseIdAndActiveTrueOrderByBinCodeAsc(warehouseId)
                .stream()
                .filter(bin -> bin.getPurpose() == BinPurpose.STORAGE
                        || bin.getPurpose() == BinPurpose.SHIPPING)
                .sorted(Comparator
                        .comparing((WarehouseBin bin) ->
                                !binContainsLot(bin.getBinId(), lot.getLotId()))
                        .thenComparing(bin -> !bin.getZone().equalsIgnoreCase(preferredZone))
                        .thenComparing(WarehouseBin::getBinCode))
                .toList();

        int available = candidates.stream()
                .mapToInt(bin -> Math.max(0,
                        bin.getEffectiveMaxCapacity()
                                - quantityInBin(bin.getBinId())))
                .sum();
        if (available < quantity) {
            throw new IllegalStateException(warehouse.getName() + "의 " + normalizedAnimal
                    + " 입고 가능 공간이 " + available + "포대뿐입니다. 구역 용량을 확보하세요.");
        }

        int remaining = quantity;
        List<String> usedBins = new ArrayList<>();
        for (WarehouseBin bin : candidates) {
            if (remaining <= 0) break;
            int room = Math.max(0, bin.getEffectiveMaxCapacity()
                    - quantityInBin(bin.getBinId()));
            int inbound = Math.min(remaining, room);
            if (inbound <= 0) continue;
            receive(lot.getLotId(), null, null, null, null, inbound,
                    bin.getBinId(), "수요 계획 일괄 입고", operatorName);
            usedBins.add(bin.getBinCode() + " " + inbound + "포");
            remaining -= inbound;
        }
        return new DemandInboundResult(
                warehouse.getName(), normalizedAnimal, lot.getLotNo(), quantity, usedBins);
    }

    private ProductLot selectDemandLot(
            Long warehouseId,
            String animalType,
            LocalDate today) {
        String normalizedAnimal = normalizeDemandAnimal(animalType);
        LocalDate sellableFrom = today.plusDays(
                SellableStockQuery.MINIMUM_SELLABLE_DAYS);
        return lots().stream()
                .filter(candidate -> candidate.getExpirationDate() == null
                        || !candidate.getExpirationDate().isBefore(sellableFrom))
                .filter(candidate -> normalizeDemandAnimal(
                        candidate.getProduct().getAnimalType()).equals(normalizedAnimal))
                .filter(candidate -> allocationRepository
                        .findByWarehouseWarehouseIdAndProductProductId(
                                warehouseId,
                                candidate.getProduct().getProductId())
                        .isPresent())
                .sorted(Comparator
                        .comparing((ProductLot candidate) ->
                                !lotLocatedInWarehouse(candidate.getLotId(), warehouseId))
                        .thenComparing(candidate -> !candidate.getProduct().isLowStock())
                        .thenComparingInt(ProductLot::getLotQuantity)
                        .thenComparing(ProductLot::getExpirationDate,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        normalizedAnimal
                                + " 축종에 연결할 유효한 기존 LOT 또는 취급 상품이 없습니다."));
    }

    private boolean lotLocatedInWarehouse(Long lotId, Long warehouseId) {
        return binInventoryRepository
                .findByLotLotIdAndQuantityGreaterThanOrderByBinBinCodeAsc(lotId, 0)
                .stream()
                .anyMatch(inventory -> inventory.getBin().getWarehouse()
                        .getWarehouseId().equals(warehouseId));
    }

    private boolean binContainsLot(Long binId, Long lotId) {
        return binInventoryRepository.findByLotLotIdAndBinBinId(lotId, binId)
                .filter(inventory -> inventory.getQuantity() > 0)
                .isPresent();
    }

    private String normalizeDemandAnimal(String value) {
        String animal = value == null ? "기타" : value.trim();
        if (animal.contains("소") || animal.contains("한우")) return "소";
        if (animal.contains("돼지") || animal.contains("양돈")) return "돼지";
        if (animal.contains("조류") || animal.contains("닭") || animal.contains("오리")
                || animal.contains("육계") || animal.contains("산란")) return "조류(닭/오리)";
        return animal;
    }

    @Transactional
    public ProductLot receiveScannedProduct(
            Long productId,
            LocalDate manufacturedDate,
            int quantity,
            Long binId,
            String memo,
            String operatorName) {
        Product product = requiredProduct(productId);
        LocalDate productionDate = manufacturedDate == null
                ? LocalDate.now()
                : manufacturedDate;
        LocalDate expirationDate = productionDate.plusMonths(
                product.getEffectiveShelfLifeMonths());
        String categoryCode = switch (product.getAnimalType()) {
            case "소" -> "CATTLE";
            case "돼지" -> "PIG";
            case "조류(닭/오리)" -> "BIRD";
            default -> "SUP";
        };
        String baseLotNo = "LOT-%s-%s-%03d".formatted(
                categoryCode,
                productionDate.toString().replace("-", ""),
                product.getProductId());
        String lotNo = baseLotNo;
        int suffix = 2;
        while (lotRepository.existsByLotNo(lotNo)) {
            lotNo = baseLotNo + "-" + suffix++;
        }
        return receive(
                null,
                productId,
                lotNo,
                productionDate,
                expirationDate,
                quantity,
                binId,
                memo,
                operatorName);
    }

    @Transactional
    public void ship(
            Long lotId,
            Long binId,
            int quantity,
            String memo,
            String operatorName) {
        ProductLot lot = requiredLot(lotId);
        WarehouseBin bin = requiredOperationalBin(binId);
        BinInventory inventory = requiredInventory(lotId, binId);
        if (!WmsAllocationPolicy.isAllocatable(bin)) {
            throw new IllegalArgumentException("보관·출고 대기 구역에 있는 재고만 출고할 수 있습니다.");
        }
        if (lot.getExpirationDate().isBefore(
                LocalDate.now().plusDays(SellableStockQuery.MINIMUM_SELLABLE_DAYS))) {
            throw new IllegalArgumentException(
                    "유통기한이 " + SellableStockQuery.MINIMUM_SELLABLE_DAYS
                            + "일 미만 남은 LOT는 출고할 수 없습니다.");
        }
        int reserved = inventoryService.reservedStockByLot()
                .getOrDefault(lotId, 0);
        int unreservedLotQuantity = Math.max(0, lot.getLotQuantity() - reserved);
        int available = Math.min(inventory.getQuantity(), unreservedLotQuantity);
        if (quantity <= 0 || available < quantity) {
            throw new IllegalArgumentException(
                    "주문 예약분을 제외한 출고 가능 재고가 부족합니다. "
                            + "요청 " + quantity + "포대 / 가능 " + available + "포대");
        }
        inventory.subtract(quantity);
        lot.changeQuantity(-quantity);
        lot.getProduct().changeStock(-quantity);
        String normalizedMemo = normalizedMemo(memo, "QR 스캔 출고");
        movementRepository.save(new WarehouseStockMovement(
                MovementType.OUTBOUND,
                lot,
                bin,
                null,
                quantity,
                null,
                normalizedMemo,
                normalizedOperator(operatorName),
                null));
        stockLogRepository.save(new StockLog(
                lot,
                1L,
                ChangeType.OUTBOUND,
                -quantity,
                normalizedMemo));
        adjustAllocation(
                bin.getWarehouse(), lot.getProduct(), -quantity);
    }

    @Transactional
    public List<OutboundAllocation> shipProductFefo(
            Long productId,
            int quantity,
            String memo,
            String operatorName) {
        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "출고 수량은 1포대 이상이어야 합니다.");
        }
        Product product = requiredProduct(productId);
        LocalDate sellableFrom = LocalDate.now()
                .plusDays(SellableStockQuery.MINIMUM_SELLABLE_DAYS);
        Map<Long, Integer> reservedByLot = inventoryService.reservedStockByLot();
        List<BinInventory> candidates = binInventoryRepository
                .findByLotProductProductIdAndQuantityGreaterThanOrderByBinBinCodeAsc(
                        productId,
                        0)
                .stream()
                .filter(inventory -> inventory.getBin().isActive())
                .filter(inventory -> WmsAllocationPolicy.isAllocatable(
                        inventory.getBin()))
                .filter(inventory -> !inventory.getLot()
                        .getExpirationDate().isBefore(sellableFrom))
                .sorted(Comparator
                        .comparing((BinInventory inventory) -> inventory
                                .getLot().getExpirationDate())
                        .thenComparing(inventory -> inventory.getBin()
                                .getWarehouse().getDisplayOrder())
                        .thenComparing(inventory -> inventory.getBin()
                                .getBinCode()))
                .toList();
        Map<Long, Integer> unreservedByLot = new LinkedHashMap<>();
        Map<BinInventory, Integer> availableByInventory = new LinkedHashMap<>();
        for (BinInventory inventory : candidates) {
            Long lotId = inventory.getLot().getLotId();
            int lotRemaining = unreservedByLot.computeIfAbsent(
                    lotId,
                    ignored -> Math.max(0, inventory.getLot().getLotQuantity()
                            - reservedByLot.getOrDefault(lotId, 0)));
            int available = Math.min(inventory.getQuantity(), lotRemaining);
            availableByInventory.put(inventory, available);
            unreservedByLot.put(lotId, lotRemaining - available);
        }
        int availableQuantity = availableByInventory.values().stream()
                .mapToInt(Integer::intValue).sum();
        if (availableQuantity < quantity) {
            throw new IllegalArgumentException(
                    product.getName() + "의 출고 가능 재고가 부족합니다. "
                            + "요청 " + quantity + "포대 / 가능 "
                            + availableQuantity + "포대");
        }

        int remaining = quantity;
        List<OutboundAllocation> allocations = new ArrayList<>();
        for (BinInventory inventory : candidates) {
            if (remaining == 0) {
                break;
            }
            int allocated = Math.min(
                    remaining,
                    availableByInventory.getOrDefault(inventory, 0));
            if (allocated == 0) {
                continue;
            }
            allocations.add(new OutboundAllocation(
                    inventory.getLot().getLotNo(),
                    inventory.getBin().getWarehouse().getName(),
                    inventory.getBin().getBinCode(),
                    inventory.getLot().getExpirationDate(),
                    allocated));
            ship(
                    inventory.getLot().getLotId(),
                    inventory.getBin().getBinId(),
                    allocated,
                    normalizedMemo(memo, "직접 출고 (FEFO)"),
                    operatorName);
            remaining -= allocated;
        }
        return List.copyOf(allocations);
    }

    public record OutboundAllocation(
            String lotNo,
            String warehouseName,
            String binCode,
            LocalDate expirationDate,
            int quantity) {
    }

    @Transactional
    public void move(
            Long lotId,
            Long sourceBinId,
            Long destinationBinId,
            int quantity,
            String memo,
            String operatorName) {
        if (sourceBinId.equals(destinationBinId)) {
            throw new IllegalArgumentException(
                    "출발 구역과 도착 구역이 같습니다.");
        }
        ProductLot lot = requiredLot(lotId);
        WarehouseBin source = requiredOperationalBin(sourceBinId);
        WarehouseBin destination = requiredOperationalBinForUpdate(destinationBinId);
        BinInventory sourceInventory = requiredInventory(lotId, sourceBinId);
        ensureAnimalZone(destination, lot.getProduct());
        int available = movableQuantity(
                lot, source, destination, sourceInventory);
        if (quantity <= 0 || available < quantity) {
            throw new IllegalArgumentException(
                    "이동 가능 재고가 부족합니다. "
                            + "요청 " + quantity + "포대 / 가능 " + available + "포대");
        }
        ensureCapacity(destination, quantity);
        sourceInventory.subtract(quantity);
        addToBin(lot, destination, quantity);
        lot.changeWarehouseLocation(
                destination.getWarehouse().getCode() + "-"
                        + destination.getBinCode());

        String operator = normalizedOperator(operatorName);
        String normalizedMemo = normalizedMemo(memo, "WMS 구역 이동");
        boolean sameWarehouse = source.getWarehouse().getWarehouseId()
                .equals(destination.getWarehouse().getWarehouseId());
        if (sameWarehouse) {
            movementRepository.save(new WarehouseStockMovement(
                    MovementType.MOVE,
                    lot,
                    source,
                    destination,
                    quantity,
                    null,
                    normalizedMemo,
                    operator,
                    null));
            // 같은 창고 안이라도 판매 구역과 그 밖을 넘나들면 판매 가능
            // 수량이 달라집니다. 갱신하지 않으면 배정 캐시가 낡습니다.
            adjustAllocation(source.getWarehouse(), lot.getProduct(), 0);
            return;
        }

        WarehouseBin transit = binRepository
                .findFirstByWarehouseWarehouseIdAndPurposeAndActiveTrueOrderByBinCodeAsc(
                        source.getWarehouse().getWarehouseId(),
                        BinPurpose.IN_TRANSIT)
                .orElseThrow(() -> new IllegalStateException(
                        "출발 센터의 이동 중 구역이 없습니다."));
        movementRepository.save(new WarehouseStockMovement(
                MovementType.TRANSFER_OUT,
                lot,
                source,
                transit,
                quantity,
                null,
                normalizedMemo,
                operator,
                null));
        movementRepository.save(new WarehouseStockMovement(
                MovementType.TRANSFER_IN,
                lot,
                transit,
                destination,
                quantity,
                null,
                normalizedMemo,
                operator,
                null));
        adjustAllocation(
                source.getWarehouse(), lot.getProduct(), -quantity);
        adjustAllocation(
                destination.getWarehouse(), lot.getProduct(), quantity);
    }

    @Transactional
    public void dispose(
            Long lotId,
            Long binId,
            int quantity,
            DisposalReason reason,
            String memo,
            String operatorName) {
        ProductLot lot = requiredLot(lotId);
        WarehouseBin bin = requiredOperationalBin(binId);
        BinInventory inventory = requiredInventory(lotId, binId);
        int available = unreservedQuantityInBin(lot, bin, inventory);
        if (quantity <= 0 || available < quantity) {
            throw new IllegalArgumentException(
                    "주문 예약분을 제외한 폐기 가능 재고가 부족합니다. "
                            + "요청 " + quantity + "포대 / 가능 " + available + "포대");
        }
        if (reason == null) {
            throw new IllegalArgumentException("폐기 사유를 선택해 주세요.");
        }
        inventory.subtract(quantity);
        lot.changeQuantity(-quantity);
        lot.getProduct().changeStock(-quantity);
        movementRepository.save(new WarehouseStockMovement(
                MovementType.DISPOSAL,
                lot,
                bin,
                null,
                quantity,
                reason,
                normalizedMemo(memo, reason.getLabel()),
                normalizedOperator(operatorName),
                null));
        stockLogRepository.save(new StockLog(
                lot,
                1L,
                ChangeType.DISPOSAL,
                -quantity,
                "WMS 폐기: " + reason.getLabel()));
        adjustAllocation(
                bin.getWarehouse(), lot.getProduct(), -quantity);
    }

    @Transactional
    public void synchronizeAll(String operatorName) {
        List<Warehouse> warehouses = warehouses();
        if (warehouses.isEmpty()) {
            throw new IllegalStateException("활성 창고가 없습니다.");
        }
        for (ProductLot lot : lots()) {
            List<BinInventory> locations = binInventoryRepository
                    .findByLotLotIdAndQuantityGreaterThanOrderByBinBinCodeAsc(
                            lot.getLotId(), 0);
            int located = locations.stream()
                    .mapToInt(BinInventory::getQuantity)
                    .sum();
            int difference = lot.getLotQuantity() - located;
            if (difference > 0) {
                distributeMissingStock(
                        lot,
                        locations,
                        warehouses,
                        difference,
                        operatorName);
            } else if (difference < 0) {
                int excess = -difference;
                for (BinInventory location : locations) {
                    int deduction = Math.min(excess, location.getQuantity());
                    if (deduction == 0) {
                        continue;
                    }
                    location.subtract(deduction);
                    movementRepository.save(new WarehouseStockMovement(
                            MovementType.ADJUSTMENT,
                            lot,
                            location.getBin(),
                            null,
                            deduction,
                            null,
                            "LOT 실재고 기준 위치 재고 자동 보정",
                            normalizedOperator(operatorName),
                            null));
                    excess -= deduction;
                    if (excess == 0) {
                        break;
                    }
                }
            }
        }

        for (Product product : products()) {
            int lotStock = lotRepository
                    .findByProductProductIdOrderByExpirationDateAsc(
                            product.getProductId())
                    .stream()
                    .mapToInt(ProductLot::getLotQuantity)
                    .sum();
            int difference = lotStock - product.getTotalStock();
            if (difference != 0) {
                product.changeStock(difference);
            }
        }

        // 계획 장부의 현재고는 실제 판매 가능한 구역 재고에서 파생한다.
        allocationRepository
                .findAllByOrderByWarehouseDisplayOrderAscProductAnimalTypeAscProductNameAsc()
                .forEach(allocation -> adjustAllocation(
                        allocation.getWarehouse(), allocation.getProduct(), 0));
    }

    private Product requiredProduct(Long productId) {
        if (productId == null) {
            throw new IllegalArgumentException("상품을 선택해 주세요.");
        }
        return productRepository.findByProductIdAndActiveTrue(productId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "상품을 찾을 수 없습니다."));
    }

    private ProductLot requiredLot(Long lotId) {
        if (lotId == null) {
            throw new IllegalArgumentException("LOT를 선택해 주세요.");
        }
        return lotRepository.findDetailByLotId(lotId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "LOT를 찾을 수 없습니다."));
    }

    private Warehouse requiredWarehouse(Long warehouseId) {
        if (warehouseId == null) {
            throw new IllegalArgumentException("창고를 선택해 주세요.");
        }
        return warehouseRepository.findById(warehouseId)
                .filter(Warehouse::isActive)
                .orElseThrow(() -> new IllegalArgumentException(
                        "활성 창고를 찾을 수 없습니다."));
    }

    private WarehouseBin requiredBin(Long binId) {
        if (binId == null) {
            throw new IllegalArgumentException("창고 구역을 선택해 주세요.");
        }
        return binRepository.findById(binId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "창고 구역을 찾을 수 없습니다."));
    }

    private WarehouseBin requiredOperationalBin(Long binId) {
        WarehouseBin bin = requiredBin(binId);
        if (!bin.isActive() || bin.getPurpose().isSystemManaged()) {
            throw new IllegalStateException(
                    "현재 작업에 사용할 수 없는 구역입니다.");
        }
        return bin;
    }

    private WarehouseBin requiredOperationalBinForUpdate(Long binId) {
        WarehouseBin bin = binRepository.findByBinIdForUpdate(binId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "창고 구역을 찾을 수 없습니다."));
        if (!bin.isActive() || bin.getPurpose().isSystemManaged()) {
            throw new IllegalStateException("현재 작업에 사용할 수 없는 구역입니다.");
        }
        return bin;
    }

    private BinInventory requiredInventory(Long lotId, Long binId) {
        return binInventoryRepository
                .findByLotLotIdAndBinBinId(lotId, binId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "선택한 구역에 해당 LOT 재고가 없습니다."));
    }

    private int quantityInBin(Long binId) {
        return binInventoryRepository.findByBinBinId(binId)
                .stream()
                .mapToInt(BinInventory::getQuantity)
                .sum();
    }

    private void ensureCapacity(WarehouseBin bin, int addQuantity) {
        if (!bin.canAccept(quantityInBin(bin.getBinId()), addQuantity)) {
            throw new IllegalStateException(
                    bin.getDisplayName() + "의 최대 적재량을 초과합니다.");
        }
    }

    private void addToBin(
            ProductLot lot,
            WarehouseBin bin,
            int quantity) {
        BinInventory inventory = binInventoryRepository
                .findByLotLotIdAndBinBinId(
                        lot.getLotId(), bin.getBinId())
                .orElseGet(() -> binInventoryRepository.save(
                        new BinInventory(lot, bin, 0)));
        inventory.add(quantity);
    }

    private WarehouseBin defaultStorageBin(Long warehouseId) {
        return binRepository
                .findFirstByWarehouseWarehouseIdAndPurposeAndActiveTrueOrderByBinCodeAsc(
                        warehouseId,
                        BinPurpose.STORAGE)
                .orElseThrow(() -> new IllegalStateException(
                        "창고에 활성 보관 구역이 없습니다."));
    }

    private void distributeMissingStock(
            ProductLot lot,
            List<BinInventory> locations,
            List<Warehouse> warehouses,
            int quantity,
            String operatorName) {
        Map<Long, WarehouseBin> candidates = new LinkedHashMap<>();
        locations.stream()
                .map(BinInventory::getBin)
                .filter(WarehouseBin::isActive)
                .filter(bin -> bin.getPurpose() == BinPurpose.STORAGE)
                .filter(bin -> isProductZone(bin, lot.getProduct()))
                .forEach(bin -> candidates.put(bin.getBinId(), bin));

        int firstWarehouse = Math.floorMod(
                lot.getLotId().intValue() - 1,
                warehouses.size());
        for (int offset = 0; offset < warehouses.size(); offset++) {
            Warehouse warehouse = warehouses.get(
                    (firstWarehouse + offset) % warehouses.size());
            binRepository
                    .findByWarehouseWarehouseIdAndActiveTrueOrderByBinCodeAsc(
                            warehouse.getWarehouseId())
                    .stream()
                    .filter(bin -> bin.getPurpose() == BinPurpose.STORAGE)
                    .filter(bin -> isProductZone(bin, lot.getProduct()))
                    .forEach(bin -> candidates.putIfAbsent(
                            bin.getBinId(), bin));
        }

        int remaining = quantity;
        for (WarehouseBin target : candidates.values()) {
            int available = target.getEffectiveMaxCapacity()
                    - quantityInBin(target.getBinId());
            if (available <= 0) {
                continue;
            }
            int allocated = Math.min(remaining, available);
            addToBin(lot, target, allocated);
            movementRepository.save(new WarehouseStockMovement(
                    MovementType.ADJUSTMENT,
                    lot,
                    null,
                    target,
                    allocated,
                    null,
                    "LOT 실재고 기준 위치 재고 자동 보정",
                    normalizedOperator(operatorName),
                    null));
            remaining -= allocated;
            if (remaining == 0) {
                return;
            }
        }
        throw new IllegalStateException(
                lot.getLotNo() + " LOT를 배치할 창고 여유 공간이 부족합니다."
                        + " (미배치 " + remaining + "포대)");
    }

    /**
     * 해당 구역에서 빼낼 수 있는 수량입니다. 주문 예약분은 남겨 둡니다.
     *
     * <p>판매 구역(보관·출고 대기)의 재고만 고객에게 팔 수 있으므로, 그 구역에서
     * 뺄 때는 배정 창고의 판매 가능 수량을 넘지 않아야 합니다. LOT 총잔량으로
     * 판단하면 검수·입고 대기·운송 중 구역 재고까지 포함되어, 판매 구역 재고를
     * 예약분 밑으로 떨어뜨리는 작업이 통과합니다. 그러면 결손이 판매가능 계산의
     * 하한 처리에 묻혀 보이지 않다가 출고 확정 단계에서 실패합니다.
     *
     * <p>반대로 판매 구역이 아닌 곳의 재고는 애초에 예약 대상이 아니므로 그
     * 구역의 실제 수량까지 다룰 수 있습니다.
     */
    /**
     * 구역별 재고의 예약 제외 이동 가능 수량을 한 번에 계산합니다.
     *
     * <p>화면에 보유 수량만 보이면 관리자가 예약분까지 옮기려 했다가 제출
     * 후에야 실패를 알게 되고, 화면에는 원인이 없습니다.
     *
     * <p>도착 구역은 아직 정해지지 않았으므로 가장 보수적인 값을 계산합니다.
     * 즉 판매 구역 밖으로 빼거나 다른 창고로 옮길 때 허용되는 수량입니다.
     * 같은 창고의 판매 구역 사이 이동은 보유 수량 전부가 가능합니다.
     *
     * <p>행마다 조회하지 않도록 창고별로 묶어 LOT 판매 가능 수량을 한 번에
     * 가져옵니다.
     */
    @Transactional(readOnly = true)
    public Map<Long, Integer> movableQuantities(
            List<BinInventory> inventories) {
        Map<Long, Integer> movable = new HashMap<>();
        if (inventories == null || inventories.isEmpty()) {
            return movable;
        }

        // 판매 구역이 아닌 곳의 재고는 예약 대상이 아닙니다.
        inventories.stream()
                .filter(inventory -> !WmsAllocationPolicy.isAllocatable(
                        inventory.getBin()))
                .forEach(inventory -> movable.put(
                        inventory.getBinInventoryId(),
                        inventory.getQuantity()));

        inventories.stream()
                .filter(inventory -> WmsAllocationPolicy.isAllocatable(
                        inventory.getBin()))
                .collect(Collectors.groupingBy(inventory -> inventory.getBin()
                        .getWarehouse().getWarehouseId()))
                .forEach((warehouseId, rows) -> {
                    Map<Long, Integer> sellableByLot = sellableStockQuery
                            .sellablePerLot(
                                    rows.stream()
                                            .map(row -> row.getLot().getLotId())
                                            .distinct()
                                            .toList(),
                                    warehouseId);
                    rows.forEach(row -> movable.put(
                            row.getBinInventoryId(),
                            Math.min(
                                    row.getQuantity(),
                                    sellableByLot.getOrDefault(
                                            row.getLot().getLotId(), 0))));
                });
        return movable;
    }

    /**
     * 구역 간 이동에 쓸 수 있는 수량입니다.
     *
     * <p>출발 구역이 판매 구역이 아니면 애초에 예약 대상이 아니므로 실제
     * 수량 전부를 옮길 수 있습니다.
     *
     * <p>출발과 도착이 모두 같은 창고의 판매 구역이면 옮긴 뒤에도 판매
     * 가능성이 그대로 유지되므로 예약분을 막을 이유가 없습니다. 출고 준비를
     * 위한 피킹 이동(보관 → 출고 대기)이 여기에 해당합니다. 이전에는 도착
     * 용도를 보지 않고 예약분을 전면 차단해 정상적인 피킹 이동이 막혔습니다.
     *
     * <p>그 밖의 경우, 즉 판매 구역 밖으로 빼거나 다른 창고로 옮기는
     * 경우에는 배정 창고의 판매 가능 수량이 줄어들기 때문에 예약분을
     * 보호해야 합니다.
     */
    public int movableQuantity(
            ProductLot lot,
            WarehouseBin source,
            WarehouseBin destination,
            BinInventory sourceInventory) {
        if (!WmsAllocationPolicy.isAllocatable(source)) {
            return sourceInventory.getQuantity();
        }
        boolean sameWarehouse = source.getWarehouse().getWarehouseId()
                .equals(destination.getWarehouse().getWarehouseId());
        if (sameWarehouse && WmsAllocationPolicy.isAllocatable(destination)) {
            return sourceInventory.getQuantity();
        }
        return unreservedQuantityInBin(lot, source, sourceInventory);
    }

    private int unreservedQuantityInBin(
            ProductLot lot,
            WarehouseBin bin,
            BinInventory inventory) {
        if (!WmsAllocationPolicy.isAllocatable(bin)) {
            return inventory.getQuantity();
        }
        int sellable = sellableStockQuery
                .sellablePerLot(
                        List.of(lot.getLotId()),
                        bin.getWarehouse().getWarehouseId())
                .getOrDefault(lot.getLotId(), 0);
        return Math.min(inventory.getQuantity(), sellable);
    }

    private void adjustAllocation(
            Warehouse warehouse,
            Product product,
            int quantity) {
        WarehouseAllocation allocation = allocationRepository
                .findByWarehouseWarehouseIdAndProductProductId(
                        warehouse.getWarehouseId(),
                        product.getProductId())
                .orElseGet(() -> allocationRepository.save(
                        new WarehouseAllocation(warehouse, product, 0, 0)));
        int sellable = sellableStockQuery.sellableAtWarehouse(
                warehouse.getWarehouseId(), product.getProductId());
        allocation.refreshCurrentStock(sellable);
    }

    private void ensureAnimalZone(WarehouseBin bin, Product product) {
        WmsZonePolicy.requireMatch(bin, product);
    }

    private boolean isProductZone(WarehouseBin bin, Product product) {
        return WmsZonePolicy.matches(bin, product);
    }

    private String normalizedOperator(String value) {
        return value == null || value.isBlank()
                ? "관리자"
                : value.trim();
    }

    private String normalizedMemo(String value, String fallback) {
        return value == null || value.isBlank()
                ? fallback
                : value.trim();
    }
}
