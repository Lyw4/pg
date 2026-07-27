package com.feedflow.repository;

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
}
