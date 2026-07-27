package com.feedflow.admin.dto;

import com.feedflow.domain.ProductLot;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

/**
 * 유통기한 임박 로트 목록 행.
 */
@Getter
@Builder
public class ExpiringLotDto {

    private final Long lotId;
    private final String lotNo;
    private final Long productId;
    private final String productName;
    private final String animalType;
    private final LocalDate manufacturedDate;
    private final LocalDate expirationDate;
    private final Integer lotQuantity;

    /** 유통기한까지 남은 일수 */
    private final Long remainingDays;

    /**
     * @param lot   fetch join 으로 product 가 초기화된 상태여야 한다.
     * @param today 기준일
     */
    public static ExpiringLotDto of(ProductLot lot, LocalDate today) {
        return ExpiringLotDto.builder()
                .lotId(lot.getLotId())
                .lotNo(lot.getLotNo())
                .productId(lot.getProduct().getProductId())
                .productName(lot.getProduct().getName())
                .animalType(lot.getProduct().getAnimalType())
                .manufacturedDate(lot.getManufacturedDate())
                .expirationDate(lot.getExpirationDate())
                .lotQuantity(lot.getLotQuantity())
                .remainingDays(lot.daysUntilExpiration(today))
                .build();
    }

    /** 잔여일 7일 이내면 위험(빨강), 그 외는 경고(노랑) */
    public String getBadgeClass() {
        return remainingDays != null && remainingDays <= 7 ? "bg-danger" : "bg-warning text-dark";
    }
}
