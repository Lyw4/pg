package com.ex.dto;

import jakarta.validation.constraints.NotBlank;

public record PortOnePaymentCompleteRequest(
        @NotBlank String impUid,
        @NotBlank String merchantUid,
        @NotBlank String paymentToken) {
}
