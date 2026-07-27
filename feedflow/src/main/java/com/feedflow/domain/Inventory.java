package com.feedflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 재고 : "어떤 로트가 어떤 구역에 몇 개 있는지" 를 나타내는 엔티티.
 * <p>
 * (lotId, binId) 조합은 유일하다.
 * 같은 로트가 같은 구역에 다시 입고되면 새 행을 만들지 않고 수량을 합산한다.
 */
@Entity
@Table(
        name = "inventories",
        uniqueConstraints = @UniqueConstraint(name = "ukInventoryLotBin", columnNames = {"lotId", "binId"})
)
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inventoryId")
    private Long inventoryId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lotId", nullable = false)
    private ProductLot lot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "binId", nullable = false)
    private WarehouseBin bin;

    /** 해당 구역에 보관된 수량 */
    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "updatedAt", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    /** 신규 재고 생성 (해당 구역에 처음 입고되는 경우) */
    public static Inventory createForInbound(ProductLot lot, WarehouseBin bin, int quantity) {
        return Inventory.builder()
                .lot(lot)
                .bin(bin)
                .quantity(quantity)
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /** 동일 로트가 동일 구역에 다시 입고되는 경우 - 수량 합산 */
    public void addQuantity(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("입고 수량은 1 이상이어야 합니다.");
        }
        this.quantity = (quantity == null ? 0 : quantity) + amount;
        this.updatedAt = LocalDateTime.now();
    }

    /** 출고 시 차감 */
    public void subtractQuantity(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("출고 수량은 1 이상이어야 합니다.");
        }
        int current = quantity == null ? 0 : quantity;
        if (current < amount) {
            throw new IllegalStateException(
                    "구역 재고가 부족합니다. 현재=" + current + ", 요청=" + amount);
        }
        this.quantity = current - amount;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isEmpty() {
        return quantity == null || quantity == 0;
    }
}
