package com.ex.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ex.entity.EmployeeAccount;
import com.ex.entity.EmployeeRole;
import com.ex.repository.EmployeeAccountRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmployeeManagementService {

    private final EmployeeAccountRepository employeeRepository;

    public List<EmployeeView> employees(String loginUsername) {
        return employeeRepository.findAllByOrderByRoleAscNameAsc()
                .stream()
                .map(employee -> EmployeeView.from(
                        employee,
                        employee.getUsername().equalsIgnoreCase(
                                loginUsername)))
                .toList();
    }

    @Transactional
    public String changeRole(
            Long employeeId,
            EmployeeRole newRole,
            String loginUsername) {
        EmployeeAccount employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "존재하지 않는 사원입니다."));
        if (employee.getUsername().equalsIgnoreCase(loginUsername)) {
            throw new IllegalStateException(
                    "현재 로그인한 본인의 권한은 변경할 수 없습니다.");
        }
        if (employee.getRole() == newRole) {
            throw new IllegalStateException(
                    "이미 " + newRole.getLabel() + " 권한입니다.");
        }
        if (employee.getRole() == EmployeeRole.ADMIN
                && newRole == EmployeeRole.STAFF
                && employeeRepository.countByRoleAndActiveTrue(
                        EmployeeRole.ADMIN) <= 1) {
            throw new IllegalStateException(
                    "마지막 책임자 계정은 사원으로 변경할 수 없습니다.");
        }
        employee.changeRole(newRole);
        return employee.getName();
    }

    public record EmployeeView(
            Long id,
            String name,
            String username,
            String phone,
            EmployeeRole role,
            boolean active,
            LocalDateTime createdAt,
            boolean currentUser) {

        private static EmployeeView from(
                EmployeeAccount employee,
                boolean currentUser) {
            return new EmployeeView(
                    employee.getId(),
                    employee.getName(),
                    employee.getUsername(),
                    employee.getPhone(),
                    employee.getRole(),
                    employee.isActive(),
                    employee.getCreatedAt(),
                    currentUser);
        }
    }
}
