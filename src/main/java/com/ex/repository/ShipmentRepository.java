package com.ex.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import com.ex.entity.Shipment;
import com.ex.entity.Shipment.ShipmentStatus;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {
    @EntityGraph(attributePaths = {
        "order",
        "order.fulfillmentWarehouse",
        "order.farmCustomer"
    })
    List<Shipment> findAllByOrderByCreatedAtDesc();
    Optional<Shipment> findByOrderOrderId(Long orderId);
    @EntityGraph(attributePaths = {
        "order",
        "order.fulfillmentWarehouse",
        "order.farmCustomer"
    })
    Optional<Shipment> findDetailByOrderOrderId(Long orderId);
    long countByStatusNotIn(List<ShipmentStatus> statuses);
}
