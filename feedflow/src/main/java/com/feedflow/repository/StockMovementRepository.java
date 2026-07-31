package com.feedflow.repository;

import com.feedflow.admin.dto.CenterActivityRow;
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
     * 타임라인이 품목 · 로트 · 구역 · <b>센터</b>를 모두 표시하므로 {@code join fetch} 로
     * 한 번에 읽는다. (건수만큼 쿼리가 반복되는 N+1 을 막는다)
     * <p>
     * 구역의 센터까지 함께 읽어야 한다. 구역은 {@code left join} 이므로
     * <b>센터도 반드시 {@code left join} 이어야 한다.</b> {@code join fetch b.center} 로 쓰면
     * Hibernate 가 inner join 을 만들어 <b>구역이 없는 이력(출고 · 취소)이 타임라인에서
     * 통째로 사라진다.</b>
     */
    @Query("""
            select m
            from StockMovement m
            join fetch m.product p
            join fetch m.lot l
            left join fetch m.bin b
            left join fetch b.center bc
            left join fetch m.fromBin fb
            left join fetch fb.center fbc
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

    /**
     * 센터별 · 유형별 기간 실적 집계 (전국 대시보드용).
     * <p>
     * 이력의 <b>도착 구역({@code binId})</b> 이 속한 센터를 기준으로 센다.
     * 이관 출고는 도착지가 출발 센터의 운송 중 구역이므로 <b>출발 센터</b> 실적으로,
     * 이관 입고는 <b>도착 센터</b> 실적으로 잡힌다. "나갔다 / 들어왔다" 가 의도대로 갈린다.
     * <p>
     * 구역이 없는 이력(로트 단위 조정 등)은 어느 센터의 실적인지 알 수 없으므로
     * {@code join}(inner) 으로 자연히 빠진다. 이 집계에서는 그것이 맞다 —
     * 센터를 모르는 실적을 특정 센터에 얹으면 숫자가 틀린다.
     * <p>
     * 유형을 컬럼으로 펼치지 않고 유형별 한 행으로 내려보낸다. 펼치는 것은 화면의 일이고,
     * 여기서 {@code case when} 을 늘리면 유형이 추가될 때마다 쿼리를 고쳐야 한다.
     */
    @Query("""
            select new com.feedflow.admin.dto.CenterActivityRow(
                       c.centerId, c.name, m.movementType, sum(m.quantity), count(m))
            from StockMovement m
                join m.bin b
                join b.center c
            where m.createdAt between :start and :end
            group by c.centerId, c.name, c.centerCode, m.movementType
            order by c.centerCode asc
            """)
    List<CenterActivityRow> findActivityByCenter(@Param("start") LocalDateTime start,
                                                @Param("end") LocalDateTime end);
}
