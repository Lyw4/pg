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

    @Test
    void brokenSeededNamesAreShownAsKoreanNames() {
        var administrator = employeeRepository
                .findByUsernameIgnoreCase("admin")
                .orElseThrow();
        var staff = employeeRepository
                .findByUsernameIgnoreCase("staff@feedflow.co.kr")
                .orElseThrow();
        administrator.repairSeededProfile("ê¹€ì±…ìž„", administrator.getPhone());
        staff.repairSeededProfile("ê¹€ì‚¬ì›", staff.getPhone());

        var employees = employeeManagementService.employees("admin");

        assertEquals("김책임", employees.stream()
                .filter(employee -> employee.username().equals("admin"))
                .findFirst().orElseThrow().name());
        assertEquals("김사원", employees.stream()
                .filter(employee -> employee.username().equals("staff@feedflow.co.kr"))
                .findFirst().orElseThrow().name());
    }
}
