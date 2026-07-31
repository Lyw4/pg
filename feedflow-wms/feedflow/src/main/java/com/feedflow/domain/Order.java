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

    /* ------------------------------------------------------------------
     * 취소 정보 (감사 추적)
     *
     * 취소 사유는 재고 이력(StockMovement)에 남기는 것만으로는 부족하다.
     * 출고 전(PAID · READY) 취소는 재고가 움직이지 않아 이력 자체가 생기지 않으므로
     * 사유 · 시각 · 처리자를 주문에 직접 보관해야 "누가 왜 취소했는지" 를 답할 수 있다.
     *
     * 취소는 주문당 한 번만 발생하고 되돌릴 수 없으므로(CANCELED 는 종단 상태)
     * 별도 이력 테이블 없이 컬럼으로 충분하다.
     * 기존 주문 데이터에는 값이 없으므로 전부 nullable 이다.
     * ------------------------------------------------------------------ */

    /** 취소 일시 (취소되지 않은 주문은 null) */
    @Column(name = "canceledAt")
    private LocalDateTime canceledAt;

    /**
     * 취소 사유.
     * <p>
     * 화면 입력이 선택 항목이라 미입력일 수 있다. 길이는 입력 폼의 maxlength 와 맞춘다.
     */
    @Column(name = "cancelReason", length = 150)
    private String cancelReason;

    /**
     * 취소 처리자 스냅샷.
     * <p>
     * {@code StockMovement} 의 처리자 기록과 같은 이유로 FK 대신 식별자와 이름을
     * 함께 스냅샷으로 남긴다. 이름만 남기면 동명이인을 구분할 수 없고,
     * ID 만 남기면 사원이 삭제된 뒤 누구인지 알 수 없다.
     */
    @Column(name = "canceledById")
    private Long canceledById;

    @Column(name = "canceledByName", length = 50)
    private String canceledByName;

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

    /**
     * 취소 가능한 상태인지.
     * <p>
     * 이미 취소된 주문은 다시 취소할 수 없고, <b>배송 완료된 주문도 취소하지 않는다.</b>
     * 물건이 이미 고객에게 넘어간 뒤라 창고 재고를 되돌리면 실물과 장부가 어긋난다.
     * (이 경우는 반품 절차로 처리해야 한다)
     */
    public boolean isCancelable() {
        return status == OrderStatus.PAID
                || status == OrderStatus.READY
                || status == OrderStatus.SHIPPED;
    }

    /** 이미 출고되어 재고가 차감된 상태인지 (취소 시 재고 복구가 필요) */
    public boolean isStockDeducted() {
        return status == OrderStatus.SHIPPED;
    }

    /** 이미 취소된 주문인지 */
    public boolean isCanceled() {
        return status == OrderStatus.CANCELED;
    }

    /**
     * 주문 취소.
     * <p>
     * 상태 변경과 취소 정보 기록을 <b>한 메서드에서 함께</b> 처리한다.
     * 상태만 바꾸는 경로를 따로 두면 사유가 누락된 취소가 생길 수 있다.
     * <p>
     * 재고 복구 여부와 무관하게 호출된다. 출고 전 취소는 되돌릴 재고가 없을 뿐,
     * "취소 사실" 자체는 똑같이 기록해야 한다.
     *
     * @param reason   취소 사유 (선택 입력. 공백만 있으면 null 로 저장한다)
     * @param userId   처리자 ID (null 허용)
     * @param userName 처리자 이름 (null 허용)
     */
    public void cancel(String reason, Long userId, String userName) {
        this.status = OrderStatus.CANCELED;
        this.canceledAt = LocalDateTime.now();
        this.cancelReason = trimToNull(reason);
        this.canceledById = userId;
        this.canceledByName = trimToNull(userName);
    }

    /**
     * 공백만 입력된 값을 null 로 정규화한다.
     * <p>
     * {@code common.util.Texts} 에 같은 기능이 있지만, 엔티티(domain)가 상위 계층
     * 유틸에 의존하지 않도록 여기서 처리한다. 화면에서 사유 입력란을 비워 보내면
     * 빈 문자열이 들어오는데, 그대로 저장하면 "사유 있음" 과 구분되지 않는다.
     */
    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    /** 출고 완료 처리 */
    public void markShipped() {
        this.status = OrderStatus.SHIPPED;
    }

    /** 주문 전체 수량 */
    public int totalQuantity() {
        return orderItems.stream()
                .mapToInt(item -> item.getQuantity() == null ? 0 : item.getQuantity())
                .sum();
    }
}
