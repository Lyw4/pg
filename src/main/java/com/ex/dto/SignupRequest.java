package com.ex.dto;

import com.ex.entity.AddressType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record SignupRequest(
        @NotBlank @Email @Size(max = 120) String email,
        @NotBlank
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[^A-Za-z\\d]).{8,64}$",
                message = "비밀번호는 8자 이상이며 영문, 숫자, 특수문자를 포함해야 합니다."
        )
        String password,
        @NotBlank @Size(max = 40) String name,
        @NotBlank @Size(max = 100) String farmName,
        @NotBlank @Pattern(regexp = "^[0-9-]{10,13}$") String phone,
        @Size(max = 20) String businessNumber,
        @Min(1) @Max(28) Integer regularDeliveryDay,
        @NotNull @Valid AddressRequest homeAddress,
        @NotNull @Valid AddressRequest farmAddress
) {
    public record AddressRequest(
            @NotNull AddressType addressType,
            @NotBlank @Size(max = 40) String recipientName,
            @NotBlank @Pattern(regexp = "^[0-9-]{10,13}$") String phone,
            @Size(max = 10) String postalCode,
            @NotBlank @Size(max = 200) String baseAddress,
            @Size(max = 200) String detailAddress,
            @Size(max = 200) String unloadingLocation,
            boolean defaultAddress
    ) {
    }
}
