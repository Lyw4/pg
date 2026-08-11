package com.ex.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.ex.entity.WarehouseStockMovement;

public interface WarehouseStockMovementRepository
        extends JpaRepository<WarehouseStockMovement, Long> {

    boolean existsByMemo(String memo);

    @EntityGraph(attributePaths = {
        "lot",
        "lot.product",
        "sourceBin",
        "sourceBin.warehouse",
        "destinationBin",
        "destinationBin.warehouse"
    })
    List<WarehouseStockMovement>
            findTop200ByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {
        "lot",
        "lot.product",
        "sourceBin",
        "sourceBin.warehouse",
        "destinationBin",
        "destinationBin.warehouse"
    })
    List<WarehouseStockMovement>
            findByLotLotIdOrderByCreatedAtDesc(Long lotId);
}
