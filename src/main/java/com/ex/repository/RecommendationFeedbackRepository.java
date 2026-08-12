package com.ex.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.ex.entity.RecommendationFeedback;

public interface RecommendationFeedbackRepository
        extends JpaRepository<RecommendationFeedback, Long> {

    Optional<RecommendationFeedback>
            findByFarmCustomerFarmCustomerIdAndProductProductId(
                    Long farmCustomerId, Long productId);

    @EntityGraph(attributePaths = {"product", "farmCustomer"})
    List<RecommendationFeedback> findByFarmCustomerFarmCustomerId(
            Long farmCustomerId);
}
