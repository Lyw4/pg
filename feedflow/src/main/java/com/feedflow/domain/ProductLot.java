package com.feedflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 상품 로트(생산 단위). 유통기한 관리의 기준이 된다.
 * <p>
 * lotQuantity 는 해당 로트의 전체 잔여 수량이며,
 * 구역(Bin)별 보관 수량은 {@link Inventory} 가 관리한다.
 * (lotQuantity = 같은 로트의 Inventory.quantity 합계)
 */
@Entity
@Table(
        name = "productLots",
        uniqueConstraints = @UniqueConstraint(name = "ukProductLotNo", columnNames = {"productId", "lotNo"})
)
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductLot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "lotId")
    private Long lotId;

    /** products.productId 참조 */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "productId", nullable = false)
    private Product product;

    @Column(name = "lotNo", nullable = false, length = 50)
    private String lotNo;

    /** 제조일자 */
    @Column(name = "manufacturedDate", nullable = false)
    private LocalDate manufacturedDate;

    /** 유통기한 (제조일자 + 품목의 shelfLifeDays 로 자동 계산) */
    @Column(name = "expirationDate", nullable = false)
    private LocalDate expirationDate;

    /** 해당 로트의 전체 잔여 수량 */
    @Column(name = "lotQuantity", nullable = false)
    private Integer lotQuantity;

    /* ------------------------------------------------------------------
     * 생성
     * ------------------------------------------------------------------ */

    /**
     * 입고 시 새로운 로트를 생성한다.
     * 유통기한은 품목의 유통기한 일수를 이용해 자동 계산한다.
     */
    public static ProductLot createForInbound(Product product,
                                             String lotNo,
                                             LocalDate manufacturedDate,
                                             int quantity) {
        return ProductLot.builder()
                .product(product)
                .lotNo(lotNo)
                .manufacturedDate(manufacturedDate)
                .expirationDate(product.calculateExpirationDate(manufacturedDate))
                .lotQuantity(quantity)
                .build();
    }

    /* ------------------------------------------------------------------
     * 수량
     * ------------------------------------------------------------------ */

    /** 추가 입고 시 로트 수량 합산 */
    public void addQuantity(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("입고 수량은 1 이상이어야 합니다.");
        }
        this.lotQuantity = (lotQuantity == null ? 0 : lotQuantity) + quantity;
    }

    /** 출고 시 로트 수량 차감 */
    public void subtractQuantity(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("출고 수량은 1 이상이어야 합니다.");
        }
        int current = lotQuantity == null ? 0 : lotQuantity;
        if (current < quantity) {
            throw new IllegalStateException(
                    "로트 잔여 수량이 부족합니다. 잔여=" + current + ", 요청=" + quantity);
        }
        this.lotQuantity = current - quantity;
    }

    /* ------------------------------------------------------------------
     * 유통기한 (D-Day)
     * ------------------------------------------------------------------ */

    /**
     * 기준일로부터 유통기한까지 남은 일수 (D-Day).
     * <ul>
     *     <li>양수 : 남은 일수 (예: 5 → D-5)</li>
     *     <li>0    : 오늘 만료 (D-0)</li>
     *     <li>음수 : 이미 만료됨 (예: -3 → 3일 전 만료)</li>
     * </ul>
     */
    public long daysUntilExpiration(LocalDate baseDate) {
        if (baseDate == null || expirationDate == null) {
            throw new IllegalStateException("유통기한 정보가 없어 D-Day 를 계산할 수 없습니다. lotNo=" + lotNo);
        }
        return ChronoUnit.DAYS.between(baseDate, expirationDate);
    }

    /** 기준일 시점에 유통기한이 지났는지 여부 (만료일 당일은 아직 만료 아님) */
    public boolean isExpired(LocalDate baseDate) {
        return daysUntilExpiration(baseDate) < 0;
    }

    /**
     * 기준일로부터 지정 일수 이내에 만료되는지 여부.
     * 이미 만료된 로트는 임박 대상에서 제외한다.
     */
    public boolean isExpiringWithin(LocalDate baseDate, int days) {
        long remaining = daysUntilExpiration(baseDate);
        return remaining >= 0 && remaining <= days;
    }
}
