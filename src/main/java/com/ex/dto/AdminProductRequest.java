package com.ex.dto;

import com.ex.entity.AnimalType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AdminProductRequest(
        @NotBlank @Size(max = 100) String manufacturerName,
        @NotBlank @Size(max = 120) String name,
        @NotNull AnimalType animalType,
        @NotBlank @Size(max = 50) String feedStage,
        @NotBlank @Size(max = 500) String description,
        @NotNull @DecimalMin("1.0") @DecimalMax("1000.0") BigDecimal weightKg,
        @Positive int price,
        @PositiveOrZero Integer originalPrice,
        @NotNull @DecimalMin("0.0") BigDecimal proteinPercent,
        @NotNull @DecimalMin("0.0") BigDecimal fatPercent,
        @NotNull @DecimalMin("0.0") BigDecimal fiberPercent,
        @NotNull @DecimalMin("0.0") BigDecimal calciumPercent,
        @Size(max = 500) String imageUrl,
        @Size(max = 30) String badge,
        @NotBlank @Size(max = 30) String displayTone,
        @NotBlank @Size(max = 30) String displayShape,
        @NotBlank @Size(max = 50) String lotNumber,
        @NotNull @PastOrPresent LocalDate manufacturedDate,
        @NotNull @Future LocalDate expirationDate,
        @PositiveOrZero int lotQuantity
) {
}
