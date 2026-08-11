package com.ex.entity;

import java.time.LocalDateTime;

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
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "bin_inventory",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"lot_id", "bin_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BinInventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long binInventoryId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lot_id", nullable = false)
    private ProductLot lot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bin_id", nullable = false)
    private WarehouseBin bin;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    public BinInventory(
            ProductLot lot,
            WarehouseBin bin,
            int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException(
                    "구역 재고는 0 이상이어야 합니다.");
        }
        this.lot = lot;
        this.bin = bin;
        this.quantity = quantity;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }

    public void add(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException(
                    "증가 수량은 1 이상이어야 합니다.");
        }
        quantity += amount;
    }

    public void subtract(int amount) {
        if (amount <= 0 || quantity < amount) {
            throw new IllegalArgumentException(
                    "구역 재고가 부족합니다.");
        }
        quantity -= amount;
    }
}
