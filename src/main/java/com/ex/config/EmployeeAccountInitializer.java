package com.ex.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ex.entity.EmployeeAccount;
import com.ex.entity.EmployeeRole;
import com.ex.repository.EmployeeAccountRepository;

import lombok.RequiredArgsConstructor;

@Component
@Order(50)
@RequiredArgsConstructor
public class EmployeeAccountInitializer implements ApplicationRunner {

    private final EmployeeAccountRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${feedflow.admin.username:admin}")
    private String adminUsername;

    @Value("${feedflow.admin.password:1234}")
    private String adminPassword;

    @Value("${feedflow.admin.name:김책임}")
    private String adminName;

    @Value("${feedflow.staff.username:staff@feedflow.co.kr}")
    private String staffUsername;

    @Value("${feedflow.staff.password:staff123}")
    private String staffPassword;

    @Value("${feedflow.staff.name:김사원}")
    private String staffName;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        upsertSeededAccount(
                adminUsername,
                adminPassword,
                adminName,
                "010-1000-0001",
                EmployeeRole.ADMIN);
        upsertSeededAccount(
                staffUsername,
                staffPassword,
                staffName,
                "010-1000-0002",
                EmployeeRole.STAFF);
    }

    private void upsertSeededAccount(
            String username,
            String rawPassword,
            String name,
            String phone,
            EmployeeRole role) {
        if (username == null || username.isBlank()
                || rawPassword == null || rawPassword.isBlank()) {
            return;
        }
        String normalizedUsername = username.trim().toLowerCase();
        EmployeeAccount account = employeeRepository
                .findByUsernameIgnoreCase(normalizedUsername)
                .orElseGet(() -> EmployeeAccount.builder()
                        .username(normalizedUsername)
                        .password(passwordEncoder.encode(rawPassword))
                        .name(name)
                        .phone(phone)
                        .role(role)
                        .active(true)
                        .build());
        // 기존 계정은 운영자가 변경한 비밀번호와 프로필을 보존합니다.
        // 최초 실행 시 계정이 없을 때만 기본 계정을 생성합니다.
        if (account.getId() == null) {
            employeeRepository.save(account);
        }
    }
}
