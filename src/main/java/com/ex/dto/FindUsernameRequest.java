package com.ex.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 현재 통합 회원 계정은 별도 아이디 대신 이메일을 로그인 ID로 사용한다.
 */
public record FindUsernameRequest(
        @NotBlank(message = "성명을 입력해 주세요.")
        @Size(max = 40)
        String name,
        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        @Size(max = 120)
        String email) {
}
