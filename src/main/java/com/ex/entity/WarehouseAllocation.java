package com.ex.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import jakarta.persistence.Column;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "warehouse_allocation",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"warehouse_id", "product_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WarehouseAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long allocationId;

    @Version
    @Column(nullable = false)
    private long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    private int monthlyPlannedQuantity;

    private int targetStockQuantity;

    /*
     * 기존 LOT 재고와 분리된 거점 창고의 실제 보유 수량입니다.
     * Integer로 두어 기존 DB 행에 새 컬럼이 추가될 때 null 여부를
     * 확인하고 초기 권장 보유량으로 한 번만 이관할 수 있게 합니다.
     */
    private Integer currentStockQuantity;

    public WarehouseAllocation(
            Warehouse warehouse,
            Product product,
            int monthlyPlannedQuantity,
            int targetStockQuantity) {
        this.warehouse = warehouse;
        this.product = product;
        changePlan(monthlyPlannedQuantity, targetStockQuantity);
        this.currentStockQuantity = targetStockQuantity;
    }

    public void changePlan(
            int monthlyPlannedQuantity,
            int targetStockQuantity) {
        if (monthlyPlannedQuantity < 0 || targetStockQuantity < 0) {
            throw new IllegalArgumentException(
                    "월 배치량과 권장 보유량은 0개 이상이어야 합니다.");
        }
        this.monthlyPlannedQuantity = monthlyPlannedQuantity;
        this.targetStockQuantity = targetStockQuantity;
    }

    public int getCurrentStockQuantity() {
        return currentStockQuantity == null ? 0 : currentStockQuantity;
    }

    public void initializeCurrentStockIfMissing() {
        if (currentStockQuantity == null) {
            currentStockQuantity = targetStockQuantity;
        }
    }

    public void adjustCurrentStock(int currentStockQuantity) {
        if (currentStockQuantity < 0) {
            throw new IllegalArgumentException(
                    "창고 현재고는 0개 이상이어야 합니다.");
        }
        this.currentStockQuantity = currentStockQuantity;
    }

    public int getStockGapQuantity() {
        return getCurrentStockQuantity() - targetStockQuantity;
    }

    public int getStockRate() {
        if (targetStockQuantity == 0) {
            return getCurrentStockQuantity() == 0 ? 0 : 100;
        }
        return (int) Math.round(
                getCurrentStockQuantity() * 100.0
                        / targetStockQuantity);
    }

    public boolean isLowStock() {
        return getCurrentStockQuantity() < targetStockQuantity;
    }

    public String getStockStatusLabel() {
        if (getCurrentStockQuantity() < targetStockQuantity) {
            return "보충 필요";
        }
        if (getCurrentStockQuantity() > targetStockQuantity * 1.2) {
            return "과잉";
        }
        return "적정";
    }

    public String getStockStatusCssClass() {
        if (getCurrentStockQuantity() < targetStockQuantity) {
            return "low";
        }
        if (getCurrentStockQuantity() > targetStockQuantity * 1.2) {
            return "over";
        }
        return "normal";
    }

    public int getFirstDeliveryQuantity() {
        return (monthlyPlannedQuantity + 1) / 2;
    }

    public int getSecondDeliveryQuantity() {
        return monthlyPlannedQuantity / 2;
    }
}
