package com.feedflow.admin.dto;

import com.feedflow.domain.Product;
import lombok.Builder;
import lombok.Getter;

/**
 * 안전재고 알림 목록 행.
 * 코딩 규칙 7번에 따라 imageUrl / description 은 뷰로 내려주지 않는다.
 */
@Getter
@Builder
public class SafetyStockAlertDto {

    private final Long productId;
    private final String name;
    private final String animalType;
    private final Integer weightKg;
    private final Long price;
    private final Integer totalStock;
    private final Integer safetyStock;

    /** 안전재고까지 부족한 수량 */
    private final Integer shortage;

    /** 재고율(%) - 프로그레스 바 표기용 */
    private final Integer stockRate;

    public static SafetyStockAlertDto from(Product product) {
        int safety = product.getSafetyStock() == null ? 0 : product.getSafetyStock();
        int total = product.getTotalStock() == null ? 0 : product.getTotalStock();
        int rate = safety == 0 ? 100 : (int) Math.round(total * 100.0 / safety);

        return SafetyStockAlertDto.builder()
                .productId(product.getProductId())
                .name(product.getName())
                .animalType(product.getAnimalType())
                .weightKg(product.getWeightKg())
                .price(product.getPrice())
                .totalStock(total)
                .safetyStock(safety)
                .shortage(product.shortageQuantity())
                .stockRate(Math.min(rate, 100))
                .build();
    }
}
