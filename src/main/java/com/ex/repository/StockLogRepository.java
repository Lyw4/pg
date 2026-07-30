package com.ex.repository;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.ex.entity.StockLog;
import com.ex.entity.StockLog.ChangeType;

public interface StockLogRepository extends JpaRepository<StockLog, Long> {

    // 재고 이력과 LOT, 상품 정보를 한 번에 조회
    @EntityGraph(attributePaths = {
        "lot",
        "lot.product"
    })
    List<StockLog> findTop30ByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {
        "lot",
        "lot.product"
    })
    List<StockLog> findTop100ByChangeTypeOrderByCreatedAtDesc(ChangeType changeType);

    @EntityGraph(attributePaths = {
        "lot",
        "lot.product"
    })
    java.util.Optional<StockLog> findByLogId(Long logId);

    @EntityGraph(attributePaths = {
        "lot",
        "lot.product"
    })
    List<StockLog> findByLotLotIdOrderByCreatedAtDesc(Long lotId);
}
