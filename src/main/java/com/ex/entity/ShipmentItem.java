package com.ex.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "shipment_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ShipmentItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long shipmentItemId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipment_id")
    private Shipment shipment;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_item_id")
    private OrderItem orderItem;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lot_id")
    private ProductLot lot;
    private int plannedQuantity;
    private int pickedQuantity;

    public ShipmentItem(Shipment shipment, OrderItem orderItem) {
        this.shipment = shipment;
        this.orderItem = orderItem;
        this.product = orderItem.getProduct();
        this.lot = orderItem.getLot();
        this.plannedQuantity = orderItem.getQuantity();
    }

    public ShipmentItem(
            Shipment shipment,
            OrderItem orderItem,
            OrderLotAllocation allocation) {
        this.shipment = shipment;
        this.orderItem = orderItem;
        this.product = orderItem.getProduct();
        this.lot = allocation.getProductLot();
        this.plannedQuantity = allocation.getQuantity();
    }

    public void completePicking() {
        pickedQuantity = plannedQuantity;
    }
}
