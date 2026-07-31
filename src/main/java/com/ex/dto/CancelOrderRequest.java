package com.ex.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CancelOrderRequest(
        @NotBlank
        @Pattern(regexp = "^[0-9-]{10,13}$")
        String phone
) {
}
