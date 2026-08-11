package com.ex.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import com.ex.entity.BinInventory;

public interface BinInventoryRepository
        extends JpaRepository<BinInventory, Long> {

    @EntityGraph(attributePaths = {
        "lot",
        "lot.product",
        "bin",
        "bin.warehouse"
    })
    List<BinInventory> findAllByOrderByBinBinCodeAsc();

    @EntityGraph(attributePaths = {
        "lot",
        "lot.product",
        "bin",
        "bin.warehouse"
    })
    List<BinInventory>
            findByBinWarehouseWarehouseIdOrderByBinBinCodeAsc(
                    Long warehouseId);

    @EntityGraph(attributePaths = {
        "lot",
        "lot.product",
        "bin",
        "bin.warehouse"
    })
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<BinInventory>
            findByLotLotIdAndQuantityGreaterThanOrderByBinBinCodeAsc(
                    Long lotId,
                    int quantity);

    @EntityGraph(attributePaths = {
        "lot",
        "lot.product",
        "bin",
        "bin.warehouse"
    })
    List<BinInventory>
            findByLotProductProductIdAndQuantityGreaterThanOrderByBinBinCodeAsc(
                    Long productId,
                    int quantity);

    Optional<BinInventory> findByLotLotIdAndBinBinId(
            Long lotId,
            Long binId);

    List<BinInventory> findByBinBinId(Long binId);
}
