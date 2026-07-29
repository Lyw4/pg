package com.feedflow.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.feedflow.common.util.DDay;
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
                .animalType(product.getAnimalType().getDescription())
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

    /**
     * D-Day 표기 (만료된 로트는 경과일로 표기).
     * <p>
     * {@code @JsonProperty} 를 명시한 이유 — Jackson 은 {@code getDDayLabel} 처럼
     * 접두사 뒤에 대문자가 연속되면 {@code ddayLabel} 로 이름을 바꿔버린다.
     * 카멜 표기법(camelCase) 규칙에 맞게 JSON 키를 고정한다.
     */
    @JsonProperty("dDayLabel")
    public String getDDayLabel() {
        return DDay.label(remainingDays);
    }

    /** 유통기한 상태 뱃지 클래스 */
    @JsonProperty("dDayBadgeClass")
    public String getDDayBadgeClass() {
        return DDay.badgeClass(remainingDays);
    }
}
