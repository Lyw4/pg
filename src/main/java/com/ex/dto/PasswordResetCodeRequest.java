package com.ex.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PasswordResetCodeRequest(
        @NotBlank
        @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_]{4,19}$")
        String username,
        @NotBlank @Email @Size(max = 120)
        String email,
        @NotBlank @Size(max = 20)
        String phone) {
}
