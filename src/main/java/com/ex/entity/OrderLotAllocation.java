package com.ex.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "order_lot_allocations", indexes = {
        @Index(
                name = "idx_order_lot_alloc_order_item",
                columnList = "order_item_id"),
        @Index(
                name = "idx_order_lot_alloc_product_lot",
                columnList = "product_lot_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class OrderLotAllocation extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "allocation_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_item_id", nullable = false)
    private OrderItem orderItem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_lot_id", nullable = false)
    private ProductLot productLot;

    @Column(nullable = false)
    private int quantity;

    public OrderLotAllocation(ProductLot productLot, int quantity) {
        this.productLot = productLot;
        this.quantity = quantity;
    }

    void assignOrderItem(OrderItem orderItem) {
        this.orderItem = orderItem;
    }
}
