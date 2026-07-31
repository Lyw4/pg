package com.ex.service;

import com.ex.dto.CreateOrderRequest;
import com.ex.dto.OrderResponse;
import com.ex.entity.*;
import com.ex.repository.ProductLotRepository;
import com.ex.repository.ProductRepository;
import com.ex.repository.PurchaseOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private static final int FREE_DELIVERY_THRESHOLD = 150_000;
    private static final int DELIVERY_FEE = 5_000;
    private static final double REGULAR_DELIVERY_DISCOUNT_RATE = 0.03;

    private final ProductRepository productRepository;
    private final ProductLotRepository productLotRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        PurchaseOrder order = PurchaseOrder.builder()
                .orderNumber(createOrderNumber())
                .customerName(request.customerName())
                .phone(request.phone())
                .address(request.address())
                .detailAddress(request.detailAddress())
                .unloadingLocation(request.unloadingLocation())
                .deliveryRequest(request.deliveryRequest())
                .paymentMethod(request.paymentMethod())
                .status(OrderStatus.PAID)
                .build();

        int productAmount = 0;

        for (CreateOrderRequest.OrderLineRequest line : request.items()) {
            Product product = productRepository.findById(line.productId())
                    .filter(Product::isActive)
                    .orElseThrow(() -> new IllegalArgumentException("주문할 수 없는 상품입니다: " + line.productId()));

            int lineAmount = Math.multiplyExact(product.getPrice(), line.quantity());
            productAmount = Math.addExact(productAmount, lineAmount);

            OrderItem orderItem = OrderItem.builder()
                    .product(product)
                    .productName(product.getName())
                    .quantity(line.quantity())
                    .unitPrice(product.getPrice())
                    .lineAmount(lineAmount)
                    .build();
            decreaseStock(product.getId(), line.quantity(), orderItem);
            order.addItem(orderItem);
        }

        int deliveryFee = productAmount >= FREE_DELIVERY_THRESHOLD ? 0 : DELIVERY_FEE;
        int discountAmount = request.regularDelivery()
                ? (int) Math.round(productAmount * REGULAR_DELIVERY_DISCOUNT_RATE)
                : 0;
        int totalAmount = productAmount + deliveryFee - discountAmount;

        order.setProductAmount(productAmount);
        order.setDeliveryFee(deliveryFee);
        order.setDiscountAmount(discountAmount);
        order.setTotalAmount(totalAmount);

        PurchaseOrder savedOrder = purchaseOrderRepository.save(order);

        return new OrderResponse(
                savedOrder.getId(),
                savedOrder.getOrderNumber(),
                savedOrder.getStatus(),
                savedOrder.getProductAmount(),
                savedOrder.getDeliveryFee(),
                savedOrder.getDiscountAmount(),
                savedOrder.getTotalAmount(),
                savedOrder.getCreatedAt()
        );
    }

    @Transactional
    public OrderResponse cancelOrder(String orderNumber, String phone) {
        PurchaseOrder order = purchaseOrderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다."));
        order.cancel(phone);
        order.getItems().stream()
                .flatMap(item -> item.getLotAllocations().stream())
                .forEach(allocation -> allocation.getProductLot().increase(allocation.getQuantity()));
        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse findOrder(String orderNumber, String phone) {
        PurchaseOrder order = purchaseOrderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다."));
        if (!order.getPhone().equals(phone)) {
            throw new IllegalArgumentException("주문자 전화번호가 일치하지 않습니다.");
        }
        return toResponse(order);
    }

    private void decreaseStock(Long productId, int requestedQuantity, OrderItem orderItem) {
        List<ProductLot> lots = productLotRepository
                .findByProductIdAndQuantityGreaterThanOrderByExpirationDateAsc(productId, 0);

        int totalStock = lots.stream().mapToInt(ProductLot::getQuantity).sum();
        if (totalStock < requestedQuantity) {
            throw new IllegalArgumentException("상품 재고가 부족합니다.");
        }

        int remaining = requestedQuantity;
        for (ProductLot lot : lots) {
            if (remaining == 0) {
                break;
            }
            int deduction = Math.min(lot.getQuantity(), remaining);
            lot.decrease(deduction);
            orderItem.addLotAllocation(OrderLotAllocation.builder()
                    .productLot(lot)
                    .quantity(deduction)
                    .build());
            remaining -= deduction;
        }
    }

    private String createOrderNumber() {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        return "FF-" + date + "-" + random;
    }

    private OrderResponse toResponse(PurchaseOrder order) {
        return new OrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus(),
                order.getProductAmount(),
                order.getDeliveryFee(),
                order.getDiscountAmount(),
                order.getTotalAmount(),
                order.getCreatedAt()
        );
    }
}
