package com.ex.service;

import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.EnumSet;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ex.repository.BinInventoryRepository;
import com.ex.repository.OrderItemRepository;
import com.ex.entity.CustomerOrder.OrderStatus;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellableStockQuery {

    /** 주문·출고·수요계획이 공통으로 사용하는 최소 판매 가능 잔여일입니다. */
    public static final int MINIMUM_SELLABLE_DAYS =
            ExpirySaleService.MINIMUM_SELLABLE_DAYS;

    private final BinInventoryRepository inventoryRepository;
    private final OrderItemRepository orderItemRepository;
    private static final EnumSet<OrderStatus> RESERVING_STATUSES = EnumSet.of(
            OrderStatus.PAYMENT_PENDING,
            OrderStatus.PAID,
            OrderStatus.PREPARING);

    public int sellable(Long productId) {
        if (productId == null) return 0;
        int physical = normalize(inventoryRepository.sumSellableQuantityByProductId(
                productId,
                WmsAllocationPolicy.ALLOCATABLE_PURPOSES,
                sellableFrom()));
        return Math.max(0, physical - reservedByProductIds(List.of(productId))
                .getOrDefault(productId, 0));
    }

    public int sellableAtWarehouse(Long warehouseId, Long productId) {
        if (warehouseId == null || productId == null) return 0;
        int physical = normalize(
                inventoryRepository.sumSellableQuantityByWarehouseAndProductId(
                        warehouseId,
                        productId,
                        WmsAllocationPolicy.ALLOCATABLE_PURPOSES,
                        sellableFrom()));
        int reserved = reservedByWarehouseAndProductIds(List.of(productId))
                .getOrDefault(stockKey(warehouseId, productId), 0);
        return Math.max(0, physical - reserved);
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
        Map<Long, Integer> reserved = reservedByProductIds(quantities.keySet());
        quantities.replaceAll((productId, physical) -> Math.max(
                0, physical - reserved.getOrDefault(productId, 0)));
        return quantities;
    }

    public int sellableByLotIds(Collection<Long> lotIds) {
        if (lotIds == null || lotIds.isEmpty()) return 0;
        int physical = normalize(inventoryRepository.sumSellableQuantityByLotIds(
                lotIds,
                WmsAllocationPolicy.ALLOCATABLE_PURPOSES,
                sellableFrom()));
        int reserved = reservedByLotIds(lotIds, null).values().stream()
                .mapToInt(Integer::intValue).sum();
        return Math.max(0, physical - reserved);
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
        Map<Long, Integer> reserved = reservedByLotIds(
                quantities.keySet(), warehouseId);
        quantities.replaceAll((lotId, physical) -> Math.max(
                0, physical - reserved.getOrDefault(lotId, 0)));
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
        Map<String, Integer> reserved = reservedByWarehouseAndProductIds(productIds);
        quantities.replaceAll((key, physical) -> Math.max(
                0, physical - reserved.getOrDefault(key, 0)));
        return quantities;
    }

    public String stockKey(Long warehouseId, Long productId) {
        return warehouseId + ":" + productId;
    }

    private LocalDate sellableFrom() {
        return LocalDate.now().plusDays(
                MINIMUM_SELLABLE_DAYS);
    }

    private int normalize(Object quantity) {
        if (!(quantity instanceof Number number)) return 0;
        long value = Math.max(0L, number.longValue());
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private Map<Long, Integer> reservedByLotIds(
            Collection<Long> lotIds,
            Long warehouseId) {
        Map<Long, Integer> result = new LinkedHashMap<>();
        if (lotIds == null || lotIds.isEmpty()) return result;
        List<Object[]> rows = warehouseId == null
                ? orderItemRepository.sumReservedQuantitiesByLotIds(
                        RESERVING_STATUSES, lotIds)
                : orderItemRepository.sumReservedQuantitiesByLotIdsAtWarehouse(
                        RESERVING_STATUSES, lotIds, warehouseId);
        rows.forEach(row -> result.put(
                ((Number) row[0]).longValue(), normalize(row[1])));
        return result;
    }

    private Map<Long, Integer> reservedByProductIds(
            Collection<Long> productIds) {
        Map<Long, Integer> result = new LinkedHashMap<>();
        if (productIds == null || productIds.isEmpty()) return result;
        orderItemRepository.sumReservedQuantitiesByProductIds(
                        RESERVING_STATUSES, productIds)
                .forEach(row -> result.put(
                        ((Number) row[0]).longValue(), normalize(row[1])));
        return result;
    }

    private Map<String, Integer> reservedByWarehouseAndProductIds(
            Collection<Long> productIds) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (productIds == null || productIds.isEmpty()) return result;
        orderItemRepository.sumReservedQuantities(RESERVING_STATUSES)
                .stream()
                .filter(row -> productIds.contains(
                        ((Number) row[1]).longValue()))
                .forEach(row -> result.put(
                        stockKey(((Number) row[0]).longValue(),
                                ((Number) row[1]).longValue()),
                        normalize(row[2])));
        return result;
    }
}
