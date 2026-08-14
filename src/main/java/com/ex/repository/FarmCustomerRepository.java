package com.ex.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.ex.entity.FarmCustomer;

public interface FarmCustomerRepository
        extends JpaRepository<FarmCustomer, Long> {

    Optional<FarmCustomer> findByFarmCode(String farmCode);

    @EntityGraph(attributePaths = "assignedWarehouse")
    @Query("select f from FarmCustomer f where f.member.id = :memberId and f.status <> com.ex.entity.FarmCustomer$CustomerStatus.DELETED")
    Optional<FarmCustomer> findByMemberId(
            @Param("memberId") Long memberId);

    @EntityGraph(attributePaths = {"assignedWarehouse", "member"})
    @Query("select f from FarmCustomer f where f.status <> com.ex.entity.FarmCustomer$CustomerStatus.DELETED order by f.assignedWarehouse.displayOrder asc, f.farmName asc")
    List<FarmCustomer>
            findAllByOrderByAssignedWarehouseDisplayOrderAscFarmNameAsc();
}
