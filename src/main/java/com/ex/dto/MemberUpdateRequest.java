package com.ex.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MemberUpdateRequest(
        @NotBlank @Size(max = 40) String name,
        @NotBlank @Size(max = 100) String farmName,
        @NotBlank @Pattern(regexp = "^[0-9-]{10,13}$") String phone,
        @Size(max = 20) String businessNumber,
        @Min(1) @Max(28) Integer regularDeliveryDay,
        @Size(max = 10) String homePostalCode,
        @NotBlank @Size(max = 200) String homeAddress,
        @Size(max = 200) String homeDetailAddress,
        @Size(max = 10) String farmPostalCode,
        @NotBlank @Size(max = 200) String farmAddress,
        @Size(max = 200) String farmDetailAddress,
        @Size(max = 200) String unloadingLocation) {
}
