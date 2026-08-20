package com.ex.dto;

import com.ex.entity.Product;
import com.ex.entity.ProductLot;
import com.ex.service.ExpirySaleService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

public record ProductResponse(
        Long id,
        String productCode,
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
        String manufacturer,
        List<LotResponse> lots,
        boolean expirySale,
        int discountRate,
        int saleStock,
        long saleDaysRemaining,
        LocalDate saleExpirationDate,
        String saleLabel,
        // 관리자 화면이 판매 중지 상품을 구분해 표시하고 다시 판매로 되돌릴 수
        // 있도록 노출합니다. 고객 카탈로그는 판매 중인 상품만 담기므로 항상 true입니다.
        boolean active
) {
    public static ProductResponse from(Product product) {
        LocalDate today = LocalDate.now();
        List<ProductLot> availableLots = product.getLots().stream()
                .filter(lot -> lot.getLotQuantity() > 0)
                // 유통기한이 비어 있으면 판매 가능 기간을 계산할 수 없어
                // 제외합니다. 빼면 아래 날짜 계산에서 NPE가 납니다.
                .filter(lot -> lot.getExpirationDate() != null)
                .filter(lot -> ChronoUnit.DAYS.between(
                        today, lot.getExpirationDate())
                        >= ExpirySaleService.MINIMUM_SELLABLE_DAYS)
                .sorted(Comparator.comparing(ProductLot::getExpirationDate))
                .toList();
        ProductLot firstAvailableLot = availableLots.stream()
                .min(Comparator.comparing(ProductLot::getExpirationDate))
                .orElse(null);

        int stock = availableLots.stream()
                .mapToInt(ProductLot::getLotQuantity)
                .sum();
        String animalCode = animalTypeCode(product);
        String animalLabel = animalLabel(product, animalCode);
        String stage = hasText(product.getFeedStage())
                ? product.getFeedStage()
                : "일반 배합";

        return new ProductResponse(
                product.getProductId(),
                "FF-P" + String.format("%05d", product.getProductId()),
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
                product.getManufacturer() == null
                        ? null
                        : product.getManufacturer().getCompanyName(),
                availableLots.stream().map(LotResponse::from).toList(),
                false,
                0,
                0,
                0,
                null,
                null,
                product.isActive()
        );
    }

    public ProductResponse withExpirySale(
            ExpirySaleService.SaleOffer offer) {
        if (offer == null || offer.saleStock() <= 0) {
            return this;
        }
        return new ProductResponse(
                id,
                productCode,
                name,
                animal,
                animalType,
                stage,
                description,
                weight,
                offer.salePrice(),
                price,
                protein,
                fat,
                fiber,
                calcium,
                lot,
                manufacturedDate,
                expiry,
                stock,
                tone,
                badge,
                shape,
                imageUrl,
                manufacturer,
                lots,
                true,
                offer.discountRate(),
                offer.saleStock(),
                offer.daysRemaining(),
                offer.expirationDate(),
                offer.label(),
                active);
    }

    public ProductResponse withSellableStock(int sellableStock) {
        return new ProductResponse(
                id, productCode, name, animal, animalType, stage,
                description, weight, price, originalPrice, protein, fat,
                fiber, calcium, lot, manufacturedDate, expiry,
                Math.max(0, sellableStock), tone, badge, shape, imageUrl,
                manufacturer, lots, expirySale, discountRate, saleStock,
                saleDaysRemaining, saleExpirationDate, saleLabel, active);
    }

    public record LotResponse(
            String lotNumber,
            LocalDate manufacturedDate,
            LocalDate expirationDate,
            int quantity,
            long daysRemaining,
            String status) {
        static LotResponse from(ProductLot lot) {
            long daysRemaining = ChronoUnit.DAYS.between(
                    LocalDate.now(), lot.getExpirationDate());
            String status = daysRemaining <= 30
                    ? "유통기한 임박"
                    : lot.getLotQuantity() <= 10 ? "재고 부족" : "판매 가능";
            return new LotResponse(
                    lot.getLotNo(),
                    lot.getManufacturedDate(),
                    lot.getExpirationDate(),
                    lot.getLotQuantity(),
                    daysRemaining,
                    status);
        }
    }

    public static String animalTypeCode(Product product) {
        String category = product.getAnimalType();
        // null을 그대로 이어붙이면 "null" 문자열이 검색 대상에 섞여 품종을
        // 잘못 판정할 수 있습니다.
        String searchable = (safe(product.getName()) + " "
                + safe(product.getDescription())).toLowerCase();
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

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
