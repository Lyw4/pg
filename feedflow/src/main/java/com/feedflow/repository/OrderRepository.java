package com.feedflow.repository;

import com.feedflow.admin.dto.DailySalesRow;
import com.feedflow.domain.Order;
import com.feedflow.domain.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    /** 특정 상태의 주문 건수 (예: 출고 대기) */
    long countByStatus(OrderStatus status);

    /** 특정 기간 + 특정 상태의 주문 건수 (예: 오늘 들어온 신규 주문) */
    long countByStatusAndCreatedAtBetween(OrderStatus status,
                                         LocalDateTime start,
                                         LocalDateTime end);

    /** 특정 기간의 전체 주문 건수 */
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    /**
     * 기간 매출 합계. 취소 주문은 제외한다.
     * 자바단 합산이 아니라 DB SUM 집계를 사용한다.
     * 데이터가 없으면 null 이 반환되므로 Service 에서 0 처리한다.
     */
    @Query("""
            select sum(o.finalPrice)
            from Order o
            where o.status <> :excludedStatus
              and o.createdAt between :start and :end
            """)
    Long sumSalesBetween(@Param("excludedStatus") OrderStatus excludedStatus,
                         @Param("start") LocalDateTime start,
                         @Param("end") LocalDateTime end);

    /**
     * 일별 매출 추이 (Chart.js 용).
     * year / month / day 로 GROUP BY 하여 DB 에서 집계한다.
     */
    @Query("""
            select new com.feedflow.admin.dto.DailySalesRow(
                       year(o.createdAt), month(o.createdAt), day(o.createdAt), sum(o.finalPrice))
            from Order o
            where o.status <> :excludedStatus
              and o.createdAt >= :start
            group by year(o.createdAt), month(o.createdAt), day(o.createdAt)
            order by year(o.createdAt), month(o.createdAt), day(o.createdAt)
            """)
    List<DailySalesRow> findDailySales(@Param("excludedStatus") OrderStatus excludedStatus,
                                      @Param("start") LocalDateTime start);
}
