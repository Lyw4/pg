package com.ex.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "order_lot_allocation", indexes = {
        @Index(
                name = "idx_allocation_purchase_order_item",
                columnList = "purchase_order_item_id"),
        @Index(name = "idx_allocation_product_lot", columnList = "product_lot_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class OrderLotAllocation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_lot_allocation_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_order_item_id", nullable = false)
    private PurchaseOrderItem orderItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_lot_id", nullable = false)
    private ProductLot productLot;

    @Column(nullable = false)
    private int quantity;

    void assignOrderItem(PurchaseOrderItem orderItem) {
        this.orderItem = orderItem;
    }
}
