package com.ex.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PasswordResetCodeResponse(
        String message,
        long expiresInSeconds,
        long resendAvailableInSeconds,
        String debugCode) {
}
