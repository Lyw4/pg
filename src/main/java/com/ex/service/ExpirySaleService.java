package com.ex.service;

import com.ex.entity.Product;
import com.ex.entity.ProductLot;
import com.ex.entity.WarehouseAllocation;
import com.ex.repository.WarehouseAllocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExpirySaleService {

    /** 배송 2일과 최소 안전 여유 2일을 고려해 D-3 이하는 판매하지 않습니다. */
    public static final int MINIMUM_SELLABLE_DAYS = 4;

    private final WarehouseAllocationRepository allocationRepository;
    private final SellableStockQuery sellableStockQuery;

    public record SaleOffer(
            int discountRate,
            int salePrice,
            int saleStock,
            long daysRemaining,
            LocalDate expirationDate,
            boolean overstock,
            List<Long> lotIds) {

        public String label() {
            return "D-" + daysRemaining + " · " + discountRate + "% 할인";
        }
    }

    public Map<Long, SaleOffer> offersFor(Collection<Product> products) {
        Set<Long> overstockProductIds = allocationRepository
                .findAllByOrderByWarehouseDisplayOrderAscProductAnimalTypeAscProductNameAsc()
                .stream()
                .filter(this::isOverstock)
                .map(allocation -> allocation.getProduct().getProductId())
                .collect(Collectors.toSet());

        Map<Long, Integer> sellableByLot = sellableStockQuery.sellablePerLot(
                products.stream()
                        .flatMap(product -> product.getLots().stream())
                        .filter(lot -> lot.getLotQuantity() > 0)
                        .map(ProductLot::getLotId)
                        .toList());
        Map<Long, SaleOffer> offers = new LinkedHashMap<>();
        products.forEach(product -> evaluate(
                product,
                overstockProductIds.contains(product.getProductId()),
                sellableByLot)
                .ifPresent(offer -> offers.put(product.getProductId(), offer)));
        return offers;
    }

    public Optional<SaleOffer> offerFor(Product product) {
        boolean overstock = allocationRepository
                .findByProductProductId(product.getProductId())
                .stream()
                .anyMatch(this::isOverstock);
        return evaluate(product, overstock, null);
    }

    private Optional<SaleOffer> evaluate(
            Product product,
            boolean overstock,
            Map<Long, Integer> prefetchedSellableByLot) {
        LocalDate today = LocalDate.now();
        List<LotCandidate> candidates = product.getLots().stream()
                .filter(lot -> lot.getLotQuantity() > 0)
                .map(lot -> candidate(lot, today, overstock))
                .filter(candidate -> candidate.discountRate() > 0)
                .sorted(Comparator
                        .comparingInt(LotCandidate::discountRate).reversed()
                        .thenComparing(candidate -> candidate.lot().getExpirationDate()))
                .toList();
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        int selectedRate = candidates.getFirst().discountRate();
        List<LotCandidate> selected = candidates.stream()
                .filter(candidate -> candidate.discountRate() == selectedRate)
                .toList();
        Map<Long, Integer> sellableByLot = prefetchedSellableByLot != null
                ? prefetchedSellableByLot
                : sellableStockQuery.sellablePerLot(
                        selected.stream()
                                .map(candidate -> candidate.lot().getLotId())
                                .toList());
        int saleStock = selected.stream()
                .mapToInt(candidate -> Math.min(
                        candidate.lot().getLotQuantity(),
                        sellableByLot.getOrDefault(
                                candidate.lot().getLotId(), 0)))
                .sum();
        if (saleStock <= 0) return Optional.empty();
        LotCandidate nearest = selected.stream()
                .min(Comparator.comparing(candidate ->
                        candidate.lot().getExpirationDate()))
                .orElseThrow();
        BigDecimal salePrice = product.getPrice()
                .multiply(BigDecimal.valueOf(100L - selectedRate))
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);

        return Optional.of(new SaleOffer(
                selectedRate,
                salePrice.intValue(),
                saleStock,
                nearest.daysRemaining(),
                nearest.lot().getExpirationDate(),
                overstock,
                selected.stream()
                        .map(candidate -> candidate.lot().getLotId())
                        .toList()));
    }

    private LotCandidate candidate(
            ProductLot lot,
            LocalDate today,
            boolean overstock) {
        long days = ChronoUnit.DAYS.between(today, lot.getExpirationDate());
        return new LotCandidate(lot, days, discountRate(days, overstock));
    }

    private int discountRate(long daysRemaining, boolean overstock) {
        if (daysRemaining < MINIMUM_SELLABLE_DAYS) return 0;
        if (daysRemaining <= 7) return 40;
        if (daysRemaining <= 14) return 30;
        if (daysRemaining <= 29) return 20;
        if (daysRemaining <= 45 && overstock) return 10;
        return 0;
    }

    private boolean isOverstock(WarehouseAllocation allocation) {
        return allocation.getCurrentStockQuantity()
                > allocation.getTargetStockQuantity() * 1.2;
    }

    private record LotCandidate(
            ProductLot lot,
            long daysRemaining,
            int discountRate) {
    }
}
