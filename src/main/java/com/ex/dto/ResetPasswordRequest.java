package com.ex.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @Pattern(
                regexp = "^[A-Za-z][A-Za-z0-9_]{4,19}$",
                message = "아이디 형식이 올바르지 않습니다.")
        String username,
        @NotBlank @Email @Size(max = 120) String email,
        @Size(max = 40) String name,
        @NotBlank @Size(max = 20) String phone,
        @NotBlank
        @Pattern(regexp = "^\\d{6}$", message = "인증번호 6자리를 입력해주세요.")
        String code,
        @NotBlank
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z\\d]).{8,64}$",
                message = "비밀번호는 영문, 숫자, 특수문자를 포함한 8~64자여야 합니다.")
        String newPassword) {

    public ResetPasswordRequest(
            String email,
            String name,
            String phone,
            String newPassword) {
        this(null, email, name, phone, null, newPassword);
    }
}
