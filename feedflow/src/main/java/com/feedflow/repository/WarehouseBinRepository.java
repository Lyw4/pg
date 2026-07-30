package com.feedflow.repository;

import com.feedflow.admin.dto.InTransitStockRow;
import com.feedflow.admin.dto.WarehouseMapRow;
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
     * <p>
     * 목록이 센터명을 표시하므로 {@code join fetch center} 로 함께 읽는다.
     * 없으면 구역 수만큼 센터 조회 쿼리가 추가로 나간다(N+1).
     *
     * @param centerId 센터 (null 이면 전체)
     * @param zone     구역 (null 이면 전체)
     * @param active   사용 여부 (null 이면 전체)
     */
    @Query("""
            select b
            from WarehouseBin b
                join fetch b.center c
            where (:centerId is null or c.centerId = :centerId)
              and (:zone is null or b.zone = :zone)
              and (:active is null or b.active = :active)
            order by c.centerCode asc, b.binCode asc
            """)
    List<WarehouseBin> search(@Param("centerId") Long centerId,
                              @Param("zone") String zone,
                              @Param("active") Boolean active);

    /** 검색 필터용 구역(Zone) 목록 */
    @Query("select distinct b.zone from WarehouseBin b order by b.zone asc")
    List<String> findDistinctZones();

    /**
     * 사용 중인 구역 목록 - <b>센터 순 → 구역 코드 순</b> (입고 · 이동 등 선택 목록용).
     * <p>
     * 구역 코드만으로 정렬하면 센터가 뒤섞인다. 제2창고의 {@code COLD-01} 이
     * 알파벳 순서상 제1창고의 {@code C-02} 와 {@code D-01} 사이에 끼어들기 때문이다.
     * 선택 목록에서 센터가 섞이면 다른 센터의 구역을 잘못 고를 수 있다.
     * <p>
     * 옵션 라벨에 센터명이 들어가므로 {@code join fetch} 로 센터를 함께 읽는다.
     * <p>
     * <b>운송 중 가상 구역은 제외한다.</b> 실재하는 바닥이 아니므로 사용자가 이동
     * 도착지로 고를 수 있으면 안 된다. 재고를 그곳에 넣는 것은 이관 로직의 권한이다.
     */
    @Query("""
            select b
            from WarehouseBin b
                join fetch b.center c
            where b.active = true
              and b.binPurpose <> com.feedflow.domain.BinPurpose.IN_TRANSIT
            order by c.centerCode asc, b.binCode asc
            """)
    List<WarehouseBin> findActiveBinsForSelection();

    /**
     * 센터의 운송 중(IN_TRANSIT) 가상 구역.
     * <p>
     * 센터 간 이관에서 재고가 잠시 머무는 자리다. 센터당 하나만 존재한다.
     * 없으면 이관 시점에 자동으로 만든다 — 센터는 운영 중에 늘어나므로
     * 센터를 만들 때마다 사람이 가상 구역을 만들게 하면 빠뜨린다.
     */
    @Query("""
            select b
            from WarehouseBin b
                join fetch b.center c
            where c.centerId = :centerId
              and b.binPurpose = com.feedflow.domain.BinPurpose.IN_TRANSIT
            """)
    Optional<WarehouseBin> findInTransitBin(@Param("centerId") Long centerId);

    /**
     * 운송 중 구역에 남아 있는 재고 (정합성 점검용).
     * <p>
     * 이관은 한 트랜잭션에서 출발·도착을 모두 처리하므로 <b>평상시 잔량은 0</b> 이다.
     * 0 이 아니면 트랜잭션이 중간에 깨졌거나 도착 처리가 누락된 것이다.
     * 가상 구역을 관찰할 수 없으면 이런 이상을 알아챌 방법이 없다.
     */
    @Query("""
            select new com.feedflow.admin.dto.InTransitStockRow(
                       c.centerId, c.name, b.binCode, sum(i.quantity), count(i))
            from Inventory i
                join i.bin b
                join b.center c
            where b.binPurpose = com.feedflow.domain.BinPurpose.IN_TRANSIT
              and i.quantity > 0
            group by c.centerId, c.name, c.centerCode, b.binCode
            order by c.centerCode asc
            """)
    List<InTransitStockRow> findInTransitStock();

    /**
     * 구역 1건 + 센터 (센터명을 표시하는 단건 조회용).
     * <p>
     * {@code findById} 로 읽으면 센터가 지연 로딩이라 {@code locationLabel()} 호출 시
     * 쿼리가 한 번 더 나간다. 결과 화면이 센터명을 쓰는 경로에서는 이 메서드를 쓴다.
     */
    @Query("""
            select b
            from WarehouseBin b
                join fetch b.center c
            where b.binId = :binId
            """)
    Optional<WarehouseBin> findWithCenterById(@Param("binId") Long binId);

    /** 사용 중인 구역 수 */
    long countByActive(boolean active);

    /* ------------------------------------------------------------------
     * 센터 2D 도면 집계
     * ------------------------------------------------------------------ */

    /**
     * 센터 한 곳의 구역별 적재 현황 집계 (2D 도면용).
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
     * <p>
     * 도면은 센터 단위로 그리므로 <b>행마다 센터 정보를 담지 않는다.</b>
     * 어느 센터의 도면인지는 호출한 쪽이 이미 알고 있다.
     * <p>
     * <b>물리적 공간이 아닌 구역(운송 중)은 제외한다.</b> 좌표 컬럼이 {@code NOT NULL} 이라
     * 값은 들어가 있지만, 도면에 그리면 창고에 실재하지 않는 칸이 나타난다.
     *
     * @param centerId 조회할 센터 (null 이면 전체 센터)
     */
    @Query("""
            select new com.feedflow.admin.dto.WarehouseMapRow(
                       b.binId,
                       b.binCode,
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
            where (:centerId is null or b.center.centerId = :centerId)
              and b.binPurpose <> com.feedflow.domain.BinPurpose.IN_TRANSIT
            group by b.binId, b.binCode, b.zone, b.binPurpose,
                     b.rack, b.binLevel, b.maxCapacity, b.active,
                     b.posX, b.posY, b.posWidth, b.posHeight
            order by b.posY asc, b.posX asc
            """)
    List<WarehouseMapRow> findWarehouseMapRows(@Param("centerId") Long centerId);

    /**
     * 구역 1건의 적재 현황 집계 (모달 상세용).
     * 집계 규칙은 {@link #findWarehouseMapRows(Long)} 과 동일하다.
     */
    @Query("""
            select new com.feedflow.admin.dto.WarehouseMapRow(
                       b.binId,
                       b.binCode,
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
            group by b.binId, b.binCode, b.zone, b.binPurpose,
                     b.rack, b.binLevel, b.maxCapacity, b.active,
                     b.posX, b.posY, b.posWidth, b.posHeight
            """)
    Optional<WarehouseMapRow> findWarehouseMapRowByBinId(@Param("binId") Long binId);
}
