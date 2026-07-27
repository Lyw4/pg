package com.feedflow.repository;

import com.feedflow.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * 안전재고 알림 대상: 전체 재고가 안전 재고보다 적은 상품.
     * 부족 수량이 큰 순서로 정렬한다.
     */
    @Query("""
            select p
            from Product p
            where p.totalStock < p.safetyStock
            order by (p.safetyStock - p.totalStock) desc, p.name asc
            """)
    List<Product> findSafetyStockAlerts();

    /** 안전재고 미달 상품 건수 */
    @Query("select count(p) from Product p where p.totalStock < p.safetyStock")
    long countSafetyStockAlerts();
}
