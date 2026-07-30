package com.ex.entity;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
@Table(name = "delivery_status_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliveryStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long historyId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "delivery_id")
    private Delivery delivery;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 30)
    private Delivery.DeliveryStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 30, nullable = false)
    private Delivery.DeliveryStatus changedStatus;

    @Column(length = 500)
    private String note;
    private LocalDateTime changedAt;

    public DeliveryStatusHistory(
            Delivery delivery,
            Delivery.DeliveryStatus previousStatus,
            Delivery.DeliveryStatus changedStatus,
            String note) {
        this.delivery = delivery;
        this.previousStatus = previousStatus;
        this.changedStatus = changedStatus;
        this.note = note;
    }

    @PrePersist
    void onCreate() {
        changedAt = LocalDateTime.now();
    }
}
