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

    /**
     * 이 이동이 영향을 준 구역.
     * <p>
     * 입고 · 폐기 · 출고취소는 해당 구역, 출고는 빠져나간 구역이다.
     * 구역 간 이동({@link MovementType#MOVE})에서는 <b>도착지</b>를 가리키고
     * 출발지는 {@link #fromBin} 에 따로 담는다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "binId")
    private WarehouseBin bin;

    /**
     * 구역 간 이동의 출발 구역 (이동 이력에만 존재).
     * <p>
     * 이동은 한 건의 이력이 <b>두 구역</b>에 영향을 준다. 기존 {@code bin} 하나로는
     * "어디서 어디로" 를 표현할 수 없어 출발지를 별도 컬럼으로 둔다.
     * <p>
     * 이력을 두 건(출발 차감 / 도착 증가)으로 쪼개는 방식도 가능하지만,
     * 이동은 <b>하나의 업무 행위</b>이고 총 재고가 변하지 않으므로
     * 타임라인에 두 줄로 나타나면 오히려 재고가 두 번 움직인 것처럼 보인다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fromBinId")
    private WarehouseBin fromBin;

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

    /**
     * 같은 센터 안에서의 구역 간 이동 이력 생성.
     * <p>
     * 창고 안에서 위치만 바뀌는 것이므로 <b>로트 잔여 수량과 품목 총 재고는 변하지 않는다.</b>
     * ({@code MovementType.MOVE} 의 sign 이 0 인 이유)
     * <p>
     * <b>센터가 다르면 이 유형을 쓸 수 없다.</b> 출발 센터의 재고가 실제로 줄어들어
     * 총량 불변 전제가 깨지기 때문이다. 센터 간에는
     * {@link #transferOut} / {@link #transferIn} 한 쌍을 쓴다.
     *
     * @param fromBin 출발 구역
     * @param toBin   도착 구역
     */
    public static StockMovement move(ProductLot lot,
                                     WarehouseBin fromBin,
                                     WarehouseBin toBin,
                                     int quantity,
                                     String memo,
                                     Long userId,
                                     String userName) {
        return StockMovement.builder()
                .movementType(MovementType.MOVE)
                .product(lot.getProduct())
                .lot(lot)
                .bin(toBin)
                .fromBin(fromBin)
                .quantity(quantity)
                .memo(memo)
                .userId(userId)
                .userName(userName)
                .createdAt(LocalDateTime.now())
                .build();
    }

    /**
     * 센터 간 이관 - <b>출발</b> 이력 생성.
     * <p>
     * 출발 구역에서 나와 운송 중 가상 구역으로 들어가는 구간이다.
     * {@code bin} 은 도착지인 <b>운송 중 구역</b>, {@code fromBin} 은 실제 출발 구역이다.
     * ({@code MOVE} 와 같은 규칙 — {@code bin} 이 항상 "이 이력의 도착지" 다)
     * <p>
     * {@link #transferIn} 과 짝을 이뤄 두 건의 합이 0 이 되므로
     * 로트 잔여 수량과 품목 총 재고는 변하지 않는다.
     *
     * @param fromBin     실제 출발 구역
     * @param inTransitBin 출발 센터의 운송 중 가상 구역
     */
    public static StockMovement transferOut(ProductLot lot,
                                            WarehouseBin fromBin,
                                            WarehouseBin inTransitBin,
                                            int quantity,
                                            String memo,
                                            Long userId,
                                            String userName) {
        return StockMovement.builder()
                .movementType(MovementType.TRANSFER_OUT)
                .product(lot.getProduct())
                .lot(lot)
                .bin(inTransitBin)
                .fromBin(fromBin)
                .quantity(quantity)
                .memo(memo)
                .userId(userId)
                .userName(userName)
                .createdAt(LocalDateTime.now())
                .build();
    }

    /**
     * 센터 간 이관 - <b>도착</b> 이력 생성.
     * <p>
     * 운송 중 가상 구역에서 나와 도착 센터의 구역으로 들어가는 구간이다.
     * {@code fromBin} 이 운송 중 구역이므로, 이력만 보고도
     * "어디를 경유해 들어왔는지" 를 알 수 있다.
     *
     * @param inTransitBin 출발 센터의 운송 중 가상 구역
     * @param toBin        도착 센터의 실제 구역
     */
    public static StockMovement transferIn(ProductLot lot,
                                           WarehouseBin inTransitBin,
                                           WarehouseBin toBin,
                                           int quantity,
                                           String memo,
                                           Long userId,
                                           String userName) {
        return StockMovement.builder()
                .movementType(MovementType.TRANSFER_IN)
                .product(lot.getProduct())
                .lot(lot)
                .bin(toBin)
                .fromBin(inTransitBin)
                .quantity(quantity)
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
