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

    /** 폐기 사유 (폐기 이력에만 존재) */
    @Enumerated(EnumType.STRING)
    @Column(name = "reason", length = 30)
    private DisposalReason reason;

    /**
     * 이 이동을 발생시킨 주문 (주문 기반 출고 · 출고 취소에만 존재).
     * <p>
     * 이 값이 없으면 <b>출고를 취소할 때 어느 로트에서 몇 개를 뺐는지 알 수 없다.</b>
     * {@code orderItems.lotId} 는 대표 로트 하나만 기록하므로 여러 로트에 걸친
     * FEFO 차감을 되돌릴 수 없기 때문이다.
     * <p>
     * 처리자(userId)와 같은 이유로 FK 대신 식별자만 스냅샷으로 보관한다.
     * 이력은 주문이 지워지더라도 남아야 하고, 이력 추적은 주문 번호로만 조회하면 된다.
     */
    @Column(name = "orderId")
    private Long orderId;

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

    /** 폐기 이력 생성 */
    public static StockMovement disposal(ProductLot lot,
                                         WarehouseBin bin,
                                         int quantity,
                                         DisposalReason reason,
                                         String memo,
                                         Long userId,
                                         String userName) {
        return StockMovement.builder()
                .movementType(MovementType.DISPOSAL)
                .product(lot.getProduct())
                .lot(lot)
                .bin(bin)
                .quantity(quantity)
                .reason(reason)
                .memo(memo)
                .userId(userId)
                .userName(userName)
                .createdAt(LocalDateTime.now())
                .build();
    }

    /**
     * 출고 이력 생성.
     *
     * @param orderId 주문 기반 출고면 주문 번호, 직접 출고면 null
     */
    public static StockMovement outbound(ProductLot lot,
                                         WarehouseBin bin,
                                         int quantity,
                                         Long orderId,
                                         String memo,
                                         Long userId,
                                         String userName) {
        return StockMovement.builder()
                .movementType(MovementType.OUTBOUND)
                .product(lot.getProduct())
                .lot(lot)
                .bin(bin)
                .quantity(quantity)
                .orderId(orderId)
                .memo(memo)
                .userId(userId)
                .userName(userName)
                .createdAt(LocalDateTime.now())
                .build();
    }

    /**
     * 출고 취소에 따른 재고 복구 이력 생성.
     * <p>
     * 원래 출고했던 로트 · 구역에 그대로 되돌려 놓은 기록이다.
     */
    public static StockMovement cancelRestore(ProductLot lot,
                                              WarehouseBin bin,
                                              int quantity,
                                              Long orderId,
                                              String memo,
                                              Long userId,
                                              String userName) {
        return StockMovement.builder()
                .movementType(MovementType.CANCEL)
                .product(lot.getProduct())
                .lot(lot)
                .bin(bin)
                .quantity(quantity)
                .orderId(orderId)
                .memo(memo)
                .userId(userId)
                .userName(userName)
                .createdAt(LocalDateTime.now())
                .build();
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
