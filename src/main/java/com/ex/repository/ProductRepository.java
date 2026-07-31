package com.ex.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.ex.entity.Product;

public interface ProductRepository
        extends JpaRepository<Product, Long> {

    // 제조사 정보를 상품과 함께 조회
    @EntityGraph(attributePaths = "manufacturer")
    List<Product> findAllByOrderByNameAsc();

    // 더미 데이터 중복 생성 방지
    boolean existsByName(String name);

    Optional<Product> findByName(String name);

    // 상품 상세 화면용: 제조사까지 함께 조회
    @EntityGraph(attributePaths = "manufacturer")
    Optional<Product> findDetailByProductId(Long productId);

    @EntityGraph(attributePaths = {
        "manufacturer",
        "lots"
    })
    List<Product> findAllByActiveTrueOrderByProductIdAsc();

    default List<Product> findAllByActiveTrueOrderByIdAsc() {
        return findAllByActiveTrueOrderByProductIdAsc();
    }

    @EntityGraph(attributePaths = {
        "manufacturer",
        "lots"
    })
    Optional<Product> findByProductIdAndActiveTrue(Long productId);

    default Optional<Product> findByIdAndActiveTrue(Long productId) {
        return findByProductIdAndActiveTrue(productId);
    }
}
