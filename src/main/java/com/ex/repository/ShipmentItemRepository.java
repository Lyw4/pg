package com.ex.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ex.entity.FarmCustomer.CustomerStatus;
import com.ex.entity.Shipment.ShipmentStatus;
import com.ex.entity.ShipmentItem;

public interface ShipmentItemRepository extends JpaRepository<ShipmentItem, Long> {
    @EntityGraph(attributePaths = {"shipment", "product", "lot"})
    List<ShipmentItem> findByShipmentShipmentId(Long shipmentId);

    /** 거래 중인 농장에 실제 출고 완료된 품목만 월별 납품량 집계에 사용합니다. */
    @Query("""
            select shipmentItem
            from ShipmentItem shipmentItem
            join fetch shipmentItem.shipment shipment
            join fetch shipment.order customerOrder
            join fetch customerOrder.farmCustomer farm
            where shipment.status = :shipmentStatus
              and shipment.shippedAt >= :fromInclusive
              and shipment.shippedAt < :toExclusive
              and farm.status = :farmStatus
            order by shipment.shippedAt asc
            """)
    List<ShipmentItem> findFarmDeliveryItems(
            @Param("shipmentStatus") ShipmentStatus shipmentStatus,
            @Param("farmStatus") CustomerStatus farmStatus,
            @Param("fromInclusive") LocalDateTime fromInclusive,
            @Param("toExclusive") LocalDateTime toExclusive);
}
