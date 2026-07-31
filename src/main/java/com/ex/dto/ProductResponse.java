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
                .filter(lot -> lot.getLotQuantity() > 0)
                .min(Comparator.comparing(ProductLot::getExpirationDate))
                .orElse(null);

        int stock = product.getLots().stream()
                .mapToInt(ProductLot::getLotQuantity)
                .sum();
        String animalCode = animalTypeCode(product);
        String animalLabel = animalLabel(product, animalCode);
        String stage = hasText(product.getFeedStage())
                ? product.getFeedStage()
                : "일반 배합";

        return new ProductResponse(
                product.getProductId(),
                product.getName(),
                animalLabel,
                animalCode,
                stage,
                product.getDescription(),
                product.getWeightKg(),
                product.getPrice().intValue(),
                product.getOriginalPrice(),
                valueOrZero(product.getProteinPercent()),
                valueOrZero(product.getFatPercent()),
                valueOrZero(product.getFiberPercent()),
                valueOrZero(product.getCalciumPercent()),
                firstAvailableLot == null ? null : firstAvailableLot.getLotNo(),
                firstAvailableLot == null ? null : firstAvailableLot.getManufacturedDate(),
                firstAvailableLot == null ? null : firstAvailableLot.getExpirationDate(),
                stock,
                hasText(product.getDisplayTone())
                        ? product.getDisplayTone()
                        : "green",
                product.getBadge(),
                hasText(product.getDisplayShape())
                        ? product.getDisplayShape()
                        : "FF",
                product.getImageUrl(),
                product.getManufacturer().getCompanyName()
        );
    }

    public static String animalTypeCode(Product product) {
        String category = product.getAnimalType();
        String searchable = (product.getName() + " "
                + product.getDescription()).toLowerCase();
        if ("소".equals(category)) {
            return searchable.contains("젖소")
                    || searchable.contains("낙농")
                    ? "DAIRY_CATTLE"
                    : "CATTLE";
        }
        if ("돼지".equals(category)) {
            return "PIG";
        }
        if ("조류(닭/오리)".equals(category)) {
            return searchable.contains("오리") ? "DUCK" : "CHICKEN";
        }
        if ("영양제".equals(category)) {
            return "SUPPLEMENT";
        }
        if ("반려동물".equals(category)) {
            return "PET";
        }
        return "CATTLE";
    }

    private static String animalLabel(
            Product product,
            String animalCode) {
        return switch (animalCode) {
            case "DAIRY_CATTLE" -> "젖소";
            case "PIG" -> "돼지";
            case "CHICKEN" -> "닭";
            case "DUCK" -> "오리";
            case "SUPPLEMENT" -> "영양제";
            case "PET" -> "반려동물";
            default -> hasText(product.getAnimalType())
                    ? product.getAnimalType()
                    : "소";
        };
    }

    private static BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
