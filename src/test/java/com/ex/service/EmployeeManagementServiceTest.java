package com.ex.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.ex.entity.EmployeeRole;
import com.ex.repository.EmployeeAccountRepository;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EmployeeManagementServiceTest {

    @Autowired
    private EmployeeManagementService employeeManagementService;

    @Autowired
    private EmployeeAccountRepository employeeRepository;

    @Test
    void administratorCanPromoteAnotherEmployeeButNotSelf() {
        var administrator = employeeRepository
                .findByUsernameIgnoreCase("admin")
                .orElseThrow();
        var staff = employeeRepository
                .findByUsernameIgnoreCase("staff@feedflow.co.kr")
                .orElseThrow();

        employeeManagementService.changeRole(
                staff.getId(),
                EmployeeRole.ADMIN,
                administrator.getUsername());

        assertEquals(EmployeeRole.ADMIN, staff.getRole());
        assertThrows(
                IllegalStateException.class,
                () -> employeeManagementService.changeRole(
                        administrator.getId(),
                        EmployeeRole.STAFF,
                        administrator.getUsername()));
    }
}
