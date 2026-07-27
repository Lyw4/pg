package com.feedflow.repository;

import com.feedflow.domain.MovementType;
import com.feedflow.domain.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    /**
     * 입·출고 이력 조회.
     *
     * @param movementType 이동 유형 (null 이면 전체)
     * @param productId    품목 (null 이면 전체)
     */
    @Query(value = """
            select m
            from StockMovement m
            join fetch m.product p
            join fetch m.lot l
            left join fetch m.bin b
            where (:movementType is null or m.movementType = :movementType)
              and (:productId is null or p.productId = :productId)
            """,
            countQuery = """
            select count(m)
            from StockMovement m
            where (:movementType is null or m.movementType = :movementType)
              and (:productId is null or m.product.productId = :productId)
            """)
    Page<StockMovement> search(@Param("movementType") MovementType movementType,
                               @Param("productId") Long productId,
                               Pageable pageable);

    /** 특정 기간의 유형별 처리 건수 (오늘 입고 건수 등) */
    long countByMovementTypeAndCreatedAtBetween(MovementType movementType,
                                                LocalDateTime start,
                                                LocalDateTime end);

    /** 특정 기간의 유형별 처리 수량 합계 */
    @Query("""
            select sum(m.quantity)
            from StockMovement m
            where m.movementType = :movementType
              and m.createdAt between :start and :end
            """)
    Long sumQuantityByTypeBetween(@Param("movementType") MovementType movementType,
                                  @Param("start") LocalDateTime start,
                                  @Param("end") LocalDateTime end);
}
