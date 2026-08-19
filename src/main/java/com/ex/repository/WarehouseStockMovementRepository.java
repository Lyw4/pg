package com.ex.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.ex.entity.WarehouseStockMovement;
import com.ex.entity.MovementType;

public interface WarehouseStockMovementRepository
        extends JpaRepository<WarehouseStockMovement, Long> {

    boolean existsByMemo(String memo);

    boolean existsBySourceBinBinIdOrDestinationBinBinId(
            Long sourceBinId,
            Long destinationBinId);

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

    @EntityGraph(attributePaths = {"sourceBin", "sourceBin.warehouse", "lot"})
    List<WarehouseStockMovement>
            findByOrderIdAndMovementTypeAndLotLotIdOrderByCreatedAtAsc(
                    Long orderId,
                    MovementType movementType,
                    Long lotId);
}
