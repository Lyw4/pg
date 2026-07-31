package com.ex.repository;

import com.ex.entity.ProductLot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;

public interface ProductLotRepository extends JpaRepository<ProductLot, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<ProductLot> findByProductIdAndQuantityGreaterThanOrderByExpirationDateAsc(Long productId, int quantity);
}
