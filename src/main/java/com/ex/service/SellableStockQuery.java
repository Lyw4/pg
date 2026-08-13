package com.ex.service;

import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ex.repository.BinInventoryRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellableStockQuery {

    private final BinInventoryRepository inventoryRepository;

    public int sellable(Long productId) {
        if (productId == null) return 0;
        return normalize(inventoryRepository.sumSellableQuantityByProductId(
                productId,
                WmsAllocationPolicy.ALLOCATABLE_PURPOSES,
                sellableFrom()));
    }

    public Map<Long, Integer> sellableByProductIds(
            Collection<Long> productIds) {
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        if (productIds == null || productIds.isEmpty()) {
            return quantities;
        }
        productIds.stream()
                .filter(productId -> productId != null)
                .forEach(productId -> quantities.put(productId, 0));
        if (quantities.isEmpty()) return quantities;

        List<Object[]> rows = inventoryRepository
                .sumSellableQuantityByProductIds(
                        quantities.keySet(),
                        WmsAllocationPolicy.ALLOCATABLE_PURPOSES,
                        sellableFrom());
        rows.forEach(row -> {
            if (row == null || row.length < 2 || !(row[0] instanceof Number)) {
                return;
            }
            quantities.put(((Number) row[0]).longValue(), normalize(row[1]));
        });
        return quantities;
    }

    public int sellableByLotIds(Collection<Long> lotIds) {
        if (lotIds == null || lotIds.isEmpty()) return 0;
        return normalize(inventoryRepository.sumSellableQuantityByLotIds(
                lotIds,
                WmsAllocationPolicy.ALLOCATABLE_PURPOSES,
                sellableFrom()));
    }

    private LocalDate sellableFrom() {
        return LocalDate.now().plusDays(
                ExpirySaleService.MINIMUM_SELLABLE_DAYS);
    }

    private int normalize(Object quantity) {
        if (!(quantity instanceof Number number)) return 0;
        long value = Math.max(0L, number.longValue());
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }
}
