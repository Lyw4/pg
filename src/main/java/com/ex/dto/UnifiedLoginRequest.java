package com.ex.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UnifiedLoginRequest(
        @NotBlank @Size(max = 120) String identifier,
        @NotBlank @Size(max = 100) String password) {
}
