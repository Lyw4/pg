package com.ex.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "shipment", uniqueConstraints = @UniqueConstraint(columnNames = "order_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Shipment {

    public enum ShipmentStatus {
        DIRECTED("출고 지시"), PICKING("피킹 중"), INSPECTED("검수 완료"),
        SHIPPED("출고 완료"), CANCELLED("출고 취소");

        private final String label;
        ShipmentStatus(String label) { this.label = label; }
        public String getLabel() { return label; }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long shipmentId;

    @Version
    private Long version;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id")
    private CustomerOrder order;

    private String shipmentNo;
    @Enumerated(EnumType.STRING)
    private ShipmentStatus status;
    private String worker;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime shippedAt;
    private LocalDateTime cancelledAt;

    public Shipment(CustomerOrder order, String worker, String note) {
        this.order = order;
        this.worker = worker;
        this.note = note;
        this.status = ShipmentStatus.DIRECTED;
    }

    public void startPicking(String worker) {
        requireStatus(ShipmentStatus.DIRECTED);
        this.worker = worker;
        status = ShipmentStatus.PICKING;
    }

    public void inspect(String worker) {
        requireStatus(ShipmentStatus.PICKING);
        this.worker = worker;
        status = ShipmentStatus.INSPECTED;
    }

    public void complete(String worker) {
        requireStatus(ShipmentStatus.INSPECTED);
        this.worker = worker;
        status = ShipmentStatus.SHIPPED;
        shippedAt = LocalDateTime.now();
    }

    public void cancel(String note) {
        if (status == ShipmentStatus.SHIPPED || status == ShipmentStatus.CANCELLED) {
            throw new IllegalStateException("완료되거나 취소된 출고는 취소할 수 없습니다.");
        }
        this.note = note;
        status = ShipmentStatus.CANCELLED;
        cancelledAt = LocalDateTime.now();
    }

    public void cancelCompleted(String note) {
        if (status != ShipmentStatus.SHIPPED) {
            throw new IllegalStateException("출고 완료된 건만 완료 취소할 수 있습니다.");
        }
        this.note = note;
        this.shippedAt = null;
        this.status = ShipmentStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
    }

    private void requireStatus(ShipmentStatus expected) {
        if (status != expected) {
            throw new IllegalStateException(expected.getLabel() + " 단계에서만 처리할 수 있습니다.");
        }
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        shipmentNo = "SHP-" + createdAt.toLocalDate().toString().replace("-", "")
                + "-" + order.getOrderId();
    }
}
