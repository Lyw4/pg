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
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 주문 상세 항목.
 * <p>
 * 테이블/컬럼명은 카멜 표기법으로 선언한다.
 */
@Entity
@Table(name = "orderItems")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "orderItemId")
    private Long orderItemId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "orderId", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "productId", nullable = false)
    private Product product;

    /** 출고된 로트 (미출고 상태에서는 null 가능) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lotId")
    private ProductLot lot;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    /** 주문 당시 단가(원) */
    @Column(name = "orderPrice", nullable = false)
    private Long orderPrice;

    /**
     * 출고된 대표 로트를 지정한다.
     * <p>
     * FEFO 출고 시 하나의 주문 항목이 여러 로트에 걸쳐 차감될 수 있는데,
     * 스키마상 orderItems 는 로트를 1개만 가리킬 수 있으므로
     * <b>가장 먼저 만료되는(먼저 출고된) 로트</b>를 대표로 기록한다.
     * 로트별 상세 차감 내역은 stockMovements 이력에 남는다.
     */
    public void assignLot(ProductLot lot) {
        this.lot = lot;
    }
}
