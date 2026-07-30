package com.feedflow.repository;

import com.feedflow.domain.MovementType;
import com.feedflow.domain.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

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
    /**
     * 특정 주문의 특정 유형 이력 조회 (출고 취소 시 복구 대상 산출용).
     * <p>
     * 복구할 때 로트 · 구역 · 품목을 모두 쓰므로 {@code join fetch} 로 한 번에 가져온다.
     * 오래된 출고부터 처리하도록 이력 순서를 유지한다.
     */
    @Query("""
            select m
            from StockMovement m
            join fetch m.lot l
            join fetch l.product p
            left join fetch m.bin b
            where m.orderId = :orderId
              and m.movementType = :movementType
            order by m.movementId asc
            """)
    List<StockMovement> findByOrderIdAndType(@Param("orderId") Long orderId,
                                             @Param("movementType") MovementType movementType);

    /**
     * 특정 로트의 전체 이력 조회 (이력 추적 타임라인용).
     * <p>
     * 입고 · 출고 · 출고취소 · 폐기 · 조정을 유형 구분 없이 <b>시간순</b>으로 가져온다.
     * 같은 시각에 여러 건이 기록될 수 있어({@code createdAt} 이 초 단위로 같을 수 있다)
     * 발생 순서를 보장하기 위해 {@code movementId} 를 2차 정렬 기준으로 둔다.
     * <p>
     * 타임라인이 품목 · 로트 · 구역을 모두 표시하므로 {@code join fetch} 로 한 번에 읽는다.
     * (건수만큼 쿼리가 반복되는 N+1 을 막는다)
     */
    @Query("""
            select m
            from StockMovement m
            join fetch m.product p
            join fetch m.lot l
            left join fetch m.bin b
            left join fetch m.fromBin fb
            where l.lotId = :lotId
            order by m.createdAt asc, m.movementId asc
            """)
    List<StockMovement> findLotHistory(@Param("lotId") Long lotId);

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
