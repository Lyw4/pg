package com.ex.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ex.entity.InboundReplenishmentRequest;
import com.ex.entity.InboundReplenishmentRequest.Status;

import jakarta.persistence.LockModeType;

public interface InboundReplenishmentRequestRepository
        extends JpaRepository<InboundReplenishmentRequest, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select request
            from InboundReplenishmentRequest request
            join fetch request.warehouse
            join fetch request.product product
            join fetch product.manufacturer
            left join fetch request.farmCustomer
            where request.warehouse.warehouseId = :warehouseId
              and request.product.productId = :productId
              and request.status = :status
            """)
    Optional<InboundReplenishmentRequest> findPendingForUpdate(
            @Param("warehouseId") Long warehouseId,
            @Param("productId") Long productId,
            @Param("status") Status status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select request
            from InboundReplenishmentRequest request
            join fetch request.warehouse
            join fetch request.product product
            join fetch product.manufacturer
            left join fetch request.farmCustomer
            where request.requestId = :requestId
            """)
    Optional<InboundReplenishmentRequest> findByIdForUpdate(
            @Param("requestId") Long requestId);

    @EntityGraph(attributePaths = {
            "warehouse", "product", "product.manufacturer", "farmCustomer"
    })
    List<InboundReplenishmentRequest> findAllByOrderByRequestedAtDesc();

    boolean existsByWarehouseWarehouseIdAndProductProductIdAndStatus(
            Long warehouseId, Long productId, Status status);
}
