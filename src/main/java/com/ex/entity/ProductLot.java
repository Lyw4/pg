package com.ex.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "product_lot", indexes = {
        @Index(name = "idx_lot_product_expiration", columnList = "product_id, expiration_date")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ProductLot extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_lot_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "lot_number", nullable = false, unique = true, length = 50)
    private String lotNumber;

    @Column(name = "manufactured_date", nullable = false)
    private LocalDate manufacturedDate;

    @Column(name = "expiration_date", nullable = false)
    private LocalDate expirationDate;

    @Column(nullable = false)
    private int quantity;

    public void decrease(int amount) {
        if (amount <= 0 || quantity < amount) {
            throw new IllegalArgumentException("LOT 재고가 부족합니다.");
        }
        quantity -= amount;
    }

    public void increase(int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("복원 수량은 1개 이상이어야 합니다.");
        }
        quantity = Math.addExact(quantity, amount);
    }
}
