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
import jakarta.persistence.Column;
import java.time.LocalDateTime;
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

    /*
     * 낙관적 락을 의도적으로 걸지 않습니다.
     *
     * 이 엔티티에서 가장 자주 바뀌는 currentStockQuantity는
     * sellableAtWarehouse()로 매번 다시 계산해 덮어쓰는 파생 캐시입니다.
     * 값이 유실돼도 다음 갱신에서 정확한 값으로 수렴하므로 락이 지킬 무결성이
     * 없습니다. 반면 결제 대기 정리 스케줄러와 고객 주문 생성이 같은 행의
     * 캐시를 동시에 갱신하면 낙관적 락 충돌이 나고, ApiExceptionHandler가
     * 이를 409로 바꿔 고객 결제를 실패시켰습니다.
     *
     * 재계산이 불가능한 관리자 입력값(monthlyPlannedQuantity,
     * targetStockQuantity, auditedStockQuantity)은
     * WarehouseManagementService가 findByIdForUpdate()로 행을 잠그고
     * 수정해 보호합니다.
     *
     * 컬럼 자체는 남겨 둡니다. ddl-auto=update는 컬럼을 삭제하지 않아
     * 기존 DB에 NOT NULL로 남아 있고, 필드를 없애면 INSERT에서 빠져
     * 제약 위반이 납니다.
     */
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

    /*
     * 관리자가 창고에서 직접 센 실사 수량과 그 시점입니다.
     *
     * currentStockQuantity 는 sellableAtWarehouse() 로 계속 덮어써지는 파생
     * 캐시이므로, 관리자가 입력한 값을 거기에만 담으면 다음 재계산에서 흔적도
     * 없이 사라집니다. 실제로 그렇게 동작해 실사 기능이 사실상 무의미했습니다.
     *
     * 그래서 실사값은 재계산이 건드리지 않는 별도 컬럼에 남깁니다. 이렇게 두면
     * 자동 계산값과의 차이(getAuditedStockVariance)가 보존되어, 장부와 실물이
     * 어긋난 사실 자체를 추적할 수 있습니다. 차이를 없애는 것은 재계산이 아니라
     * 구역 재고를 실제로 바로잡는 작업이어야 합니다.
     */
    @Column(name = "audited_stock_quantity")
    private Integer auditedStockQuantity;

    @Column(name = "audited_stock_at")
    private LocalDateTime auditedStockAt;

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

    /**
     * 자동 재계산 결과로 파생 캐시를 갱신합니다.
     *
     * <p>호출자는 sellableAtWarehouse() 같은 계산 결과를 그대로 넘깁니다.
     * 사람이 입력한 값을 이 메서드로 넣으면 다음 재계산에 덮여 사라지므로,
     * 관리자 실사 입력에는 {@link #recordStockAudit(int)} 를 사용합니다.
     */
    public void refreshCurrentStock(int currentStockQuantity) {
        if (currentStockQuantity < 0) {
            throw new IllegalArgumentException(
                    "창고 현재고는 0개 이상이어야 합니다.");
        }
        this.currentStockQuantity = currentStockQuantity;
    }

    /**
     * 관리자가 직접 센 실사 수량을 기록합니다.
     *
     * <p>실사값을 재계산이 건드리지 않는 별도 컬럼에 남기고, 화면에서 바로
     * 확인할 수 있도록 파생 캐시에도 함께 반영합니다. 캐시는 다음 재계산에서
     * 자동 계산값으로 돌아가지만 실사 기록은 그대로 남아, 그 차이를
     * {@link #getAuditedStockVariance()} 로 확인할 수 있습니다.
     */
    public void recordStockAudit(int auditedStockQuantity) {
        if (auditedStockQuantity < 0) {
            throw new IllegalArgumentException(
                    "창고 현재고는 0개 이상이어야 합니다.");
        }
        this.auditedStockQuantity = auditedStockQuantity;
        this.auditedStockAt = LocalDateTime.now();
        this.currentStockQuantity = auditedStockQuantity;
    }

    public boolean hasStockAudit() {
        return auditedStockQuantity != null;
    }

    /**
     * 실사 수량에서 현재 자동 계산값을 뺀 차이입니다.
     *
     * <p>양수면 실물이 장부보다 많고, 음수면 장부보다 적다는 뜻입니다.
     * 실사 기록이 없으면 0 을 돌려줍니다.
     */
    public int getAuditedStockVariance() {
        if (auditedStockQuantity == null) {
            return 0;
        }
        return auditedStockQuantity - getCurrentStockQuantity();
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
