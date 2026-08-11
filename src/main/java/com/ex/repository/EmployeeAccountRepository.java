package com.ex.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ex.entity.EmployeeAccount;
import com.ex.entity.EmployeeRole;

public interface EmployeeAccountRepository
        extends JpaRepository<EmployeeAccount, Long> {

    Optional<EmployeeAccount> findByUsernameIgnoreCase(String username);

    List<EmployeeAccount> findAllByOrderByRoleAscNameAsc();

    long countByRoleAndActiveTrue(EmployeeRole role);
}
