package com.ex.service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ex.dto.FarmDashboardResponse;
import com.ex.dto.FarmDashboardResponse.FarmAlert;
import com.ex.dto.FarmDashboardResponse.FeedbackSummary;
import com.ex.dto.FarmDashboardResponse.RecentOrder;
import com.ex.dto.FarmDashboardResponse.UsagePoint;
import com.ex.entity.CustomerOrder;
import com.ex.entity.Delivery;
import com.ex.entity.FarmCustomer;
import com.ex.entity.FarmFeedUsage;
import com.ex.entity.RecommendationFeedback;
import com.ex.repository.CustomerOrderRepository;
import com.ex.repository.DeliveryRepository;
import com.ex.repository.FarmCustomerRepository;
import com.ex.repository.FarmFeedUsageRepository;
import com.ex.repository.OrderItemRepository;
import com.ex.repository.ProductLotRepository;
import com.ex.repository.ProductRepository;
import com.ex.repository.RecommendationFeedbackRepository;
import com.ex.repository.WarehouseAllocationRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FarmInsightService {

    private final FarmCustomerRepository farmCustomerRepository;
    private final FarmFeedUsageRepository usageRepository;
    private final RecommendationFeedbackRepository feedbackRepository;
    private final ProductRepository productRepository;
    private final CustomerOrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final DeliveryRepository deliveryRepository;
    private final WarehouseAllocationRepository allocationRepository;
    private final ProductLotRepository lotRepository;
    private final FarmFeedModelService farmFeedModelService;

    @Transactional(readOnly = true)
    public FarmDashboardResponse dashboard(Long memberId) {
        FarmCustomer farm = requireFarm(memberId);
        var recommendation = farmFeedModelService.recommendation(farm);
        List<FarmFeedUsage> usages = usageRepository
                .findByFarmCustomerFarmCustomerIdOrderByUsageMonthAsc(
                        farm.getFarmCustomerId());
        List<CustomerOrder> orders = orderRepository
                .findByMember_IdAndCreatedAtAfterOrderByCreatedAtDesc(
                        memberId, LocalDateTime.now().minusMonths(12));
        Map<Long, Delivery> deliveries = deliveryRepository
                .findAllByOrderByDeliveryIdDesc().stream()
                .filter(delivery -> delivery.getOrder().getMember() != null)
                .filter(delivery -> memberId.equals(
                        delivery.getOrder().getMember().getId()))
                .collect(Collectors.toMap(
                        delivery -> delivery.getOrder().getOrderId(),
                        Function.identity(), (first, ignored) -> first));
        List<RecentOrder> recentOrders = orders.stream()
                .limit(8)
                .map(order -> recentOrder(order, deliveries.get(
                        order.getOrderId())))
                .toList();
        long totalPurchaseAmount = orders.stream()
                .filter(order -> order.getStatus()
                        != CustomerOrder.OrderStatus.CANCELLED)
                .map(CustomerOrder::getFinalPrice)
                .filter(java.util.Objects::nonNull)
                .mapToLong(java.math.BigDecimal::longValue)
                .sum();
        List<RecommendationFeedback> feedbacks = feedbackRepository
                .findByFarmCustomerFarmCustomerId(
                        farm.getFarmCustomerId());
        long suitable = feedbacks.stream()
                .filter(RecommendationFeedback::isSuitable).count();
        long unsuitable = feedbacks.size() - suitable;
        int nextQuantity = usages.isEmpty()
                ? farm.getMonthlyFeedQuantity()
                : usages.getLast().getAdjustedNextMonthQuantity();
        return new FarmDashboardResponse(
                farm.getFarmName(),
                farm.getAnimalType(),
                farm.getMonthlyFeedQuantity(),
                nextQuantity,
                farm.getAssignedWarehouse().getName(),
                farm.getDistanceKm(),
                totalPurchaseAmount,
                estimatedSaving(orders),
                recommendation,
                usages.stream().map(this::usagePoint).toList(),
                recentOrders,
                alerts(farm, orders, deliveries),
                new FeedbackSummary(
                        suitable,
                        unsuitable,
                        feedbacks.isEmpty() ? 0
                                : (int) Math.round(
                                        suitable * 100.0 / feedbacks.size())));
    }

    @Transactional
    public UsagePoint recordUsage(
            Long memberId,
            YearMonth month,
            int actualQuantity,
            String note) {
        if (month == null || month.isAfter(YearMonth.now())) {
            throw new IllegalArgumentException("현재 달까지의 사용량만 입력할 수 있습니다.");
        }
        if (actualQuantity <= 0) {
            throw new IllegalArgumentException("실제 사용량은 1포대 이상이어야 합니다.");
        }
        FarmCustomer farm = requireFarm(memberId);
        LocalDate usageMonth = month.atDay(1);
        int predicted = farm.getMonthlyFeedQuantity();
        List<FarmFeedUsage> previous = usageRepository
                .findByFarmCustomerFarmCustomerIdOrderByUsageMonthAsc(
                        farm.getFarmCustomerId()).stream()
                .filter(usage -> !usage.getUsageMonth().equals(usageMonth))
                .sorted(Comparator.comparing(FarmFeedUsage::getUsageMonth)
                        .reversed())
                .limit(2)
                .toList();
        double recentAverage = java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(actualQuantity),
                        previous.stream().map(FarmFeedUsage::getActualQuantity))
                .mapToInt(Integer::intValue)
                .average().orElse(actualQuantity);
        int adjusted = Math.max(1, (int) Math.round(
                recentAverage * .7 + predicted * .3));
        FarmFeedUsage usage = usageRepository
                .findByFarmCustomerFarmCustomerIdAndUsageMonth(
                        farm.getFarmCustomerId(), usageMonth)
                .orElseGet(() -> new FarmFeedUsage(
                        farm, usageMonth, predicted,
                        actualQuantity, adjusted, note));
        usage.update(predicted, actualQuantity, adjusted, note);
        farm.adjustMonthlyFeedQuantity(adjusted);
        return usagePoint(usageRepository.save(usage));
    }

    @Transactional
    public void saveFeedback(
            Long memberId,
            Long productId,
            boolean suitable,
            String comment) {
        FarmCustomer farm = requireFarm(memberId);
        var product = productRepository.findByProductIdAndActiveTrue(productId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "평가할 추천 상품을 찾을 수 없습니다."));
        RecommendationFeedback feedback = feedbackRepository
                .findByFarmCustomerFarmCustomerIdAndProductProductId(
                        farm.getFarmCustomerId(), productId)
                .orElseGet(() -> new RecommendationFeedback(
                        farm, product, suitable, comment));
        feedback.update(suitable, comment);
        feedbackRepository.save(feedback);
    }

    @Transactional(readOnly = true)
    public byte[] monthlyReportCsv(Long memberId) {
        FarmDashboardResponse dashboard = dashboard(memberId);
        StringBuilder csv = new StringBuilder("\uFEFF");
        csv.append("FeedFlow 농장 월간 리포트\r\n");
        csv.append("농장명,").append(csv(dashboard.farmName())).append("\r\n");
        csv.append("축종,").append(csv(dashboard.animalType())).append("\r\n");
        csv.append("담당 창고,").append(csv(dashboard.warehouseName())).append("\r\n");
        csv.append("누적 구매금액,").append(dashboard.totalPurchaseAmount()).append("\r\n");
        csv.append("예상 절감 가능액,").append(dashboard.estimatedSavingAmount()).append("\r\n\r\n");
        csv.append("사용 월,예측량(포),실사용량(포),다음 달 보정량(포),예측 정확도(%)\r\n");
        dashboard.usages().forEach(usage -> csv.append(usage.month())
                .append(',').append(usage.predictedQuantity())
                .append(',').append(usage.actualQuantity())
                .append(',').append(usage.adjustedNextMonthQuantity())
                .append(',').append(usage.accuracyRate()).append("\r\n"));
        csv.append("\r\n주문번호,주문일,상태,수량(포),결제금액\r\n");
        dashboard.recentOrders().forEach(order -> csv
                .append(csv(order.orderNumber())).append(',')
                .append(order.orderedAt().toLocalDate()).append(',')
                .append(csv(order.statusLabel())).append(',')
                .append(order.quantity()).append(',')
                .append(order.amount()).append("\r\n"));
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private RecentOrder recentOrder(CustomerOrder order, Delivery delivery) {
        int quantity = orderItemRepository.findByOrderOrderId(
                order.getOrderId()).stream()
                .mapToInt(item -> item.getQuantity()).sum();
        String deliveryStatus = delivery == null
                ? "배송 준비 전" : delivery.getStatus().getLabel();
        return new RecentOrder(
                order.getOrderNumber(),
                order.getCreatedAt(),
                order.getStatus().name(),
                orderStatusLabel(order.getStatus()),
                quantity,
                order.getFinalPrice() == null ? 0
                        : order.getFinalPrice().longValue(),
                deliveryStatus,
                delivery != null && delivery.isDelayed());
    }

    private List<FarmAlert> alerts(
            FarmCustomer farm,
            List<CustomerOrder> orders,
            Map<Long, Delivery> deliveries) {
        List<FarmAlert> result = new ArrayList<>();
        allocationRepository
                .findAllByOrderByWarehouseDisplayOrderAscProductAnimalTypeAscProductNameAsc()
                .stream()
                .filter(allocation -> allocation.getWarehouse().getWarehouseId()
                        .equals(farm.getAssignedWarehouse().getWarehouseId()))
                .filter(allocation -> animalMatches(
                        allocation.getProduct().getAnimalType(),
                        farm.getAnimalType()))
                .filter(allocation -> allocation.getCurrentStockQuantity()
                        < allocation.getTargetStockQuantity())
                .limit(3)
                .forEach(allocation -> result.add(new FarmAlert(
                        "LOW_STOCK", "warning", "담당 창고 안전재고 부족",
                        allocation.getProduct().getName() + " · 목표 대비 "
                                + (allocation.getTargetStockQuantity()
                                - allocation.getCurrentStockQuantity())
                                + "포 부족",
                        "/#products")));
        LocalDate today = LocalDate.now();
        lotRepository.findAllByOrderByExpirationDateAsc().stream()
                .filter(lot -> lot.getLotQuantity() > 0)
                .filter(lot -> animalMatches(
                        lot.getProduct().getAnimalType(), farm.getAnimalType()))
                .filter(lot -> lot.getExpirationDate() != null
                        && !lot.getExpirationDate().isAfter(today.plusDays(30)))
                .limit(2)
                .forEach(lot -> result.add(new FarmAlert(
                        "EXPIRY", "info", "유통기한 임박 할인 가능",
                        lot.getProduct().getName() + " · "
                                + lot.getExpirationDate() + "까지",
                        "/?sale=true#products")));
        int daysUntilDelivery = farm.getRecurringDeliveryDay() > 0
                ? daysUntilDayOfMonth(farm.getRecurringDeliveryDay())
                : Integer.MAX_VALUE;
        if (daysUntilDelivery <= 7) {
            result.add(new FarmAlert(
                    "RECURRING", "notice", "정기배송 예정",
                    daysUntilDelivery == 0 ? "오늘 정기배송 예정일입니다."
                            : daysUntilDelivery + "일 후 정기배송 예정입니다.",
                    "/mypage"));
        }
        orders.stream().map(order -> deliveries.get(order.getOrderId()))
                .filter(java.util.Objects::nonNull)
                .filter(Delivery::isDelayed)
                .forEach(delivery -> result.add(new FarmAlert(
                        "DELIVERY_DELAY", "danger", "배송 지연",
                        delivery.getOrder().getOrderNumber() + " · "
                                + (delivery.getDelayReason() == null
                                ? "도착 예정일이 지났습니다."
                                : delivery.getDelayReason()),
                        "/mypage/orders/"
                                + delivery.getOrder().getOrderNumber())));
        return result;
    }

    private UsagePoint usagePoint(FarmFeedUsage usage) {
        int gap = Math.abs(usage.getPredictedQuantity()
                - usage.getActualQuantity());
        int accuracy = usage.getActualQuantity() == 0 ? 0
                : Math.max(0, 100 - (int) Math.round(
                        gap * 100.0 / usage.getActualQuantity()));
        return new UsagePoint(
                usage.getUsageId(), usage.getUsageMonth(),
                usage.getPredictedQuantity(), usage.getActualQuantity(),
                usage.getAdjustedNextMonthQuantity(), accuracy,
                usage.getNote());
    }

    private long estimatedSaving(List<CustomerOrder> orders) {
        long spend = orders.stream()
                .filter(order -> order.getStatus()
                        != CustomerOrder.OrderStatus.CANCELLED)
                .map(CustomerOrder::getFinalPrice)
                .filter(java.util.Objects::nonNull)
                .mapToLong(java.math.BigDecimal::longValue).sum();
        return Math.round(spend * .03);
    }

    private int daysUntilDayOfMonth(int day) {
        LocalDate today = LocalDate.now();
        LocalDate candidate = today.withDayOfMonth(
                Math.min(day, today.lengthOfMonth()));
        if (candidate.isBefore(today)) {
            LocalDate next = today.plusMonths(1);
            candidate = next.withDayOfMonth(
                    Math.min(day, next.lengthOfMonth()));
        }
        return (int) java.time.temporal.ChronoUnit.DAYS
                .between(today, candidate);
    }

    private boolean animalMatches(String productAnimal, String farmAnimal) {
        if (productAnimal == null || farmAnimal == null) return false;
        if (farmAnimal.contains("조류")) {
            return productAnimal.contains("조류")
                    || productAnimal.contains("닭")
                    || productAnimal.contains("오리");
        }
        return productAnimal.contains(farmAnimal)
                || farmAnimal.contains(productAnimal);
    }

    private String orderStatusLabel(CustomerOrder.OrderStatus status) {
        return switch (status) {
            case PAYMENT_PENDING -> "결제 대기";
            case PAID -> "결제 완료";
            case PREPARING -> "상품 준비";
            case SHIPPING -> "배송 중";
            case DELIVERED -> "배송 완료";
            case CANCELLED -> "취소";
        };
    }

    private FarmCustomer requireFarm(Long memberId) {
        if (memberId == null) {
            throw new IllegalArgumentException("로그인이 필요합니다.");
        }
        return farmCustomerRepository.findByMemberId(memberId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "등록된 농장 정보를 찾을 수 없습니다."));
    }

    private String csv(String value) {
        return "\"" + (value == null ? "" : value.replace("\"", "\"\"")) + "\"";
    }
}
