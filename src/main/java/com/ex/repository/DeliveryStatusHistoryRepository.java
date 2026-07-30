package com.ex.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.ex.entity.DeliveryStatusHistory;

public interface DeliveryStatusHistoryRepository
        extends JpaRepository<DeliveryStatusHistory, Long> {

    @EntityGraph(attributePaths = "delivery")
    List<DeliveryStatusHistory>
            findByDeliveryDeliveryIdOrderByChangedAtDesc(Long deliveryId);

    long countByNoteStartingWith(String prefix);
}
