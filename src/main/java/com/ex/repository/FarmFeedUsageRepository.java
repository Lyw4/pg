package com.ex.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.ex.entity.FarmFeedUsage;

public interface FarmFeedUsageRepository extends JpaRepository<FarmFeedUsage, Long> {
    @EntityGraph(attributePaths = "farmCustomer")
    List<FarmFeedUsage> findByFarmCustomerFarmCustomerIdOrderByUsageMonthAsc(
            Long farmCustomerId);

    Optional<FarmFeedUsage> findByFarmCustomerFarmCustomerIdAndUsageMonth(
            Long farmCustomerId, LocalDate usageMonth);
}
