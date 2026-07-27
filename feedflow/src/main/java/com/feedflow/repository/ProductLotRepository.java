package com.feedflow.repository;

import com.feedflow.domain.ProductLot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ProductLotRepository extends JpaRepository<ProductLot, Long> {

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
