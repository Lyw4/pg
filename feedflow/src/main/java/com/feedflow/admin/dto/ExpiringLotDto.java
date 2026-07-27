package com.feedflow.admin.dto;

import com.feedflow.domain.ProductLot;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

/**
 * 유통기한 경고 목록 행 (임박 + 이미 만료).
 */
@Getter
@Builder
public class ExpiringLotDto {

    private final Long lotId;
    private final String lotNo;
    private final Long productId;
    private final String productCode;
    private final String productName;
    private final String animalType;
    private final LocalDate manufacturedDate;
    private final LocalDate expirationDate;
    private final Integer lotQuantity;

    /** 유통기한까지 남은 일수 (음수면 이미 만료) */
    private final Long remainingDays;

    /** 이미 유통기한이 지났는지 여부 */
    private final boolean expired;

    /**
     * @param lot   fetch join 으로 product 가 초기화된 상태여야 한다.
     * @param today 기준일
     */
    public static ExpiringLotDto of(ProductLot lot, LocalDate today) {
        return ExpiringLotDto.builder()
                .lotId(lot.getLotId())
                .lotNo(lot.getLotNo())
                .productId(lot.getProduct().getProductId())
                .productCode(lot.getProduct().getProductCode())
                .productName(lot.getProduct().getName())
                .animalType(lot.getProduct().getAnimalType())
                .manufacturedDate(lot.getManufacturedDate())
                .expirationDate(lot.getExpirationDate())
                .lotQuantity(lot.getLotQuantity())
                .remainingDays(lot.daysUntilExpiration(today))
                .expired(lot.isExpired(today))
                .build();
    }

    /** D-Day 표기 (만료된 로트는 경과일로 표기) */
    public String getDDayLabel() {
        if (remainingDays == null) {
            return "-";
        }
        if (remainingDays < 0) {
            return "만료 " + Math.abs(remainingDays) + "일 경과";
        }
        if (remainingDays == 0) {
            return "오늘 만료";
        }
        return "D-" + remainingDays;
    }

    /**
     * 위험도별 뱃지 클래스.
     * 만료(검정) → 7일 이내(빨강) → 그 외(노랑)
     */
    public String getBadgeClass() {
        if (remainingDays == null) {
            return "bg-light text-dark border";
        }
        if (remainingDays < 0) {
            return "bg-dark";
        }
        if (remainingDays <= 7) {
            return "bg-danger";
        }
        return "bg-warning text-dark";
    }
}
