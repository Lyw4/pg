package com.feedflow.domain;

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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 재고 이동 이력 (입고 / 출고 / 이동 / 조정).
 * <p>
 * 감사(audit) 목적의 이력이므로 처리자 정보는 FK 대신
 * 처리 시점의 값(userId, userName)을 스냅샷으로 보관한다.
 */
@Entity
@Table(name = "stockMovements")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "movementId")
    private Long movementId;

    @Enumerated(EnumType.STRING)
    @Column(name = "movementType", nullable = false, length = 20)
    private MovementType movementType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "productId", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lotId", nullable = false)
    private ProductLot lot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "binId")
    private WarehouseBin bin;

    /** 이동 수량 (항상 양수, 증감 방향은 movementType 이 결정) */
    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "memo", length = 200)
    private String memo;

    /** 처리자 스냅샷 */
    @Column(name = "userId")
    private Long userId;

    @Column(name = "userName", length = 50)
    private String userName;

    @Column(name = "createdAt", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    /** 입고 이력 생성 */
    public static StockMovement inbound(ProductLot lot,
                                        WarehouseBin bin,
                                        int quantity,
                                        String memo,
                                        Long userId,
                                        String userName) {
        return StockMovement.builder()
                .movementType(MovementType.INBOUND)
                .product(lot.getProduct())
                .lot(lot)
                .bin(bin)
                .quantity(quantity)
                .memo(memo)
                .userId(userId)
                .userName(userName)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
