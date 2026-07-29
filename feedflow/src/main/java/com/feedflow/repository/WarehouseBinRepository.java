package com.feedflow.repository;

import com.feedflow.admin.dto.WarehouseMapRow;
import com.feedflow.domain.Warehouse;
import com.feedflow.domain.WarehouseBin;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WarehouseBinRepository extends JpaRepository<WarehouseBin, Long> {

    /** 등록 시 중복 검사 */
    boolean existsByBinCode(String binCode);

    /** 수정 시 중복 검사 (자기 자신은 제외) */
    boolean existsByBinCodeAndBinIdNot(String binCode, Long binId);

    /**
     * 구역 목록 검색.
     *
     * @param warehouse 창고 (null 이면 전체)
     * @param zone      구역 (null 이면 전체)
     * @param active    사용 여부 (null 이면 전체)
     */
    @Query("""
            select b
            from WarehouseBin b
            where (:warehouse is null or b.warehouse = :warehouse)
              and (:zone is null or b.zone = :zone)
              and (:active is null or b.active = :active)
            order by b.warehouse asc, b.binCode asc
            """)
    List<WarehouseBin> search(@Param("warehouse") Warehouse warehouse,
                              @Param("zone") String zone,
                              @Param("active") Boolean active);

    /** 검색 필터용 구역(Zone) 목록 */
    @Query("select distinct b.zone from WarehouseBin b order by b.zone asc")
    List<String> findDistinctZones();

    /** 입고 등 업무 화면의 선택 목록용 (사용 중인 구역만) */
    List<WarehouseBin> findByActiveTrueOrderByBinCodeAsc();

    /** 사용 중인 구역 수 */
    long countByActive(boolean active);

    /* ------------------------------------------------------------------
     * 창고 2D 도면 집계
     * ------------------------------------------------------------------ */

    /**
     * 창고 한 동의 구역별 적재 현황 집계 (2D 도면용).
     * <p>
     * 구역마다 재고 합계 쿼리를 따로 날리면 N+1 이 되므로 {@code left join} + {@code group by} 로
     * DB 단에서 한 번에 집계한다.
     * <p>
     * <b>주의</b> — 조인 조건을 {@code on} 절에 두는 것이 핵심이다.
     * {@code where i.quantity > 0} 으로 쓰면 재고가 없는 빈 구역이 결과에서 사라져
     * 도면에 구역이 아예 표시되지 않는다. 빈 구역도 도면에는 그려져야 하므로
     * 수량 조건을 {@code on} 절에 붙여 outer join 을 유지한다.
     * <p>
     * {@code i.lot} / {@code l.product} 도 명시적 {@code left join} 으로 연결한다.
     * 경로 표현식({@code i.lot.lotId})을 쓰면 Hibernate 가 inner join 을 만들어
     * 같은 이유로 빈 구역이 탈락한다.
     *
     * @param warehouse 조회할 창고 (null 이면 전체 창고)
     */
    @Query("""
            select new com.feedflow.admin.dto.WarehouseMapRow(
                       b.binId,
                       b.binCode,
                       b.warehouse,
                       b.zone,
                       b.binPurpose,
                       b.rack,
                       b.binLevel,
                       b.maxCapacity,
                       b.active,
                       b.posX,
                       b.posY,
                       b.posWidth,
                       b.posHeight,
                       coalesce(sum(i.quantity), 0L),
                       count(distinct l.lotId),
                       count(distinct p.productId),
                       min(l.expirationDate))
            from WarehouseBin b
                left join Inventory i on i.bin = b and i.quantity > 0
                left join i.lot l
                left join l.product p
            where (:warehouse is null or b.warehouse = :warehouse)
            group by b.binId, b.binCode, b.warehouse, b.zone, b.binPurpose,
                     b.rack, b.binLevel, b.maxCapacity, b.active,
                     b.posX, b.posY, b.posWidth, b.posHeight
            order by b.posY asc, b.posX asc
            """)
    List<WarehouseMapRow> findWarehouseMapRows(@Param("warehouse") Warehouse warehouse);

    /**
     * 구역 1건의 적재 현황 집계 (모달 상세용).
     * 집계 규칙은 {@link #findWarehouseMapRows(Warehouse)} 과 동일하다.
     */
    @Query("""
            select new com.feedflow.admin.dto.WarehouseMapRow(
                       b.binId,
                       b.binCode,
                       b.warehouse,
                       b.zone,
                       b.binPurpose,
                       b.rack,
                       b.binLevel,
                       b.maxCapacity,
                       b.active,
                       b.posX,
                       b.posY,
                       b.posWidth,
                       b.posHeight,
                       coalesce(sum(i.quantity), 0L),
                       count(distinct l.lotId),
                       count(distinct p.productId),
                       min(l.expirationDate))
            from WarehouseBin b
                left join Inventory i on i.bin = b and i.quantity > 0
                left join i.lot l
                left join l.product p
            where b.binId = :binId
            group by b.binId, b.binCode, b.warehouse, b.zone, b.binPurpose,
                     b.rack, b.binLevel, b.maxCapacity, b.active,
                     b.posX, b.posY, b.posWidth, b.posHeight
            """)
    Optional<WarehouseMapRow> findWarehouseMapRowByBinId(@Param("binId") Long binId);
}
