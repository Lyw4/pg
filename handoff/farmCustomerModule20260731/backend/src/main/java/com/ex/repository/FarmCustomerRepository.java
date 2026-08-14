package com.ex.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.ex.entity.FarmCustomer;

public interface FarmCustomerRepository
        extends JpaRepository<FarmCustomer, Long> {

    Optional<FarmCustomer> findByFarmCode(String farmCode);

    @EntityGraph(attributePaths = "assignedWarehouse")
    List<FarmCustomer>
            findAllByOrderByAssignedWarehouseDisplayOrderAscFarmNameAsc();
}
