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

    public Map<Long, Integer> sellablePerLot(Collection<Long> lotIds) {
        return sellablePerLot(lotIds, null);
    }

    public Map<Long, Integer> sellablePerLot(
            Collection<Long> lotIds,
            Long warehouseId) {
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        if (lotIds == null || lotIds.isEmpty()) return quantities;
        lotIds.stream().filter(java.util.Objects::nonNull)
                .forEach(lotId -> quantities.put(lotId, 0));
        List<Object[]> rows = warehouseId == null
                ? inventoryRepository.sumSellableQuantityPerLot(
                        quantities.keySet(),
                        WmsAllocationPolicy.ALLOCATABLE_PURPOSES,
                        sellableFrom())
                : inventoryRepository.sumSellableQuantityPerLotAtWarehouse(
                        quantities.keySet(), warehouseId,
                        WmsAllocationPolicy.ALLOCATABLE_PURPOSES,
                        sellableFrom());
        rows.forEach(row -> quantities.put(
                ((Number) row[0]).longValue(), normalize(row[1])));
        return quantities;
    }

    public Map<String, Integer> sellableByWarehouseAndProductIds(
            Collection<Long> productIds) {
        Map<String, Integer> quantities = new LinkedHashMap<>();
        if (productIds == null || productIds.isEmpty()) return quantities;
        inventoryRepository.sumSellableQuantityByWarehouseAndProductIds(
                        productIds,
                        WmsAllocationPolicy.ALLOCATABLE_PURPOSES,
                        sellableFrom())
                .forEach(row -> quantities.put(
                        stockKey(((Number) row[0]).longValue(),
                                ((Number) row[1]).longValue()),
                        normalize(row[2])));
        return quantities;
    }

    public String stockKey(Long warehouseId, Long productId) {
        return warehouseId + ":" + productId;
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
