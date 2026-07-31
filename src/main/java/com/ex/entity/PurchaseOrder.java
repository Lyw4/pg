package com.ex.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "purchase_order", indexes = {
        @Index(name = "idx_order_number", columnList = "order_number", unique = true)
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class PurchaseOrder extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "purchase_order_id")
    private Long id;

    @Column(name = "order_number", nullable = false, unique = true, length = 40)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @Column(name = "customer_name", nullable = false, length = 40)
    private String customerName;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(nullable = false, length = 200)
    private String address;

    @Column(name = "detail_address", length = 200)
    private String detailAddress;

    @Column(name = "unloading_location", length = 200)
    private String unloadingLocation;

    @Column(name = "delivery_request", length = 300)
    private String deliveryRequest;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 30)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    @Column(name = "product_amount", nullable = false)
    private int productAmount;

    @Column(name = "delivery_fee", nullable = false)
    private int deliveryFee;

    @Column(name = "discount_amount", nullable = false)
    private int discountAmount;

    @Column(name = "total_amount", nullable = false)
    private int totalAmount;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    public void addItem(OrderItem item) {
        items.add(item);
        item.assignOrder(this);
    }

    public void cancel(String requestPhone) {
        if (!phone.equals(requestPhone)) {
            throw new IllegalArgumentException("주문자 전화번호가 일치하지 않습니다.");
        }
        if (status == OrderStatus.SHIPPING || status == OrderStatus.DELIVERED) {
            throw new IllegalArgumentException("배송이 시작된 주문은 취소할 수 없습니다.");
        }
        if (status == OrderStatus.CANCELLED) {
            throw new IllegalArgumentException("이미 취소된 주문입니다.");
        }
        status = OrderStatus.CANCELLED;
    }
}
