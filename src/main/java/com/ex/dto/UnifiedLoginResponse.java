package com.ex.dto;

public record UnifiedLoginResponse(
        String accountType,
        String redirectUrl,
        MemberResponse member) {

    public static UnifiedLoginResponse admin() {
        return operator("ADMIN");
    }

    public static UnifiedLoginResponse operator(String accountType) {
        return new UnifiedLoginResponse(accountType, "/", null);
    }

    public static UnifiedLoginResponse customer(MemberResponse member) {
        return new UnifiedLoginResponse(
                "CUSTOMER",
                null,
                member);
    }
}
