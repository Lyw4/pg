package com.ex.repository;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import com.ex.entity.ShipmentItem;

public interface ShipmentItemRepository extends JpaRepository<ShipmentItem, Long> {
    @EntityGraph(attributePaths = {"shipment", "product", "lot"})
    List<ShipmentItem> findByShipmentShipmentId(Long shipmentId);
}
