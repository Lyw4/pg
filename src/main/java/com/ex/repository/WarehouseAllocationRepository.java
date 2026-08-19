package com.ex.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import com.ex.entity.WarehouseAllocation;

public interface WarehouseAllocationRepository
        extends JpaRepository<WarehouseAllocation, Long> {

    Optional<WarehouseAllocation>
            findByWarehouseWarehouseIdAndProductProductId(
                    Long warehouseId,
                    Long productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select allocation
            from WarehouseAllocation allocation
            join fetch allocation.warehouse
            join fetch allocation.product product
            join fetch product.manufacturer
            where allocation.allocationId = :allocationId
            """)
    Optional<WarehouseAllocation> findByIdForUpdate(
            @Param("allocationId") Long allocationId);

    @EntityGraph(attributePaths = {
        "warehouse",
        "product",
        "product.manufacturer"
    })
    List<WarehouseAllocation>
            findAllByOrderByWarehouseDisplayOrderAscProductAnimalTypeAscProductNameAsc();

    List<WarehouseAllocation> findByProductProductId(Long productId);

    @EntityGraph(attributePaths = {"warehouse", "product"})
    List<WarehouseAllocation> findByWarehouseWarehouseId(Long warehouseId);
}
