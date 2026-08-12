package com.ex.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ex.dto.FarmModelResponse;
import com.ex.dto.FarmModelResponse.AlternativeFeed;
import com.ex.dto.FarmModelResponse.FeedRecommendation;
import com.ex.entity.FeedModelPolicy;
import com.ex.dto.SignupRequest;
import com.ex.entity.FarmCustomer;
import com.ex.entity.Product;
import com.ex.entity.ProductLot;
import com.ex.repository.WarehouseAllocationRepository;
import com.ex.repository.FarmFeedUsageRepository;
import com.ex.repository.FeedModelPolicyRepository;
import com.ex.repository.FarmCustomerRepository;
import com.ex.repository.OrderItemRepository;
import com.ex.repository.ProductRepository;
import com.ex.repository.RecommendationFeedbackRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FarmFeedModelService {

    private final FarmCustomerRepository farmCustomerRepository;
    private final ProductRepository productRepository;
    private final WarehouseAllocationRepository allocationRepository;
    private final FarmFeedUsageRepository usageRepository;
    private final FeedModelPolicyRepository policyRepository;
    private final RecommendationFeedbackRepository feedbackRepository;
    private final OrderItemRepository orderItemRepository;

    public ModeledProfile model(SignupRequest.FarmProfileRequest request) {
        String animalType = normalizeAnimalType(
                request == null ? null : request.animalType());
        int livestockCount = positiveOrZero(
                request == null ? null : request.livestockCount());
        List<FarmCustomer> comparableFarms = comparableFarms(animalType);

        boolean quantityEstimated = request == null
                || request.monthlyFeedQuantity() == null
                || request.monthlyFeedQuantity() <= 0;
        int monthlyQuantity = quantityEstimated
                ? estimateMonthlyQuantity(
                        livestockCount, comparableFarms, animalType)
                : request.monthlyFeedQuantity();
        String requestedFeed = request == null ? null : request.preferredFeed();
        String preferredFeed = hasText(requestedFeed)
                ? requestedFeed.trim()
                : mostCommonPreferredFeed(comparableFarms);
        String basis = comparableFarms.isEmpty()
                ? "축종 기본 소비계수 기준"
                : "가상 유사 농장 " + comparableFarms.size()
                        + "곳의 월 사용량 평균 기준";
        return new ModeledProfile(
                animalType,
                livestockCount,
                monthlyQuantity,
                preferredFeed,
                comparableFarms.size(),
                quantityEstimated,
                basis);
    }

    public FarmModelResponse recommendation(FarmCustomer farmCustomer) {
        List<FarmCustomer> comparableFarms = comparableFarms(
                farmCustomer.getAnimalType());
        String preferredFeed = hasText(farmCustomer.getPreferredFeed())
                ? farmCustomer.getPreferredFeed()
                : mostCommonPreferredFeed(comparableFarms);
        FeedModelPolicy policy = policy(farmCustomer.getAnimalType());
        Set<Long> excludedProductIds = excludedProductIds(policy);
        Map<Long, Boolean> feedbackByProduct = feedbackRepository
                .findByFarmCustomerFarmCustomerId(
                        farmCustomer.getFarmCustomerId())
                .stream()
                .collect(Collectors.toMap(
                        feedback -> feedback.getProduct().getProductId(),
                        feedback -> feedback.isSuitable()));
        Map<Long, Integer> purchaseQuantityByProduct = new HashMap<>();
        int completedPurchaseCount = 0;
        if (farmCustomer.getMember() != null) {
            var purchasedItems = orderItemRepository.findByOrderMemberId(
                    farmCustomer.getMember().getId());
            purchasedItems.stream()
                    .filter(item -> item.getOrder().getStatus()
                            != com.ex.entity.CustomerOrder.OrderStatus.CANCELLED)
                    .forEach(item -> purchaseQuantityByProduct.merge(
                            item.getProduct().getProductId(),
                            item.getQuantity(), Integer::sum));
            completedPurchaseCount = (int) purchasedItems.stream()
                    .map(item -> item.getOrder().getOrderId())
                    .distinct().count();
        }
        LocalDate today = LocalDate.now();
        List<ProductCandidate> rankedCandidates = productRepository
                .findAllByActiveTrueOrderByProductIdAsc()
                .stream()
                .filter(product -> matchesAnimal(
                        product.getAnimalType(), farmCustomer.getAnimalType()))
                .filter(product -> !excludedProductIds.contains(
                        product.getProductId()))
                .map(product -> recommendationCandidate(
                        product, farmCustomer, today,
                        preferredFeed, policy,
                        feedbackByProduct, purchaseQuantityByProduct))
                .filter(candidate -> candidate.sellableStock() > 0)
                .sorted(Comparator
                        .comparingInt(ProductCandidate::score).reversed()
                        .thenComparing(Comparator.comparingInt(
                                ProductCandidate::assignedWarehouseStock)
                                .reversed())
                        .thenComparing(candidate ->
                                candidate.product().getName()))
                .toList();
        List<FeedRecommendation> feeds = rankedCandidates.stream()
                .limit(3)
                .map(candidate -> new FeedRecommendation(
                        candidate.product().getProductId(),
                        candidate.product().getName(),
                        candidate.product().getFeedStage(),
                        candidate.product().getAnimalType(),
                        candidate.product().getPrice().intValue(),
                        candidate.product().getImageUrl(),
                        candidate.product().getName().equalsIgnoreCase(preferredFeed)
                                ? "유사 농장 선호도가 높은 사료"
                                : candidate.assignedWarehouseStock() > 0
                                        ? farmCustomer.getAnimalType()
                                                + " 사육 단계와 담당 창고 재고를 함께 반영"
                                        : farmCustomer.getAnimalType()
                                                + " 사육 단계에 맞는 판매 가능 상품",
                        candidate.sellableStock(),
                        candidate.assignedWarehouseStock(),
                        candidate.nearestExpirationDate(),
                        candidate.expiringSoon(),
                        candidate.assignedWarehouseStock() > 0
                                ? "담당 창고 즉시 공급 가능"
                                : "다른 거점 재고로 공급",
                        candidate.score(),
                        feedbackByProduct.get(
                                candidate.product().getProductId()),
                        alternativesFor(candidate, rankedCandidates)))
                .toList();
        boolean estimated = farmCustomer.getNotes() != null
                && farmCustomer.getNotes().contains("모델 예측");
        String basis = comparableFarms.isEmpty()
                ? "축종 기본 소비계수와 등록 상품 기준"
                : "가상 유사 농장 " + comparableFarms.size()
                        + "곳과 현재 판매 상품 기준";
        double monthlyBagsPerHead = farmCustomer.getLivestockCount() <= 0
                ? 0
                : (double) farmCustomer.getMonthlyFeedQuantity()
                        / farmCustomer.getLivestockCount();
        String quantityReason = estimated
                ? "같은 축종의 가상 농장별 월 사용량을 사육 규모로 나눈 뒤 평균 소비계수를 적용했습니다."
                : "회원가입 때 입력한 월 사료 사용량을 사용했습니다.";
        String productSelectionReason = hasText(preferredFeed)
                && !"상담 후 지정".equals(preferredFeed)
                        ? "선호 사료 일치 여부를 먼저 비교하고, 같은 축종의 활성 상품과 현재 재고를 함께 반영했습니다."
                        : "같은 축종의 활성 상품 중 현재 재고가 충분한 상품을 우선 추천했습니다.";
        int usageMonths = usageRepository
                .findByFarmCustomerFarmCustomerIdOrderByUsageMonthAsc(
                        farmCustomer.getFarmCustomerId()).size();
        String confidenceLevel = confidenceLevel(
                comparableFarms.size(), usageMonths, completedPurchaseCount);
        String confidenceReason = "유사 농장 %d곳 · 실사용 %d개월 · 구매 이력 %d건 기준"
                .formatted(comparableFarms.size(), usageMonths,
                        completedPurchaseCount);
        return new FarmModelResponse(
                farmCustomer.getAnimalType(),
                farmCustomer.getLivestockCount(),
                farmCustomer.getMonthlyFeedQuantity(),
                preferredFeed,
                comparableFarms.size(),
                estimated,
                basis,
                monthlyBagsPerHead,
                quantityReason,
                productSelectionReason,
                confidenceLevel,
                confidenceReason,
                today,
                policy.getModelVersion(),
                feeds);
    }

    private ProductCandidate recommendationCandidate(
            Product product,
            FarmCustomer farmCustomer,
            LocalDate today,
            String preferredFeed,
            FeedModelPolicy policy,
            Map<Long, Boolean> feedbackByProduct,
            Map<Long, Integer> purchaseQuantityByProduct) {
        List<ProductLot> sellableLots = product.getLots().stream()
                .filter(lot -> lot.getLotQuantity() > 0)
                .filter(lot -> lot.getExpirationDate() != null)
                .filter(lot -> !lot.getExpirationDate().isBefore(
                        today.plusDays(ExpirySaleService.MINIMUM_SELLABLE_DAYS)))
                .toList();
        int sellableStock = sellableLots.stream()
                .mapToInt(ProductLot::getLotQuantity)
                .sum();
        LocalDate nearestExpirationDate = sellableLots.stream()
                .map(ProductLot::getExpirationDate)
                .min(LocalDate::compareTo)
                .orElse(null);
        int assignedWarehouseStock = farmCustomer.getAssignedWarehouse() == null
                ? 0
                : allocationRepository
                        .findByWarehouseWarehouseIdAndProductProductId(
                                farmCustomer.getAssignedWarehouse()
                                        .getWarehouseId(),
                                product.getProductId())
                        .map(allocation -> Math.max(
                                0, allocation.getCurrentStockQuantity()))
                        .orElse(0);
        boolean expiringSoon = nearestExpirationDate != null
                && ChronoUnit.DAYS.between(today, nearestExpirationDate) <= 30;
        int score = 50;
        if (product.getName().equalsIgnoreCase(preferredFeed)) {
            score += policy.getPreferredFeedWeight();
        }
        if (assignedWarehouseStock > 0) {
            score += policy.getWarehouseStockWeight();
        }
        score += Math.min(30, purchaseQuantityByProduct.getOrDefault(
                product.getProductId(), 0));
        Boolean feedback = feedbackByProduct.get(product.getProductId());
        if (Boolean.TRUE.equals(feedback)) score += 20;
        if (Boolean.FALSE.equals(feedback)) score -= 80;
        if (expiringSoon) score -= 5;
        return new ProductCandidate(
                product,
                sellableStock,
                assignedWarehouseStock,
                nearestExpirationDate,
                expiringSoon,
                score);
    }

    private List<AlternativeFeed> alternativesFor(
            ProductCandidate selected,
            List<ProductCandidate> rankedCandidates) {
        return rankedCandidates.stream()
                .filter(candidate -> !candidate.product().getProductId()
                        .equals(selected.product().getProductId()))
                .filter(candidate -> Objects.equals(
                        normalizedStage(candidate.product().getFeedStage()),
                        normalizedStage(selected.product().getFeedStage())))
                .limit(2)
                .map(candidate -> new AlternativeFeed(
                        candidate.product().getProductId(),
                        candidate.product().getName(),
                        candidate.product().getPrice().intValue(),
                        candidate.sellableStock(),
                        candidate.nearestExpirationDate(),
                        comparisonLabel(selected, candidate)))
                .toList();
    }

    private String comparisonLabel(
            ProductCandidate selected,
            ProductCandidate alternative) {
        int priceGap = alternative.product().getPrice()
                .subtract(selected.product().getPrice()).intValue();
        String price = priceGap == 0 ? "동일 가격"
                : priceGap < 0 ? "%,d원 저렴".formatted(-priceGap)
                : "%,d원 높음".formatted(priceGap);
        return price + " · 재고 %,d포".formatted(
                alternative.sellableStock());
    }

    private String normalizedStage(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private FeedModelPolicy policy(String animalType) {
        String normalized = normalizeAnimalType(animalType);
        return policyRepository.findByAnimalType(normalized)
                .orElseGet(() -> new FeedModelPolicy(
                                normalized,
                                java.math.BigDecimal.valueOf(
                                        defaultBagsPerHead(normalized)),
                                30,
                                20));
    }

    private Set<Long> excludedProductIds(FeedModelPolicy policy) {
        if (!hasText(policy.getExcludedProductIds())) return Set.of();
        return java.util.Arrays.stream(
                        policy.getExcludedProductIds().split(","))
                .map(String::trim)
                .filter(value -> value.matches("\\d+"))
                .map(Long::valueOf)
                .collect(Collectors.toSet());
    }

    private String confidenceLevel(
            int samples,
            int usageMonths,
            int purchaseCount) {
        if (samples >= 5 && (usageMonths >= 2 || purchaseCount >= 2)) {
            return "높음";
        }
        if (samples >= 3 || usageMonths >= 1 || purchaseCount >= 1) {
            return "보통";
        }
        return "참고용";
    }

    private List<FarmCustomer> comparableFarms(String animalType) {
        return farmCustomerRepository
                .findAllByOrderByAssignedWarehouseDisplayOrderAscFarmNameAsc()
                .stream()
                .filter(FarmCustomer::isDemoData)
                .filter(farm -> normalizeAnimalType(farm.getAnimalType())
                        .equals(normalizeAnimalType(animalType)))
                .filter(farm -> farm.getLivestockCount() > 0
                        && farm.getMonthlyFeedQuantity() > 0)
                .toList();
    }

    private int estimateMonthlyQuantity(
            int livestockCount,
            List<FarmCustomer> comparableFarms,
            String animalType) {
        if (!comparableFarms.isEmpty()) {
            double averagePerHead = comparableFarms.stream()
                    .mapToDouble(farm -> (double) farm.getMonthlyFeedQuantity()
                            / farm.getLivestockCount())
                    .average()
                    .orElse(0);
            if (livestockCount > 0) {
                return Math.max(1, (int) Math.round(
                        livestockCount * averagePerHead));
            }
            return (int) Math.round(comparableFarms.stream()
                    .mapToInt(FarmCustomer::getMonthlyFeedQuantity)
                    .average()
                    .orElse(0));
        }
        return Math.max(1, (int) Math.round(
                livestockCount * defaultBagsPerHead(animalType)));
    }

    private String mostCommonPreferredFeed(List<FarmCustomer> farms) {
        return farms.stream()
                .map(FarmCustomer::getPreferredFeed)
                .filter(this::hasText)
                .collect(Collectors.groupingBy(
                        Function.identity(), Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.<String, Long>comparingByValue()
                        .thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey)
                .orElse("상담 후 지정");
    }

    private boolean matchesAnimal(String productAnimal, String farmAnimal) {
        return Objects.equals(
                normalizeAnimalType(productAnimal),
                normalizeAnimalType(farmAnimal));
    }

    private String normalizeAnimalType(String value) {
        if (!hasText(value)) return "미등록";
        String normalized = value.trim().toLowerCase();
        if (normalized.contains("소") || normalized.contains("한우")
                || normalized.contains("젖소") || normalized.contains("cattle")) {
            return "소";
        }
        if (normalized.contains("돼지") || normalized.contains("돈")
                || normalized.contains("pig")) {
            return "돼지";
        }
        if (normalized.contains("닭") || normalized.contains("오리")
                || normalized.contains("조류") || normalized.contains("chicken")
                || normalized.contains("duck") || normalized.contains("poultry")) {
            return "조류(닭/오리)";
        }
        return value.trim();
    }

    private double defaultBagsPerHead(String animalType) {
        String normalized = normalizeAnimalType(animalType);
        return policyRepository.findByAnimalType(normalized)
                .map(policy -> policy.getBagsPerHead().doubleValue())
                .orElseGet(() -> switch (normalized) {
            case "소" -> 3.8;
            case "돼지" -> 0.72;
            case "조류(닭/오리)" -> 0.05;
            default -> 1.0;
        });
    }

    private int positiveOrZero(Integer value) {
        return value == null ? 0 : Math.max(0, value);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record ModeledProfile(
            String animalType,
            int livestockCount,
            int monthlyFeedQuantity,
            String preferredFeed,
            int comparableFarmCount,
            boolean monthlyQuantityEstimated,
            String modelBasis) {
    }

    private record ProductCandidate(
            Product product,
            int sellableStock,
            int assignedWarehouseStock,
            LocalDate nearestExpirationDate,
            boolean expiringSoon,
            int score) {
    }
}
