package com.ex.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 농장 회원 화면에 필요한 추천·소비·주문·배송·알림 통합 결과입니다. */
public record FarmDashboardResponse(
        String farmName,
        String animalType,
        int predictedMonthlyQuantity,
        int adjustedNextMonthQuantity,
        String warehouseName,
        double warehouseDistanceKm,
        long totalPurchaseAmount,
        long estimatedSavingAmount,
        FarmModelResponse recommendation,
        List<UsagePoint> usages,
        List<RecentOrder> recentOrders,
        List<FarmAlert> alerts,
        FeedbackSummary feedbackSummary) {

    public record UsagePoint(
            Long usageId,
            LocalDate month,
            int predictedQuantity,
            int actualQuantity,
            int adjustedNextMonthQuantity,
            int accuracyRate,
            String note) {
    }

    public record RecentOrder(
            String orderNumber,
            LocalDateTime orderedAt,
            String status,
            String statusLabel,
            int quantity,
            long amount,
            String deliveryStatus,
            boolean delayed) {
    }

    public record FarmAlert(
            String type,
            String level,
            String title,
            String message,
            String actionUrl) {
    }

    public record FeedbackSummary(
            long suitableCount,
            long unsuitableCount,
            int suitabilityRate) {
    }
}
