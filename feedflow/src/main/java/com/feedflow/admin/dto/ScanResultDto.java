package com.feedflow.admin.dto;

import com.feedflow.domain.Inventory;
import com.feedflow.domain.Product;
import com.feedflow.domain.ProductLot;
import com.feedflow.domain.WarehouseBin;

import java.time.LocalDate;
import java.util.List;

/**
 * 바코드 스캔 결과 (JSON 응답 전용).
 *
 * @param scanType      인식 유형 (LOT = 로트번호, PRODUCT = 품목코드)
 * @param code          정규화된 스캔 코드
 * @param product       품목 정보
 * @param lot           로트 정보 (품목코드 스캔 시 null)
 * @param stocks        구역별 재고 현황
 * @param totalQuantity 재고 합계 (로트 스캔 시 해당 로트 합계, 품목 스캔 시 품목 전체 재고)
 */
public record ScanResultDto(
        ScanType scanType,
        String code,
        ProductInfo product,
        LotInfo lot,
        List<StockLocation> stocks,
        int totalQuantity
) {

    public enum ScanType {
        LOT, PRODUCT
    }

    /* ------------------------------------------------------------------
     * 중첩 응답 타입
     * ------------------------------------------------------------------ */

    public record ProductInfo(
            Long productId,
            String productCode,
            String name,
            String animalType,
            Integer weightKg,
            Long price,
            Integer totalStock,
            Integer safetyStock,
            Integer shelfLifeDays,
            boolean active,
            boolean belowSafetyStock
    ) {
        public static ProductInfo from(Product product) {
            return new ProductInfo(
                    product.getProductId(),
                    product.getProductCode(),
                    product.getName(),
                    product.getAnimalType().getDescription(),
                    product.getWeightKg(),
                    product.getPrice(),
                    product.getTotalStock(),
                    product.getSafetyStock(),
                    product.getShelfLifeDays(),
                    product.isActive(),
                    product.isBelowSafetyStock());
        }
    }

    public record LotInfo(
            Long lotId,
            String lotNo,
            LocalDate manufacturedDate,
            LocalDate expirationDate,
            long remainingDays,
            boolean expired,
            Integer lotQuantity
    ) {
        public static LotInfo of(ProductLot lot, LocalDate today) {
            return new LotInfo(
                    lot.getLotId(),
                    lot.getLotNo(),
                    lot.getManufacturedDate(),
                    lot.getExpirationDate(),
                    lot.daysUntilExpiration(today),
                    lot.isExpired(today),
                    lot.getLotQuantity());
        }
    }

    public record StockLocation(
            Long binId,
            String binCode,
            String locationLabel,
            String lotNo,
            LocalDate expirationDate,
            long remainingDays,
            Integer quantity
    ) {
        /** lot / bin 이 초기화된 Inventory 로부터 생성 */
        public static StockLocation of(Inventory inventory, LocalDate today) {
            WarehouseBin bin = inventory.getBin();
            ProductLot lot = inventory.getLot();

            return new StockLocation(
                    bin.getBinId(),
                    bin.getBinCode(),
                    bin.locationLabel(),
                    lot.getLotNo(),
                    lot.getExpirationDate(),
                    lot.daysUntilExpiration(today),
                    inventory.getQuantity());
        }
    }

    /* ------------------------------------------------------------------
     * 정적 팩토리
     * ------------------------------------------------------------------ */

    /** 로트번호를 스캔한 경우 */
    public static ScanResultDto ofLot(ProductLot lot,
                                      List<Inventory> inventories,
                                      LocalDate today) {
        List<StockLocation> stocks = inventories.stream()
                .map(inventory -> StockLocation.of(inventory, today))
                .toList();

        int total = stocks.stream()
                .mapToInt(stock -> stock.quantity() == null ? 0 : stock.quantity())
                .sum();

        return new ScanResultDto(
                ScanType.LOT,
                lot.getLotNo(),
                ProductInfo.from(lot.getProduct()),
                LotInfo.of(lot, today),
                stocks,
                total);
    }

    /** 품목코드를 스캔한 경우 */
    public static ScanResultDto ofProduct(Product product,
                                          List<Inventory> inventories,
                                          LocalDate today) {
        List<StockLocation> stocks = inventories.stream()
                .map(inventory -> StockLocation.of(inventory, today))
                .toList();

        return new ScanResultDto(
                ScanType.PRODUCT,
                product.getProductCode(),
                ProductInfo.from(product),
                null,
                stocks,
                product.getTotalStock() == null ? 0 : product.getTotalStock());
    }
}
