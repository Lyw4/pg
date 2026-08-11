package com.ex.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

import com.ex.entity.ProductLot;

public interface ProductLotRepository extends JpaRepository<ProductLot, Long> {

    // FIFO 출고용: 유통기한이 빠른 LOT부터 조회
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<ProductLot>
        findByProductProductIdAndLotQuantityGreaterThanOrderByExpirationDateAsc(
            Long productId,
            int quantity
        );

    // LOT와 상품, 제조사를 한 번에 조회
    @EntityGraph(attributePaths = {
        "product",
        "product.manufacturer"
    })
    List<ProductLot> findAllByOrderByExpirationDateAsc();

    // 상품 상세 화면용 LOT 목록
    @EntityGraph(attributePaths = "product")
    List<ProductLot> findByProductProductIdOrderByExpirationDateAsc(
        Long productId
    );

    boolean existsByLotNo(String lotNo);

    Optional<ProductLot> findByLotNo(String lotNo);

    @EntityGraph(attributePaths = {
        "product",
        "product.manufacturer"
    })
    Optional<ProductLot> findDetailByLotId(Long lotId);
}
