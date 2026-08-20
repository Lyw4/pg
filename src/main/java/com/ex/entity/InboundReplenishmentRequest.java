package com.ex.entity;

import java.time.LocalDate;
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
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 정기 납품 후 부족해진 창고 재고를 관리자가 승인하기 위한 1회성 입고 요청입니다. */
@Entity
@Table(name = "inbound_replenishment_request")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InboundReplenishmentRequest {

    public enum Status {
        PENDING("승인 대기"),
        APPROVED("입고 완료"),
        REJECTED("반려");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long requestId;

    @Version
    @Column(nullable = false)
    private long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    private Warehouse warehouse;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "farm_customer_id")
    private FarmCustomer farmCustomer;

    @Column(nullable = false)
    private int requestedQuantity;

    @Column(nullable = false)
    private LocalDate referenceDate;

    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(nullable = false)
    private LocalDateTime requestedAt;

    private LocalDateTime processedAt;

    @Column(length = 100)
    private String processedBy;

    public InboundReplenishmentRequest(
            Warehouse warehouse,
            Product product,
            FarmCustomer farmCustomer,
            int requestedQuantity,
            LocalDate referenceDate,
            String reason) {
        if (requestedQuantity <= 0) {
            throw new IllegalArgumentException("입고 요청 수량은 1포 이상이어야 합니다.");
        }
        this.warehouse = warehouse;
        this.product = product;
        this.farmCustomer = farmCustomer;
        this.requestedQuantity = requestedQuantity;
        this.referenceDate = referenceDate;
        this.reason = reason;
        this.status = Status.PENDING;
    }

    /** 같은 창고·상품의 미처리 요청은 최신 부족량 하나로 합칩니다. */
    public void refresh(int requestedQuantity, FarmCustomer farmCustomer, String reason) {
        requirePending();
        if (requestedQuantity <= 0) {
            throw new IllegalArgumentException("입고 요청 수량은 1포 이상이어야 합니다.");
        }
        this.requestedQuantity = Math.max(this.requestedQuantity, requestedQuantity);
        if (this.farmCustomer == null) this.farmCustomer = farmCustomer;
        this.reason = reason;
    }

    public void approve(String operator) {
        requirePending();
        this.status = Status.APPROVED;
        this.processedAt = LocalDateTime.now();
        this.processedBy = normalizeOperator(operator);
    }

    public void reject(String operator) {
        requirePending();
        this.status = Status.REJECTED;
        this.processedAt = LocalDateTime.now();
        this.processedBy = normalizeOperator(operator);
    }

    private void requirePending() {
        if (status != Status.PENDING) {
            throw new IllegalStateException("이미 처리된 입고 요청입니다.");
        }
    }

    private String normalizeOperator(String operator) {
        return operator == null || operator.isBlank() ? "관리자" : operator.trim();
    }

    @PrePersist
    void onCreate() {
        if (requestedAt == null) requestedAt = LocalDateTime.now();
        if (status == null) status = Status.PENDING;
    }
}
