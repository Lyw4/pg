package com.ex.dto;

import java.time.LocalDate;
import java.util.List;

/** 가입 농장과 가상 농장 표본을 비교해 만든 배합사료 안내 결과입니다. */
public record FarmModelResponse(
        String animalType,
        int livestockCount,
        int monthlyFeedQuantity,
        String preferredFeed,
        int comparableFarmCount,
        boolean monthlyQuantityEstimated,
        String modelBasis,
        double monthlyBagsPerHead,
        String quantityReason,
        String productSelectionReason,
        String confidenceLevel,
        String confidenceReason,
        LocalDate dataAsOf,
        int modelVersion,
        List<FeedRecommendation> recommendedFeeds) {

    public record FeedRecommendation(
            Long productId,
            String name,
            String feedStage,
            String animalType,
            int price,
            String imageUrl,
            String reason,
            int sellableStock,
            int assignedWarehouseStock,
            LocalDate nearestExpirationDate,
            boolean expiringSoon,
            String availabilityLabel,
            int recommendationScore,
            Boolean suitableFeedback,
            List<AlternativeFeed> alternatives) {
    }

    public record AlternativeFeed(
            Long productId,
            String name,
            int price,
            int stock,
            LocalDate nearestExpirationDate,
            String comparisonLabel) {
    }
}
