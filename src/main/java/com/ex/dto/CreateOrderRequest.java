package com.ex.dto;

import com.ex.entity.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

public record CreateOrderRequest(
        @NotBlank @Size(max = 40) String customerName,
        @NotBlank @Pattern(regexp = "^[0-9-]{10,13}$") String phone,
        @Size(max = 10) String postalCode,
        @NotBlank @Size(max = 200) String address,
        @Size(max = 200) String detailAddress,
        @Size(max = 200) String unloadingLocation,
        @Size(max = 300) String deliveryRequest,
        @NotNull PaymentMethod paymentMethod,
        boolean regularDelivery,
        @NotEmpty List<@Valid OrderLineRequest> items
) {
    public CreateOrderRequest(
            String customerName,
            String phone,
            String address,
            String detailAddress,
            String unloadingLocation,
            String deliveryRequest,
            PaymentMethod paymentMethod,
            boolean regularDelivery,
            List<OrderLineRequest> items) {
        this(customerName, phone, null, address, detailAddress,
                unloadingLocation, deliveryRequest, paymentMethod,
                regularDelivery, items);
    }

    public record OrderLineRequest(
            @NotNull @Positive Long productId,
            @Positive int quantity
    ) {
    }
}
