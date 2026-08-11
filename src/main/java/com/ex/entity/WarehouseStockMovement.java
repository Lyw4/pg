package com.ex.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "warehouse_stock_movement")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WarehouseStockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long movementId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MovementType movementType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lot_id", nullable = false)
    private ProductLot lot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_bin_id")
    private WarehouseBin sourceBin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_bin_id")
    private WarehouseBin destinationBin;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private DisposalReason disposalReason;

    @Column(length = 250)
    private String memo;

    @Column(length = 50)
    private String operatorName;

    private Long orderId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public WarehouseStockMovement(
            MovementType movementType,
            ProductLot lot,
            WarehouseBin sourceBin,
            WarehouseBin destinationBin,
            int quantity,
            DisposalReason disposalReason,
            String memo,
            String operatorName,
            Long orderId) {
        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "이동 수량은 1 이상이어야 합니다.");
        }
        this.movementType = movementType;
        this.lot = lot;
        this.sourceBin = sourceBin;
        this.destinationBin = destinationBin;
        this.quantity = quantity;
        this.disposalReason = disposalReason;
        this.memo = memo;
        this.operatorName = operatorName;
        this.orderId = orderId;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public String getRouteLabel() {
        String source = sourceBin == null
                ? "외부"
                : sourceBin.getDisplayName();
        String destination = destinationBin == null
                ? "외부"
                : destinationBin.getDisplayName();
        return source + " → " + destination;
    }
}
