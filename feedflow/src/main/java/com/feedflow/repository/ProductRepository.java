package com.feedflow.repository;

import com.feedflow.admin.dto.StockSyncRow;
import com.feedflow.domain.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /* ------------------------------------------------------------------
     * 기준 정보(Master Data) - 품목 코드 중복 검사
     * ------------------------------------------------------------------ */

    /** 등록 시 중복 검사 */
    boolean existsByProductCode(String productCode);

    /** 수정 시 중복 검사 (자기 자신은 제외) */
    boolean existsByProductCodeAndProductIdNot(String productCode, Long productId);

    Optional<Product> findByProductCode(String productCode);

    /* ------------------------------------------------------------------
     * 기준 정보(Master Data) - 목록 검색
     * ------------------------------------------------------------------ */

    /**
     * 품목 목록 검색.
     * 파라미터가 null 이면 해당 조건은 무시한다.
     *
     * @param keyword    품목 코드 또는 품목명 부분 일치 (null 이면 전체)
     * @param animalType 축종 (null 이면 전체)
     * @param active     사용 여부 (null 이면 전체)
     */
    @Query("""
            select p
            from Product p
            where (:keyword is null
                   or lower(p.productCode) like lower(concat('%', :keyword, '%'))
                   or lower(p.name) like lower(concat('%', :keyword, '%')))
              and (:animalType is null or p.animalType = :animalType)
              and (:active is null or p.active = :active)
            """)
    Page<Product> search(@Param("keyword") String keyword,
                         @Param("animalType") String animalType,
                         @Param("active") Boolean active,
                         Pageable pageable);

    /** 검색 필터용 축종 목록 */
    @Query("select distinct p.animalType from Product p order by p.animalType asc")
    List<String> findDistinctAnimalTypes();

    /** 입고 등 업무 화면의 선택 목록용 (사용 중인 품목만) */
    List<Product> findByActiveTrueOrderByProductCodeAsc();

    /* ------------------------------------------------------------------
     * 대시보드 - 안전재고 알림 (사용 중인 품목만 대상)
     * ------------------------------------------------------------------ */

    @Query("""
            select p
            from Product p
            where p.active = true
              and p.totalStock < p.safetyStock
            order by (p.safetyStock - p.totalStock) desc, p.name asc
            """)
    List<Product> findSafetyStockAlerts();

    /** 안전재고 미달 상품 건수 */
    @Query("""
            select count(p)
            from Product p
            where p.active = true
              and p.totalStock < p.safetyStock
            """)
    long countSafetyStockAlerts();

    /* ------------------------------------------------------------------
     * 재고 정합성 진단 (읽기 전용)
     * ------------------------------------------------------------------ */

    /**
     * 전체 품목의 장부 재고(totalStock)와 로트 수량 합계를 한 번에 조회한다.
     * <p>
     * 품목마다 합계 쿼리를 반복하면 N+1 이 되므로 {@code left join} + {@code group by} 로
     * DB 단에서 집계한다. 로트가 없는 품목도 결과에 포함되며 합계는 0 으로 내려온다.
     * <b>값을 변경하지 않는 진단 전용</b> 쿼리다.
     */
    @Query("""
            select new com.feedflow.admin.dto.StockSyncRow(
                       p.productId,
                       p.productCode,
                       p.name,
                       p.active,
                       p.totalStock,
                       coalesce(sum(l.lotQuantity), 0L))
            from Product p
                left join ProductLot l on l.product = p
            group by p.productId, p.productCode, p.name, p.active, p.totalStock
            order by p.productCode asc
            """)
    List<StockSyncRow> findStockSyncRows();
}
