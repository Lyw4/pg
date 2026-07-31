package com.feedflow.admin.dto;

import com.feedflow.domain.Product;
import com.feedflow.domain.ProductType;
import lombok.Builder;
import lombok.Getter;

/**
 * 품목 목록 / 상세 조회용 DTO.
 * 코딩 규칙 7번에 따라 imageUrl / description 은 포함하지 않는다.
 */
@Getter
@Builder
public class ProductDto {

    private final Long productId;
    private final String productCode;
    private final String name;

    /** 축종 한글 라벨 (소 / 돼지 / 조류) */
    private final String animalType;

    /** 품목 구분 (뱃지 표기를 위해 enum 그대로 전달) */
    private final ProductType productType;

    private final Integer weightKg;
    private final Long price;
    private final Integer totalStock;
    private final Integer safetyStock;
    private final Integer shelfLifeDays;
    private final boolean active;
    private final boolean belowSafetyStock;
    private final Integer shortage;

    public static ProductDto from(Product product) {
        return ProductDto.builder()
                .productId(product.getProductId())
                .productCode(product.getProductCode())
                .name(product.getName())
                .animalType(product.getAnimalType().getDescription())
                .productType(product.getProductType())
                .weightKg(product.getWeightKg())
                .price(product.getPrice())
                .totalStock(product.getTotalStock())
                .safetyStock(product.getSafetyStock())
                .shelfLifeDays(product.getShelfLifeDays())
                .active(product.isActive())
                .belowSafetyStock(product.isBelowSafetyStock())
                .shortage(product.shortageQuantity())
                .build();
    }

    /** 사용 여부 뱃지 클래스 */
    public String getActiveBadgeClass() {
        return active ? "bg-success" : "bg-secondary";
    }

    public String getActiveLabel() {
        return active ? "사용" : "중지";
    }
}
