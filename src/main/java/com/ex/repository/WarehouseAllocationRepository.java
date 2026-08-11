package com.ex.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.ex.entity.WarehouseAllocation;

public interface WarehouseAllocationRepository
        extends JpaRepository<WarehouseAllocation, Long> {

    Optional<WarehouseAllocation>
            findByWarehouseWarehouseIdAndProductProductId(
                    Long warehouseId,
                    Long productId);

    @EntityGraph(attributePaths = {
        "warehouse",
        "product",
        "product.manufacturer"
    })
    List<WarehouseAllocation>
            findAllByOrderByWarehouseDisplayOrderAscProductAnimalTypeAscProductNameAsc();

    List<WarehouseAllocation> findByProductProductId(Long productId);
}
