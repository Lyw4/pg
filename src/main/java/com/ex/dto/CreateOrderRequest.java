package com.ex.dto;

import com.ex.entity.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

public record CreateOrderRequest(
        @NotBlank @Size(max = 40) String customerName,
        @NotBlank @Pattern(regexp = "^[0-9-]{10,13}$") String phone,
        @NotBlank @Size(max = 200) String address,
        @Size(max = 200) String detailAddress,
        @Size(max = 200) String unloadingLocation,
        @Size(max = 300) String deliveryRequest,
        @NotNull PaymentMethod paymentMethod,
        boolean regularDelivery,
        @NotEmpty List<@Valid OrderLineRequest> items
) {
    public record OrderLineRequest(
            @NotNull @Positive Long productId,
            @Positive int quantity
    ) {
    }
}
