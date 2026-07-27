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
}
