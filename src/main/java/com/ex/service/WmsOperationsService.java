package com.ex.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
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

    private static final int AUTO_BIN_CAPACITY_PER_CELL = 500;

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
            String rawValue,
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
            String binCode,
            String locationLabel,
            String statusLabel,
            String statusBadgeClass,
            int loadedQuantity,
            int maxCapacity,
            int usageRate,
            int remainingCapacity) {
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
    private final BarcodeService barcodeService;
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
        return new Overview(
                warehouses().size(),
                bins().size(),
                (int) binQuantities.values().stream()
                        .filter(quantity -> quantity > 0)
                        .count(),
                binQuantities.values().stream()
                        .mapToInt(Integer::intValue)
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
        List<WarehouseBin> warehouseBins = bins(warehouseId).stream()
                .filter(bin -> bin.getPurpose().isPhysicalSpace())
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
                binItems.stream()
                        .filter(item -> !item.bin().isActive())
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
                        bin.getBinCode(),
                        bin.getLocationLabel(),
                        mapItem.statusLabel(),
                        mapItem.statusBadgeClass(),
                        quantity,
                        effectiveCapacity,
                        usageRate,
                        mapItem.remainingCapacity()),
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

    public Map<Long, String> lotBarcodes() {
        return lots().stream().collect(Collectors.toMap(
                ProductLot::getLotId,
                lot -> barcodeService.code39DataUri(lot.getLotNo()),
                (left, right) -> left,
                LinkedHashMap::new));
    }

    public List<LotLabel> lotLabels() {
        LocalDate today = LocalDate.now();
        return lots().stream()
                .map(lot -> new LotLabel(
                        lot,
                        lot.getLotNo(),
                        productCode(lot.getProduct()),
                        barcodeService.qrCodeDataUri(
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
                            barcodeService.qrCodeDataUri(
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

    public ScanResult scan(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException(
                    "LOT 번호, 품목 코드 또는 구역 코드를 스캔해 주세요.");
        }
        String normalized = rawValue.trim().toUpperCase(Locale.ROOT);
        String value = normalized
                .replaceFirst("^(LOT|BIN|PRODUCT):", "")
                .trim();
        Optional<ProductLot> lot = lotRepository.findByLotNo(value);
        if (lot.isPresent()) {
            ProductLot found = lot.get();
            List<BinInventory> inventories = binInventoryRepository
                    .findByLotLotIdAndQuantityGreaterThanOrderByBinBinCodeAsc(
                            found.getLotId(), 0);
            int located = inventories.stream()
                    .mapToInt(BinInventory::getQuantity)
                    .sum();
            return new ScanResult(
                    rawValue,
                    "LOT",
                    found.getLotNo(),
                    found.getProduct().getName()
                            + " · LOT 재고 " + found.getLotQuantity()
                            + "포대",
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

        Optional<Product> product = products().stream()
                .filter(item -> productCode(item).equalsIgnoreCase(value))
                .findFirst();
        if (product.isPresent()) {
            Product found = product.get();
            List<BinInventory> inventories = binInventoryRepository
                    .findByLotProductProductIdAndQuantityGreaterThanOrderByBinBinCodeAsc(
                            found.getProductId(), 0);
            return new ScanResult(
                    rawValue,
                    "PRODUCT",
                    productCode(found),
                    found.getName() + " · 전체 재고 "
                            + found.getTotalStock() + "포대",
                    null,
                    null,
                    found.getProductId(),
                    productCode(found),
                    found,
                    null,
                    inventories,
                    found.getTotalStock(),
                    inventories.stream()
                            .map(inventory -> inventory.getBin().getBinId())
                            .distinct()
                            .toList()
                            .size());
        }
        Optional<WarehouseBin> bin = bins().stream()
                .filter(item -> item.getBinCode().equalsIgnoreCase(value)
                        || (item.getWarehouse().getCode() + "-"
                                + item.getBinCode()).equalsIgnoreCase(value))
                .findFirst();
        if (bin.isPresent()) {
            WarehouseBin found = bin.get();
            int quantity = binInventoryRepository
                    .findByBinBinId(found.getBinId())
                    .stream()
                    .mapToInt(BinInventory::getQuantity)
                    .sum();
            return new ScanResult(
                    rawValue,
                    "BIN",
                    found.getDisplayName(),
                    found.getPurpose().getLabel()
                            + " · 현재 " + quantity + "/"
                            + found.getEffectiveMaxCapacity() + "포대",
                    null,
                    found.getBinId(),
                    null,
                    null,
                    null,
                    null,
                    binInventoryRepository.findByBinBinId(found.getBinId()),
                    quantity,
                    1);
        }
        return new ScanResult(
                rawValue,
                "UNKNOWN",
                "일치하는 데이터 없음",
                "LOT 번호, 품목 코드와 구역 코드를 확인해 주세요.",
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
        int width = Math.min(26, (int) Math.ceil(Math.sqrt(requiredCells)));
        int height = (int) Math.ceil(requiredCells / (double) width);
        int[] layout;
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
        if (width < 1 || height < 1 || width > 26 || height > 16) {
            throw new IllegalArgumentException("도면 크기는 가로 1~26, 세로 1~16 범위로 입력하세요.");
        }
        List<WarehouseBin> existing = binRepository
                .findByWarehouseWarehouseIdOrderByBinCodeAsc(warehouseId);
        for (int y = 1; y <= 16 - height + 1; y++) {
            for (int x = 1; x <= 26 - width + 1; x++) {
                int candidateX = x;
                int candidateY = y;
                boolean overlaps = existing.stream().anyMatch(bin ->
                        bin.isActive() && rectanglesOverlap(candidateX, candidateY, width, height, bin));
                if (!overlaps) return new int[] {candidateX, candidateY};
            }
        }
        throw new IllegalStateException("현재 창고 도면에 겹치지 않는 빈 구역이 없습니다. 기존 구역 크기를 줄이거나 위치를 조정하세요.");
    }

    private boolean rectanglesOverlap(int x, int y, int width, int height, WarehouseBin bin) {
        return x < bin.getPosX() + bin.getPosWidth()
                && x + width > bin.getPosX()
                && y < bin.getPosY() + bin.getPosHeight()
                && y + height > bin.getPosY();
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
        allocation.adjustCurrentStock(current);
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
        String normalizedMemo = normalizedMemo(memo, "바코드 스캔 출고");
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
        int reserved = inventoryService.reservedStockByLot()
                .getOrDefault(lotId, 0);
        int available = Math.min(
                sourceInventory.getQuantity(),
                Math.max(0, lot.getLotQuantity() - reserved));
        if (quantity <= 0 || available < quantity) {
            throw new IllegalArgumentException(
                    "주문 예약분을 제외한 이동 가능 재고가 부족합니다.");
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
        int reserved = inventoryService.reservedStockByLot()
                .getOrDefault(lotId, 0);
        int available = Math.min(
                inventory.getQuantity(),
                Math.max(0, lot.getLotQuantity() - reserved));
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
        allocation.adjustCurrentStock(sellable);
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
