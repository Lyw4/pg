package com.ex.repository;

import java.util.List;
import java.util.Optional;
import java.util.Collection;
import java.time.LocalDate;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import com.ex.entity.BinInventory;
import com.ex.entity.BinPurpose;

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

    @Query("""
            select sum(inventory.quantity)
            from BinInventory inventory
            where inventory.lot.product.productId = :productId
              and inventory.quantity > 0
              and inventory.bin.active = true
              and inventory.bin.purpose in :purposes
              and inventory.lot.expirationDate >= :sellableFrom
            """)
    Integer sumSellableQuantityByProductId(
            @Param("productId") Long productId,
            @Param("purposes") Collection<BinPurpose> purposes,
            @Param("sellableFrom") LocalDate sellableFrom);

    @Query("""
            select inventory.lot.product.productId, sum(inventory.quantity)
            from BinInventory inventory
            where inventory.lot.product.productId in :productIds
              and inventory.quantity > 0
              and inventory.bin.active = true
              and inventory.bin.purpose in :purposes
              and inventory.lot.expirationDate >= :sellableFrom
            group by inventory.lot.product.productId
            """)
    List<Object[]> sumSellableQuantityByProductIds(
            @Param("productIds") Collection<Long> productIds,
            @Param("purposes") Collection<BinPurpose> purposes,
            @Param("sellableFrom") LocalDate sellableFrom);

    @Query("""
            select sum(inventory.quantity)
            from BinInventory inventory
            where inventory.lot.lotId in :lotIds
              and inventory.quantity > 0
              and inventory.bin.active = true
              and inventory.bin.purpose in :purposes
              and inventory.lot.expirationDate >= :sellableFrom
            """)
    Integer sumSellableQuantityByLotIds(
            @Param("lotIds") Collection<Long> lotIds,
            @Param("purposes") Collection<BinPurpose> purposes,
            @Param("sellableFrom") LocalDate sellableFrom);

    Optional<BinInventory> findByLotLotIdAndBinBinId(
            Long lotId,
            Long binId);

    List<BinInventory> findByBinBinId(Long binId);
}
