package com.ex.dto;

public record PaymentConfigResponse(
        boolean portOneEnabled,
        String portOneCustomerCode,
        boolean cardEnabled,
        String cardChannelKey,
        boolean kakaoEnabled,
        String kakaoChannelKey,
        boolean virtualAccountEnabled,
        String virtualAccountChannelKey) {
}
