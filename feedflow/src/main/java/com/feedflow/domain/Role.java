package com.feedflow.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 사용자 권한.
 * - USER  : B2C 쇼핑몰 고객(농가). 관리자 시스템 접근 불가
 * - STAFF : 일반 사원. 재고/출고 등 실무 화면만 접근
 * - ADMIN : 책임자. 매출 통계 및 사원 권한 관리까지 접근
 */
@Getter
@RequiredArgsConstructor
public enum Role {

    USER("고객", "bg-secondary"),
    STAFF("사원", "bg-primary"),
    ADMIN("책임자", "bg-danger");

    private final String description;

    /** Bootstrap 5 뱃지 클래스 */
    private final String badgeClass;

    /** Spring Security 에서 사용하는 권한 문자열 (ROLE_ prefix) */
    public String authority() {
        return "ROLE_" + name();
    }

    /** 사원(관리자 시스템 접근 가능) 여부 */
    public boolean isEmployee() {
        return this == STAFF || this == ADMIN;
    }
}
