package com.feedflow.admin.dto;

import com.feedflow.common.util.DDay;
import com.feedflow.domain.Product;
import com.feedflow.domain.ProductLot;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

/**
 * 로트번호 검색 결과 후보 한 건.
 * <p>
 * 로트번호는 품목 단위로만 유일하므로({@code ukProductLotNo}) 서로 다른 품목이
 * 같은 번호를 쓸 수 있다. 그때 어느 품목의 로트를 추적할지 고르게 하려고 쓴다.
 */
@Getter
@Builder
public class LotCandidateDto {

    private final Long lotId;
    private final String lotNo;

    private final String productCode;
    private final String productName;
    private final String animalType;

    private final LocalDate expirationDate;
    private final long remainingDays;
    private final int lotQuantity;

    public static LotCandidateDto of(ProductLot lot, LocalDate today) {
        Product product = lot.getProduct();

        return LotCandidateDto.builder()
                .lotId(lot.getLotId())
                .lotNo(lot.getLotNo())
                .productCode(product.getProductCode())
                .productName(product.getName())
                .animalType(product.getAnimalType().getDescription())
                .expirationDate(lot.getExpirationDate())
                .remainingDays(lot.daysUntilExpiration(today))
                .lotQuantity(lot.getLotQuantity() == null ? 0 : lot.getLotQuantity())
                .build();
    }

    public String getDDayLabel() {
        return DDay.label(remainingDays);
    }

    public String getDDayBadgeClass() {
        return DDay.badgeClass(remainingDays);
    }
}
