package com.ex.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "warehouse",
        uniqueConstraints = @UniqueConstraint(columnNames = "warehouse_code"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Warehouse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long warehouseId;

    @Column(name = "warehouse_code", nullable = false, length = 10)
    private String code;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false, length = 150)
    private String address;

    @Column(nullable = false, length = 100)
    private String serviceArea;

    @Column(nullable = false, length = 100)
    private String operationFocus;

    private int displayOrder;

    private boolean active = true;

    public Warehouse(
            String code,
            String name,
            String address,
            String serviceArea,
            String operationFocus,
            int displayOrder) {
        this.code = code;
        this.name = name;
        this.address = address;
        this.serviceArea = serviceArea;
        this.operationFocus = operationFocus;
        this.displayOrder = displayOrder;
    }

    public void updateDetails(
            String name,
            String address,
            String serviceArea,
            String operationFocus,
            int displayOrder) {
        this.name = name;
        this.address = address;
        this.serviceArea = serviceArea;
        this.operationFocus = operationFocus;
        this.displayOrder = displayOrder;
        this.active = true;
    }
}
