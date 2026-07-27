package com.feedflow.domain;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 주문.
 * <p>
 * 테이블/컬럼명은 카멜 표기법으로 선언한다.
 * (ORDER 는 SQL 예약어이므로 테이블명은 orders)
 */
@Entity
@Table(name = "orders")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "orderId")
    private Long orderId;

    /** 주문한 고객 (users.userId 참조) */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "userId", nullable = false)
    private User user;

    /** 할인 전 주문 금액 */
    @Column(name = "totalPrice", nullable = false)
    private Long totalPrice;

    /** 할인 금액 */
    @Column(name = "discountPrice", nullable = false)
    private Long discountPrice;

    /** 실제 결제 금액 (매출 집계 기준) */
    @Column(name = "finalPrice", nullable = false)
    private Long finalPrice;

    /** 배송지 주소 */
    @Column(name = "shippingAddress", nullable = false, length = 300)
    private String shippingAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "createdAt", nullable = false)
    private LocalDateTime createdAt;

    @Builder.Default
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> orderItems = new ArrayList<>();

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = OrderStatus.PAID;
        }
    }

    /* ------------------------------------------------------------------
     * 출고 관련 도메인 로직
     * ------------------------------------------------------------------ */

    /** 출고 처리 가능한 상태인지 (결제완료 / 출고대기) */
    public boolean isDispatchable() {
        return status == OrderStatus.PAID || status == OrderStatus.READY;
    }

    /** 출고 대기 상태로 변경 */
    public void markReady() {
        this.status = OrderStatus.READY;
    }

    /** 출고 완료 처리 */
    public void markShipped() {
        this.status = OrderStatus.SHIPPED;
    }

    public void changeStatus(OrderStatus status) {
        this.status = status;
    }

    /** 주문 전체 수량 */
    public int totalQuantity() {
        return orderItems.stream()
                .mapToInt(item -> item.getQuantity() == null ? 0 : item.getQuantity())
                .sum();
    }
}
