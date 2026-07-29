package com.feedflow.admin.dto;

import com.feedflow.common.util.DDay;
import com.feedflow.domain.Product;
import com.feedflow.domain.ProductLot;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

/**
 * 테스트/현장용 바코드 라벨.
 * <p>
 * 화면에서 QR 코드로 렌더링해 모바일 카메라로 스캔 테스트하거나,
 * 인쇄해서 실제 파렛트/포대에 부착할 수 있다.
 */
@Getter
@Builder
public class BarcodeLabelDto {

    public enum LabelType {
        LOT, PRODUCT
    }

    private final LabelType labelType;

    /** QR 코드에 담길 값 (로트번호 또는 품목코드) */
    private final String code;

    private final String productCode;
    private final String productName;
    private final String animalType;
    private final Integer weightKg;

    /* 로트 라벨에만 존재 */
    private final String lotNo;
    private final LocalDate manufacturedDate;
    private final LocalDate expirationDate;
    private final Integer lotQuantity;
    private final Long remainingDays;

    public static BarcodeLabelDto ofProduct(Product product) {
        return BarcodeLabelDto.builder()
                .labelType(LabelType.PRODUCT)
                .code(product.getProductCode())
                .productCode(product.getProductCode())
                .productName(product.getName())
                .animalType(product.getAnimalType().getDescription())
                .weightKg(product.getWeightKg())
                .build();
    }

    public static BarcodeLabelDto ofLot(ProductLot lot, LocalDate today) {
        Product product = lot.getProduct();
        return BarcodeLabelDto.builder()
                .labelType(LabelType.LOT)
                .code(lot.getLotNo())
                .productCode(product.getProductCode())
                .productName(product.getName())
                .animalType(product.getAnimalType().getDescription())
                .weightKg(product.getWeightKg())
                .lotNo(lot.getLotNo())
                .manufacturedDate(lot.getManufacturedDate())
                .expirationDate(lot.getExpirationDate())
                .lotQuantity(lot.getLotQuantity())
                .remainingDays(lot.daysUntilExpiration(today))
                .build();
    }
    public String getDDayLabel() {
        return DDay.label(remainingDays);
    }

    public String getDDayBadgeClass() {
        return DDay.badgeClass(remainingDays);
    }
}
