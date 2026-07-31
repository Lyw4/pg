package com.feedflow.repository;

import com.feedflow.admin.dto.CenterAlertRow;
import com.feedflow.admin.dto.CenterAnimalMixRow;
import com.feedflow.admin.dto.CenterStockRow;
import com.feedflow.domain.Inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
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

    /**
     * 폐기 · 구역 이동 처리를 위해 재고 + 로트 + 품목 + 구역 + 센터를 한 번에 조회.
     * <p>
     * 처리 결과 화면이 위치 라벨(센터명 포함)을 표시하므로 센터까지 함께 읽는다.
     */
    @Query("""
            select i
            from Inventory i
            join fetch i.lot l
            join fetch l.product p
            join fetch i.bin b
            join fetch b.center c
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

    /**
     * 특정 로트의 구역별 재고 (바코드 스캔 결과 · 이력 추적의 현재 보관 위치).
     * <p>
     * 결과를 담는 DTO 가 위치 라벨에 센터명을 쓰므로 {@code join fetch b.center} 가 필요하다.
     * 없으면 보관 구역 수만큼 센터 조회 쿼리가 추가로 나간다.
     * <p>
     * 정렬도 <b>센터 → 구역</b> 순이다. 구역 코드만으로 정렬하면 제2창고의 {@code COLD-01} 이
     * 제1창고의 {@code C-02} 와 {@code D-01} 사이에 끼어 센터가 뒤섞인다.
     */
    @Query("""
            select i
            from Inventory i
            join fetch i.lot l
            join fetch l.product p
            join fetch i.bin b
            join fetch b.center c
            where l.lotId = :lotId
              and i.quantity > 0
            order by c.centerCode asc, b.binCode asc
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
     * 화면에서 품목명 / 로트번호 / 구역코드 / 센터명을 함께 보여주므로
     * fetch join 으로 N+1 을 방지한다.
     *
     * <h3>정렬</h3>
     * <b>유통기한 순이 1순위다.</b> 이 화면은 FEFO 출고 순서를 눈으로 확인하는 용도이므로
     * 센터를 먼저 정렬하면 "가장 급한 재고" 가 화면 아래로 밀린다.
     * 유통기한이 같을 때만 센터 → 구역 순으로 묶어 같은 센터 행이 흩어지지 않게 한다.
     *
     * @param centerId  물류센터 (null 이면 전국 전체)
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
            join fetch b.center c
            where (:centerId is null or c.centerId = :centerId)
              and (:productId is null or p.productId = :productId)
              and (:binId is null or b.binId = :binId)
              and (:zone is null or b.zone = :zone)
              and i.quantity > 0
            order by l.expirationDate asc, c.centerCode asc, b.binCode asc
            """)
    List<Inventory> search(@Param("centerId") Long centerId,
                           @Param("productId") Long productId,
                           @Param("binId") Long binId,
                           @Param("zone") String zone);

    /**
     * 센터별 보관 수량 집계 (재고 현황 화면의 센터 분포용).
     * <p>
     * 센터를 선택하지 않았을 때 "전국 재고가 어느 센터에 얼마나 있는지" 를 한 줄로 보여준다.
     * 목록을 자바에서 그룹핑해도 되지만, 그러면 <b>필터가 걸린 목록만</b> 집계되어
     * 센터를 하나 고른 순간 분포가 그 센터 하나로 줄어든다. 분포는 필터와 무관해야
     * "다른 센터에도 재고가 있다" 는 사실을 알 수 있으므로 별도 집계 쿼리로 둔다.
     *
     * @param productId 품목 (null 이면 전체 품목)
     */
    @Query("""
            select new com.feedflow.admin.dto.CenterStockRow(
                       c.centerId, c.name, sum(i.quantity), count(i))
            from Inventory i
                join i.bin b
                join b.center c
                join i.lot l
                join l.product p
            where (:productId is null or p.productId = :productId)
              and i.quantity > 0
            group by c.centerId, c.name, c.centerCode
            order by c.centerCode asc
            """)
    List<CenterStockRow> findStockByCenter(@Param("productId") Long productId);

    /**
     * 센터별 <b>보관 구역</b> 재고 합계 (적재율 계산 전용).
     * <p>
     * {@link #findStockByCenter(Long)} 와 나누어 둔 이유 — 두 값의 쓰임이 다르다.
     * <ul>
     *     <li>재고 <b>분포</b>는 모든 구역을 센다. 입고 대기 구역의 물건도 그 센터에
     *         실물로 있고, 운송 중 재고도 출발 센터 소속으로 잡아야 전국 합계가 맞는다.</li>
     *     <li>재고 <b>적재율</b>은 보관 구역만 센다. 분모인
     *         {@code findStorageCapacityByCenter()} 가 보관 구역 수용량이기 때문이다.</li>
     * </ul>
     * 분자와 분모의 기준이 다르면 적재율이 부풀려지고 100% 를 넘길 수도 있다.
     * 입고 등록으로 대기 구역에 물건이 들어온 순간 같은 센터가 2D 도면에서는 39%,
     * 대시보드에서는 84% 로 보이게 된다. 도면은 대기분을 따로 표시하기 때문이다.
     */
    @Query("""
            select new com.feedflow.admin.dto.CenterStockRow(
                       c.centerId, c.name, sum(i.quantity), count(i))
            from Inventory i
                join i.bin b
                join b.center c
            where i.quantity > 0
              and b.active = true
              and b.binPurpose = com.feedflow.domain.BinPurpose.STORAGE
            group by c.centerId, c.name, c.centerCode
            order by c.centerCode asc
            """)
    List<CenterStockRow> findStorageStockByCenter();

    /**
     * 센터별 유통기한 경보 집계 (전국 대시보드용).
     * <p>
     * 어느 센터에 급한 재고가 몰려 있는지 한 줄로 보여준다.
     * 센터가 여러 곳이면 "전국에 임박 재고 12건" 만으로는 어디를 먼저 손봐야 할지 알 수 없다.
     * <p>
     * <b>운송 중 가상 구역은 제외한다.</b> 트럭 위 재고를 "이 센터에서 처리해야 할 임박 재고"
     * 로 세면 담당자가 찾을 수 없는 일감이 생긴다.
     *
     * @param today         기준일
     * @param expiringUntil 이 날짜까지 만료되는 재고를 임박으로 본다 (경과분 포함)
     */
    @Query("""
            select new com.feedflow.admin.dto.CenterAlertRow(
                       c.centerId,
                       c.name,
                       count(i),
                       sum(case when l.expirationDate < :today then 1L else 0L end),
                       sum(i.quantity))
            from Inventory i
                join i.bin b
                join b.center c
                join i.lot l
            where i.quantity > 0
              and b.binPurpose <> com.feedflow.domain.BinPurpose.IN_TRANSIT
              and l.expirationDate <= :expiringUntil
            group by c.centerId, c.name, c.centerCode
            order by c.centerCode asc
            """)
    List<CenterAlertRow> findExpiringByCenter(@Param("today") LocalDate today,
                                             @Param("expiringUntil") LocalDate expiringUntil);

    /**
     * 센터별 · 축종별 보관 수량 집계.
     * <p>
     * 센터의 운영 방향이 실제 재고로 지켜지는지 확인하는 근거다.
     * "나주 센터는 닭 · 오리 최우선" 이라고 적어두는 것만으로는 그 방향이 지켜지는지 알 수 없다.
     */
    @Query("""
            select new com.feedflow.admin.dto.CenterAnimalMixRow(
                       c.centerId, p.animalType, sum(i.quantity))
            from Inventory i
                join i.bin b
                join b.center c
                join i.lot l
                join l.product p
            where i.quantity > 0
              and b.binPurpose <> com.feedflow.domain.BinPurpose.IN_TRANSIT
            group by c.centerId, p.animalType
            order by c.centerId asc, p.animalType asc
            """)
    List<CenterAnimalMixRow> findAnimalMixByCenter();

    /**
     * 폐기 대상 재고 조회.
     * <p>
     * 이전에는 전체 재고를 읽어와 자바에서 만료 여부를 걸러냈다.
     * 폐기 화면은 보통 만료 재고만 보므로 <b>필터를 DB 로 내려</b>
     * 필요 없는 행을 애초에 가져오지 않게 한다.
     *
     * <p>
     * 결과 DTO 가 위치 라벨에 센터명을 쓰므로 {@code join fetch b.center} 가 필요하다.
     * (센터 필터 조건은 아직 없다. 폐기 화면은 만료 재고를 전국 단위로 훑는 것이 기본
     *  동작이라 센터로 좁히는 요구가 나오면 그때 {@code :centerId} 를 추가한다)
     *
     * @param productId     품목 (null 이면 전체)
     * @param zone          구역 그룹 (null 이면 전체)
     * @param expiredBefore 이 날짜보다 유통기한이 이전인 재고만 (null 이면 만료 여부 무시)
     */
    @Query("""
            select i
            from Inventory i
            join fetch i.lot l
            join fetch l.product p
            join fetch i.bin b
            join fetch b.center c
            where (:productId is null or p.productId = :productId)
              and (:zone is null or b.zone = :zone)
              and (:expiredBefore is null or l.expirationDate < :expiredBefore)
              and i.quantity > 0
            order by l.expirationDate asc, c.centerCode asc, b.binCode asc
            """)
    List<Inventory> findDisposalTargets(@Param("productId") Long productId,
                                       @Param("zone") String zone,
                                       @Param("expiredBefore") LocalDate expiredBefore);

    /**
     * 여러 품목의 FEFO 출고 후보 재고를 한 번에 조회.
     * <p>
     * 주문 미리보기는 주문 항목마다 후보 재고를 조회하므로
     * 항목 수만큼 쿼리가 반복(N+1)됐다. 품목 목록을 {@code in} 조건으로 묶어 1회로 줄인다.
     * 정렬 규칙은 {@link #findAllocatableByProductId} 와 동일하다.
     */
    @Query("""
            select i
            from Inventory i
            join fetch i.lot l
            join fetch l.product p
            join fetch i.bin b
            where p.productId in :productIds
              and i.quantity > 0
              and l.expirationDate >= :today
              and b.active = true
            order by l.expirationDate asc, b.binCode asc
            """)
    List<Inventory> findAllocatableByProductIds(@Param("productIds") Collection<Long> productIds,
                                                @Param("today") LocalDate today);

    /** 재고가 남아있는 행 수 */
    @Query("select count(i) from Inventory i where i.quantity > 0")
    long countWithStock();

    /** 전체 보관 수량 */
    @Query("select sum(i.quantity) from Inventory i")
    Long sumAllQuantity();
}
