package com.ex.dto;

import com.ex.entity.AddressType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record SignupRequest(
        @Pattern(
                regexp = "^[A-Za-z][A-Za-z0-9_]{4,19}$",
                message = "아이디는 영문으로 시작하는 5~20자의 영문, 숫자, 밑줄만 사용할 수 있습니다."
        )
        String username,
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
        @NotNull @Valid AddressRequest farmAddress,
        @Valid FarmProfileRequest farmProfile
) {
    public SignupRequest(
            String email,
            String password,
            String name,
            String farmName,
            String phone,
            String businessNumber,
            Integer regularDeliveryDay,
            AddressRequest homeAddress,
            AddressRequest farmAddress,
            FarmProfileRequest farmProfile) {
        this(null, email, password, name, farmName, phone,
                businessNumber, regularDeliveryDay,
                homeAddress, farmAddress, farmProfile);
    }

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

    public record FarmProfileRequest(
            @Size(max = 30) String animalType,
            @PositiveOrZero Integer livestockCount,
            @PositiveOrZero Integer monthlyFeedQuantity,
            @Size(max = 80) String preferredFeed,
            @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
            @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude
    ) {
    }
}
