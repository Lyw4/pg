package com.ex.dto;

import com.ex.entity.Product;
import com.ex.entity.ProductLot;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;

public record ProductResponse(
        Long id,
        String name,
        String animal,
        String animalType,
        String stage,
        String description,
        BigDecimal weight,
        int price,
        Integer originalPrice,
        BigDecimal protein,
        BigDecimal fat,
        BigDecimal fiber,
        BigDecimal calcium,
        String lot,
        LocalDate manufacturedDate,
        LocalDate expiry,
        int stock,
        String tone,
        String badge,
        String shape,
        String imageUrl,
        String manufacturer
) {
    public static ProductResponse from(Product product) {
        ProductLot firstAvailableLot = product.getLots().stream()
                .filter(lot -> lot.getQuantity() > 0)
                .min(Comparator.comparing(ProductLot::getExpirationDate))
                .orElse(null);

        int stock = product.getLots().stream()
                .mapToInt(ProductLot::getQuantity)
                .sum();

        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getAnimalType().getLabel(),
                product.getAnimalType().name(),
                product.getFeedStage(),
                product.getDescription(),
                product.getWeightKg(),
                product.getPrice(),
                product.getOriginalPrice(),
                product.getProteinPercent(),
                product.getFatPercent(),
                product.getFiberPercent(),
                product.getCalciumPercent(),
                firstAvailableLot == null ? null : firstAvailableLot.getLotNumber(),
                firstAvailableLot == null ? null : firstAvailableLot.getManufacturedDate(),
                firstAvailableLot == null ? null : firstAvailableLot.getExpirationDate(),
                stock,
                product.getDisplayTone(),
                product.getBadge(),
                product.getDisplayShape(),
                product.getImageUrl(),
                product.getManufacturer().getName()
        );
    }
}
