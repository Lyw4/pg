package com.ex.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 관리자가 조정하는 축종별 추천 모델 기준입니다. */
@Entity
@Table(name = "feed_model_policy", uniqueConstraints =
        @UniqueConstraint(columnNames = "animal_type"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeedModelPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long policyId;

    @Column(name = "animal_type", nullable = false, length = 30)
    private String animalType;

    @Column(nullable = false, precision = 8, scale = 3)
    private BigDecimal bagsPerHead;

    private int preferredFeedWeight;
    private int warehouseStockWeight;

    @Column(length = 1000)
    private String excludedProductIds;

    private int modelVersion;
    private LocalDateTime updatedAt;

    public FeedModelPolicy(
            String animalType,
            BigDecimal bagsPerHead,
            int preferredFeedWeight,
            int warehouseStockWeight) {
        this.animalType = animalType;
        this.modelVersion = 0;
        update(bagsPerHead, preferredFeedWeight,
                warehouseStockWeight, "");
    }

    public void update(
            BigDecimal bagsPerHead,
            int preferredFeedWeight,
            int warehouseStockWeight,
            String excludedProductIds) {
        if (bagsPerHead == null || bagsPerHead.signum() <= 0) {
            throw new IllegalArgumentException("마리당 소비계수는 0보다 커야 합니다.");
        }
        if (preferredFeedWeight < 0 || warehouseStockWeight < 0) {
            throw new IllegalArgumentException("추천 가중치는 0 이상이어야 합니다.");
        }
        this.bagsPerHead = bagsPerHead;
        this.preferredFeedWeight = preferredFeedWeight;
        this.warehouseStockWeight = warehouseStockWeight;
        this.excludedProductIds = excludedProductIds == null
                ? "" : excludedProductIds.trim();
        this.modelVersion = Math.max(1, modelVersion + 1);
        this.updatedAt = LocalDateTime.now();
    }

    @PrePersist
    void onCreate() {
        updatedAt = LocalDateTime.now();
    }
}
