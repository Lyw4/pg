package com.feedflow.admin.dto;

import com.feedflow.domain.Inventory;
import com.feedflow.domain.Product;
import com.feedflow.domain.ProductLot;
import com.feedflow.domain.WarehouseBin;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 재고 현황 목록 행 (로트 × 구역).
 * 코딩 규칙 7번에 따라 imageUrl / description 은 포함하지 않는다.
 */
@Getter
@Builder
public class InventoryDto {

    private final Long inventoryId;

    /* 품목 */
    private final Long productId;
    private final String productCode;
    private final String productName;
    private final String animalType;

    /* 로트 */
    private final Long lotId;
    private final String lotNo;
    private final LocalDate manufacturedDate;
    private final LocalDate expirationDate;

    /** 유통기한까지 남은 일수 (음수면 이미 만료) */
    private final long remainingDays;
    private final boolean expired;

    /* 구역 */
    private final Long binId;
    private final String binCode;
    private final String locationLabel;

    /* 수량 */
    private final Integer quantity;
    private final LocalDateTime updatedAt;

    /**
     * @param inventory fetch join 으로 lot / product / bin 이 초기화된 상태여야 한다.
     * @param today     D-Day 계산 기준일
     */
    public static InventoryDto of(Inventory inventory, LocalDate today) {
        ProductLot lot = inventory.getLot();
        Product product = lot.getProduct();
        WarehouseBin bin = inventory.getBin();

        return InventoryDto.builder()
                .inventoryId(inventory.getInventoryId())
                .productId(product.getProductId())
                .productCode(product.getProductCode())
                .productName(product.getName())
                .animalType(product.getAnimalType())
                .lotId(lot.getLotId())
                .lotNo(lot.getLotNo())
                .manufacturedDate(lot.getManufacturedDate())
                .expirationDate(lot.getExpirationDate())
                .remainingDays(lot.daysUntilExpiration(today))
                .expired(lot.isExpired(today))
                .binId(bin.getBinId())
                .binCode(bin.getBinCode())
                .locationLabel(bin.locationLabel())
                .quantity(inventory.getQuantity())
                .updatedAt(inventory.getUpdatedAt())
                .build();
    }

    /** D-Day 표기 (만료된 로트는 경과일로 표기) */
    public String getDDayLabel() {
        if (remainingDays < 0) {
            return "만료 " + Math.abs(remainingDays) + "일 경과";
        }
        return "D-" + remainingDays;
    }

    /** 유통기한 상태 뱃지 클래스 */
    public String getDDayBadgeClass() {
        if (remainingDays < 0) {
            return "bg-dark";
        }
        if (remainingDays <= 7) {
            return "bg-danger";
        }
        if (remainingDays <= 30) {
            return "bg-warning text-dark";
        }
        return "bg-light text-dark border";
    }
}
