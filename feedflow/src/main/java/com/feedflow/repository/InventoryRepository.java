package com.feedflow.repository;

import com.feedflow.domain.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    /**
     * 특정 로트가 특정 구역에 이미 보관되어 있는지 조회.
     * 존재하면 수량 합산(update), 없으면 신규 생성(insert) 한다.
     */
    Optional<Inventory> findByLot_LotIdAndBin_BinId(Long lotId, Long binId);

    /**
     * 구역의 현재 총 적재 수량. (적재 용량 초과 검증용)
     * 데이터가 없으면 null 이 반환되므로 Service 에서 0 처리한다.
     */
    @Query("select sum(i.quantity) from Inventory i where i.bin.binId = :binId")
    Long sumQuantityByBinId(@Param("binId") Long binId);

    /** 폐기 처리를 위해 재고 + 로트 + 품목 + 구역을 한 번에 조회 */
    @Query("""
            select i
            from Inventory i
            join fetch i.lot l
            join fetch l.product p
            join fetch i.bin b
            where i.inventoryId = :inventoryId
            """)
    Optional<Inventory> findWithDetailById(@Param("inventoryId") Long inventoryId);

    /** 유통기한이 지난 재고 건수 (폐기 대상) */
    @Query("""
            select count(i)
            from Inventory i
            where i.quantity > 0
              and i.lot.expirationDate < :today
            """)
    long countExpiredInventories(@Param("today") LocalDate today);

    /** 유통기한이 지난 재고 수량 합계 */
    @Query("""
            select sum(i.quantity)
            from Inventory i
            where i.quantity > 0
              and i.lot.expirationDate < :today
            """)
    Long sumExpiredQuantity(@Param("today") LocalDate today);

    /** 특정 로트의 구역별 재고 (바코드 스캔 결과 표시용) */
    @Query("""
            select i
            from Inventory i
            join fetch i.lot l
            join fetch l.product p
            join fetch i.bin b
            where l.lotId = :lotId
              and i.quantity > 0
            order by b.binCode asc
            """)
    List<Inventory> findByLotIdWithBin(@Param("lotId") Long lotId);

    /**
     * FEFO(First Expired First Out) 출고 후보 재고 조회.
     * <p>
     * <b>유통기한이 가장 적게 남은 로트가 먼저 나오도록 정렬</b>하며,
     * 이미 유통기한이 지난 로트와 사용 중지된 구역은 출고 대상에서 제외한다.
     *
     * @param productId 출고할 품목
     * @param today     기준일 (이 날짜 이전에 만료된 로트는 제외)
     */
    @Query("""
            select i
            from Inventory i
            join fetch i.lot l
            join fetch l.product p
            join fetch i.bin b
            where p.productId = :productId
              and i.quantity > 0
              and l.expirationDate >= :today
              and b.active = true
            order by l.expirationDate asc, b.binCode asc
            """)
    List<Inventory> findAllocatableByProductId(@Param("productId") Long productId,
                                               @Param("today") LocalDate today);

    /**
     * 재고 현황 목록.
     * 화면에서 품목명 / 로트번호 / 구역코드를 함께 보여주므로 fetch join 으로 N+1 을 방지한다.
     *
     * @param productId 품목 (null 이면 전체)
     * @param binId     구역 (null 이면 전체)
     * @param zone      구역 그룹 (null 이면 전체)
     */
    @Query("""
            select i
            from Inventory i
            join fetch i.lot l
            join fetch l.product p
            join fetch i.bin b
            where (:productId is null or p.productId = :productId)
              and (:binId is null or b.binId = :binId)
              and (:zone is null or b.zone = :zone)
              and i.quantity > 0
            order by l.expirationDate asc, b.binCode asc
            """)
    List<Inventory> search(@Param("productId") Long productId,
                           @Param("binId") Long binId,
                           @Param("zone") String zone);

    /** 재고가 남아있는 행 수 */
    @Query("select count(i) from Inventory i where i.quantity > 0")
    long countWithStock();

    /** 전체 보관 수량 */
    @Query("select sum(i.quantity) from Inventory i")
    Long sumAllQuantity();
}
