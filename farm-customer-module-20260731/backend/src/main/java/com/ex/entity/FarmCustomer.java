package com.ex.entity;

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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "farm_customer",
        uniqueConstraints = @UniqueConstraint(columnNames = "farm_code"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FarmCustomer {

    public enum CustomerStatus {
        ACTIVE("거래 중"),
        PAUSED("거래 보류");

        private final String label;

        CustomerStatus(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long farmCustomerId;

    @Column(name = "farm_code", nullable = false, length = 20)
    private String farmCode;

    @Column(nullable = false, length = 80)
    private String farmName;

    @Column(nullable = false, length = 30)
    private String representativeName;

    @Column(nullable = false, length = 30)
    private String phone;

    @Column(nullable = false, length = 10)
    private String postalCode;

    @Column(nullable = false, length = 180)
    private String address;

    private Double latitude;
    private Double longitude;

    @Column(nullable = false, length = 30)
    private String animalType;

    private int livestockCount;
    private int monthlyFeedQuantity;

    @Column(nullable = false, length = 80)
    private String preferredFeed;

    private int recurringDeliveryDay;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id")
    private Warehouse assignedWarehouse;

    private double distanceKm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CustomerStatus status;

    @Column(length = 200)
    private String notes;

    private boolean demoData = true;
    private LocalDateTime createdAt;

    public FarmCustomer(
            String farmCode,
            String farmName,
            String representativeName,
            String phone,
            String postalCode,
            String address,
            double latitude,
            double longitude,
            String animalType,
            int livestockCount,
            int monthlyFeedQuantity,
            String preferredFeed,
            int recurringDeliveryDay,
            Warehouse assignedWarehouse,
            double distanceKm,
            CustomerStatus status,
            String notes) {
        this.farmCode = farmCode;
        updateDetails(
                farmName,
                representativeName,
                phone,
                postalCode,
                address,
                latitude,
                longitude,
                animalType,
                livestockCount,
                monthlyFeedQuantity,
                preferredFeed,
                recurringDeliveryDay,
                assignedWarehouse,
                distanceKm,
                status,
                notes);
    }

    public void updateDetails(
            String farmName,
            String representativeName,
            String phone,
            String postalCode,
            String address,
            double latitude,
            double longitude,
            String animalType,
            int livestockCount,
            int monthlyFeedQuantity,
            String preferredFeed,
            int recurringDeliveryDay,
            Warehouse assignedWarehouse,
            double distanceKm,
            CustomerStatus status,
            String notes) {
        if (livestockCount < 0 || monthlyFeedQuantity < 0) {
            throw new IllegalArgumentException(
                    "사육 규모와 월 예상 사료량은 0 이상이어야 합니다.");
        }
        if (recurringDeliveryDay < 1 || recurringDeliveryDay > 28) {
            throw new IllegalArgumentException(
                    "정기 배송일은 1일부터 28일 사이여야 합니다.");
        }
        this.farmName = farmName;
        this.representativeName = representativeName;
        this.phone = phone;
        this.postalCode = postalCode;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.animalType = animalType;
        this.livestockCount = livestockCount;
        this.monthlyFeedQuantity = monthlyFeedQuantity;
        this.preferredFeed = preferredFeed;
        this.recurringDeliveryDay = recurringDeliveryDay;
        this.assignedWarehouse = assignedWarehouse;
        this.distanceKm = distanceKm;
        this.status = status;
        this.notes = notes;
        this.demoData = true;
    }

    public String getDistanceLabel() {
        return "약 %.1fkm".formatted(distanceKm);
    }

    public void changeStatus(CustomerStatus status) {
        if (status == null) {
            throw new IllegalArgumentException(
                    "변경할 거래 상태를 선택해 주세요.");
        }
        this.status = status;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
