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

    /* 물류센터 */
    private final Long centerId;
    private final String centerName;

    /* 구역 */
    private final Long binId;
    private final String binCode;

    /**
     * 센터명까지 포함한 전체 위치 (예: 제1창고 · A구역 · 1랙 · 2단).
     * <p>
     * 센터를 별도 컬럼으로 보여주지 않는 화면(바코드 스캔 결과 등)에서 쓴다.
     */
    private final String locationLabel;

    /**
     * 센터 안에서의 위치만 (예: A구역 · 1랙 · 2단).
     * <p>
     * 재고 현황 목록처럼 <b>센터를 이미 별도 컬럼으로 보여주는</b> 화면에서 쓴다.
     * 여기서 {@link #locationLabel} 을 쓰면 센터명이 한 행에 두 번 나온다.
     */
    private final String binLocationLabel;

    /* 수량 */
    private final Integer quantity;
    private final LocalDateTime updatedAt;

    /**
     * @param inventory fetch join 으로 lot / product / bin / <b>bin.center</b> 가
     *                  초기화된 상태여야 한다. 센터가 지연 로딩으로 남아 있으면
     *                  변환하는 행마다 센터 조회 쿼리가 한 번씩 더 나간다.
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
                .centerId(bin.centerId())
                .centerName(bin.centerName())
                .binId(bin.getBinId())
                .binCode(bin.getBinCode())
                .locationLabel(bin.locationLabel())
                .binLocationLabel(bin.zoneLabel())
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
