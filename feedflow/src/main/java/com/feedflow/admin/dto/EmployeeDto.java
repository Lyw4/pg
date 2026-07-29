package com.feedflow.admin.dto;

import com.feedflow.domain.Role;
import com.feedflow.domain.User;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 사원 목록 행.
 */
@Getter
@Builder
public class EmployeeDto {

    private final Long userId;
    private final String email;
    private final String name;
    private final String phone;
    private final Role role;
    private final LocalDateTime createdAt;

    /** 현재 로그인한 본인 계정 여부 (본인 권한은 변경 불가) */
    private final boolean self;

    public static EmployeeDto of(User user, Long loginUserId) {
        return EmployeeDto.builder()
                .userId(user.getUserId())
                .email(user.getEmail())
                .name(user.getName())
                .phone(user.getPhone())
                .role(user.getRole())
                .createdAt(user.getCreatedAt())
                .self(loginUserId != null && loginUserId.equals(user.getUserId()))
                .build();
    }

    /** Bootstrap 뱃지 클래스 (ADMIN: bg-danger / STAFF: bg-primary) */
    public String getRoleBadgeClass() {
        return role.getBadgeClass();
    }

    public String getRoleDescription() {
        return role.getDescription();
    }
}
