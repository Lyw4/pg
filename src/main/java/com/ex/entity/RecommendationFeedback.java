package com.ex.entity;

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

/** 추천 제품에 대한 회원별 최신 평가입니다. */
@Entity
@Table(name = "recommendation_feedback", uniqueConstraints = @UniqueConstraint(
        columnNames = {"farm_customer_id", "product_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecommendationFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long feedbackId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "farm_customer_id")
    private FarmCustomer farmCustomer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    private boolean suitable;

    @Column(length = 300)
    private String comment;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public RecommendationFeedback(
            FarmCustomer farmCustomer,
            Product product,
            boolean suitable,
            String comment) {
        this.farmCustomer = farmCustomer;
        this.product = product;
        update(suitable, comment);
    }

    public void update(boolean suitable, String comment) {
        this.suitable = suitable;
        this.comment = comment == null || comment.isBlank()
                ? null : comment.trim();
        this.updatedAt = LocalDateTime.now();
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }
}
