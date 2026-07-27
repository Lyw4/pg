package com.feedflow.repository;

import com.feedflow.domain.ProductLot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ProductLotRepository extends JpaRepository<ProductLot, Long> {

    /* ------------------------------------------------------------------
     * 입고 - 로트 조회 / 로트번호 생성
     * ------------------------------------------------------------------ */

    /**
     * 같은 품목에 동일한 로트번호가 이미 있는지 조회.
     * 존재하면 새 로트를 만들지 않고 기존 로트에 수량을 합산한다.
     */
    Optional<ProductLot> findByProduct_ProductIdAndLotNo(Long productId, String lotNo);

    /** 로트번호 자동 생성용 : 같은 품목 + 같은 제조일자의 로트 개수 */
    long countByProduct_ProductIdAndManufacturedDate(Long productId, LocalDate manufacturedDate);

    /**
     * 로트번호로 조회 (바코드 스캔용).
     * 로트번호는 품목 단위로 유일하므로 서로 다른 품목에 같은 번호가 있을 수 있어
     * 목록으로 반환하고 유통기한이 임박한 것을 우선한다.
     */
    @Query("""
            select l
            from ProductLot l
            join fetch l.product p
            where upper(l.lotNo) = :lotNo
            order by l.expirationDate asc
            """)
    List<ProductLot> findAllByLotNo(@Param("lotNo") String lotNo);

    /** 전체 로트 (라벨 출력용) - 품목코드 → 유통기한 순 */
    @Query("""
            select l
            from ProductLot l
            join fetch l.product p
            order by p.productCode asc, l.expirationDate asc
            """)
    List<ProductLot> findAllWithProduct();

    /** 특정 품목의 로트 목록 (유통기한 임박 순) */
    @Query("""
            select l
            from ProductLot l
            join fetch l.product p
            where p.productId = :productId
            order by l.expirationDate asc
            """)
    List<ProductLot> findByProductId(@Param("productId") Long productId);

    /* ------------------------------------------------------------------
     * 대시보드 - 유통기한 임박 알림
     * ------------------------------------------------------------------ */

    /**
     * 유통기한 임박 로트 조회.
     * 상품명을 함께 보여줘야 하므로 fetch join 으로 N+1 을 방지한다.
     *
     * @param from 조회 시작일 (오늘)
     * @param to   조회 종료일 (오늘 + 알림 기준일)
     */
    @Query("""
            select l
            from ProductLot l
            join fetch l.product p
            where l.expirationDate between :from and :to
              and l.lotQuantity > 0
            order by l.expirationDate asc
            """)
    List<ProductLot> findExpiringLots(@Param("from") LocalDate from,
                                     @Param("to") LocalDate to);

    /** 유통기한 임박 로트 건수 */
    @Query("""
            select count(l)
            from ProductLot l
            where l.expirationDate between :from and :to
              and l.lotQuantity > 0
            """)
    long countExpiringLots(@Param("from") LocalDate from,
                           @Param("to") LocalDate to);
}
