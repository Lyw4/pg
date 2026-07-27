package com.feedflow.repository;

import com.feedflow.domain.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /** 특정 로트의 구역별 보관 수량 합계 */
    @Query("select sum(i.quantity) from Inventory i where i.lot.lotId = :lotId")
    Long sumQuantityByLotId(@Param("lotId") Long lotId);

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
