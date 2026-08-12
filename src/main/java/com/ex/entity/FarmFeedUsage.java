package com.ex.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 농장이 직접 기록한 월별 실제 사료 소비량입니다. */
@Entity
@Table(name = "farm_feed_usage", uniqueConstraints = @UniqueConstraint(
        columnNames = {"farm_customer_id", "usage_month"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FarmFeedUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long usageId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "farm_customer_id")
    private FarmCustomer farmCustomer;

    @Column(name = "usage_month", nullable = false)
    private LocalDate usageMonth;

    private int predictedQuantity;
    private int actualQuantity;
    private int adjustedNextMonthQuantity;

    @Column(length = 200)
    private String note;

    private LocalDateTime createdAt;

    public FarmFeedUsage(
            FarmCustomer farmCustomer,
            LocalDate usageMonth,
            int predictedQuantity,
            int actualQuantity,
            int adjustedNextMonthQuantity,
            String note) {
        this.farmCustomer = farmCustomer;
        this.usageMonth = usageMonth.withDayOfMonth(1);
        update(predictedQuantity, actualQuantity,
                adjustedNextMonthQuantity, note);
    }

    public void update(
            int predictedQuantity,
            int actualQuantity,
            int adjustedNextMonthQuantity,
            String note) {
        if (predictedQuantity < 0 || actualQuantity < 0
                || adjustedNextMonthQuantity < 0) {
            throw new IllegalArgumentException("사료 사용량은 0포대 이상이어야 합니다.");
        }
        this.predictedQuantity = predictedQuantity;
        this.actualQuantity = actualQuantity;
        this.adjustedNextMonthQuantity = adjustedNextMonthQuantity;
        this.note = note == null || note.isBlank() ? null : note.trim();
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
