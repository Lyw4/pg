package com.ex.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.ex.entity.Delivery;

public interface DeliveryRepository extends JpaRepository<Delivery, Long> {

    Optional<Delivery> findByOrderOrderId(Long orderId);

    // 배송 정보와 주문을 한 번에 조회
    @EntityGraph(attributePaths = {
        "order",
        "order.fulfillmentWarehouse",
        "order.farmCustomer"
    })
    List<Delivery> findAllByOrderByDeliveryIdDesc();

    @EntityGraph(attributePaths = {
        "order",
        "order.fulfillmentWarehouse",
        "order.farmCustomer"
    })
    Optional<Delivery> findDetailByDeliveryId(Long deliveryId);
}
