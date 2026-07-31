package com.ex.service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ex.entity.CustomerOrder;
import com.ex.entity.CustomerOrder.OrderStatus;
import com.ex.entity.OrderItem;
import com.ex.entity.Product;
import com.ex.entity.ShipmentItem;
import com.ex.entity.Warehouse;
import com.ex.entity.WarehouseAllocation;
import com.ex.repository.CustomerOrderRepository;
import com.ex.repository.OrderItemRepository;
import com.ex.repository.WarehouseAllocationRepository;
import com.ex.repository.WarehouseRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WarehouseFulfillmentService {

    private static final double EARTH_RADIUS_KM = 6371.0088;

    private record ProductNeed(Product product, int quantity) {
    }

    private record Candidate(
            Warehouse warehouse,
            Double distanceKm,
            int regionRank) {
    }

    private final WarehouseRepository warehouseRepository;
    private final WarehouseAllocationRepository allocationRepository;
    private final CustomerOrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    @Transactional
    public Warehouse assignNearest(
            CustomerOrder order,
            Product product,
            int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException(
                    "창고 배정 수량은 1포 이상이어야 합니다.");
        }
        Map<Long, ProductNeed> needs = Map.of(
                product.getProductId(),
                new ProductNeed(product, quantity));
        return assignNearest(order, needs);
    }

    @Transactional
    public void assignUnassignedOrders() {
        orderRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(order -> order.getFulfillmentWarehouse() == null)
                .filter(order -> order.getStatus() == OrderStatus.PAID
                        || order.getStatus() == OrderStatus.PREPARING)
                .forEach(order -> {
                    List<OrderItem> items =
                            orderItemRepository.findByOrderOrderId(
                                    order.getOrderId());
                    if (items.isEmpty()) {
                        return;
                    }
                    try {
                        assignNearest(order, needsFromOrderItems(items));
                    } catch (IllegalStateException ignored) {
                        // 과거 주문은 창고 재고가 부족해도 애플리케이션 시작을 막지 않습니다.
                    }
                });
    }

    @Transactional
    public void deductStock(
            CustomerOrder order,
            List<ShipmentItem> items) {
        Map<Long, ProductNeed> needs = needsFromShipmentItems(items);
        if (order.getFulfillmentWarehouse() == null) {
            assignNearest(order, needs);
        }
        changeStock(order, needs, false);
    }

    @Transactional
    public void restoreStock(
            CustomerOrder order,
            List<ShipmentItem> items) {
        if (order.getFulfillmentWarehouse() == null) {
            return;
        }
        changeStock(order, needsFromShipmentItems(items), true);
    }

    private Warehouse assignNearest(
            CustomerOrder order,
            Map<Long, ProductNeed> needs) {
        if (needs.isEmpty()) {
            throw new IllegalStateException(
                    "창고를 배정할 주문 상품이 없습니다.");
        }

        Map<String, Integer> reserved = reservedQuantities();
        List<String> regionPreference = regionPreference(
                order.getShippingAddress());
        boolean hasCustomerCoordinates =
                validCoordinates(order.getLatitude(), order.getLongitude());

        Candidate candidate = warehouseRepository
                .findAllByActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .filter(warehouse -> hasEnoughStock(
                        warehouse, needs, reserved))
                .map(warehouse -> new Candidate(
                        warehouse,
                        hasCustomerCoordinates && warehouse.hasCoordinates()
                                ? distanceKm(
                                        order.getLatitude(),
                                        order.getLongitude(),
                                        warehouse.getLatitude(),
                                        warehouse.getLongitude())
                                : null,
                        regionRank(regionPreference, warehouse.getCode())))
                .min(hasCustomerCoordinates
                        ? Comparator
                                .comparing(
                                        Candidate::distanceKm,
                                        Comparator.nullsLast(
                                                Comparator.naturalOrder()))
                                .thenComparingInt(item ->
                                        item.warehouse().getDisplayOrder())
                        : Comparator
                                .comparingInt(Candidate::regionRank)
                                .thenComparingInt(item ->
                                        item.warehouse().getDisplayOrder()))
                .orElseThrow(() -> new IllegalStateException(
                        "주문 상품 재고가 충분한 운영 창고가 없습니다."));

        order.assignFulfillmentWarehouse(
                candidate.warehouse(),
                candidate.distanceKm(),
                hasCustomerCoordinates
                        ? "배송지 좌표 기준 자동 배정"
                        : "주소 권역 기준 자동 배정");
        return candidate.warehouse();
    }

    private boolean hasEnoughStock(
            Warehouse warehouse,
            Map<Long, ProductNeed> needs,
            Map<String, Integer> reserved) {
        return needs.values().stream().allMatch(need ->
                allocationRepository
                        .findByWarehouseWarehouseIdAndProductProductId(
                                warehouse.getWarehouseId(),
                                need.product().getProductId())
                        .map(allocation -> {
                            int reservedQuantity = reserved.getOrDefault(
                                    stockKey(
                                            warehouse.getWarehouseId(),
                                            need.product().getProductId()),
                                    0);
                            return allocation.getCurrentStockQuantity()
                                    - reservedQuantity >= need.quantity();
                        })
                        .orElse(false));
    }

    private Map<String, Integer> reservedQuantities() {
        Map<String, Integer> reserved = new HashMap<>();
        orderItemRepository.findByOrderStatusIn(
                List.of(OrderStatus.PAID, OrderStatus.PREPARING))
                .stream()
                .filter(item ->
                        item.getOrder().getFulfillmentWarehouse() != null)
                .forEach(item -> reserved.merge(
                        stockKey(
                                item.getOrder().getFulfillmentWarehouse()
                                        .getWarehouseId(),
                                item.getProduct().getProductId()),
                        item.getQuantity(),
                        Integer::sum));
        return reserved;
    }

    private void changeStock(
            CustomerOrder order,
            Map<Long, ProductNeed> needs,
            boolean restore) {
        Warehouse warehouse = order.getFulfillmentWarehouse();
        Map<Long, WarehouseAllocation> allocations = new HashMap<>();

        needs.values().forEach(need -> {
            WarehouseAllocation allocation = allocationRepository
                    .findByWarehouseWarehouseIdAndProductProductId(
                            warehouse.getWarehouseId(),
                            need.product().getProductId())
                    .orElseThrow(() -> new IllegalStateException(
                            warehouse.getName()
                                    + "에 해당 상품 재고 배치가 없습니다: "
                                    + need.product().getName()));
            if (!restore
                    && allocation.getCurrentStockQuantity()
                            < need.quantity()) {
                throw new IllegalStateException(
                        warehouse.getName() + "의 "
                                + need.product().getName()
                                + " 현재고가 부족합니다.");
            }
            allocations.put(need.product().getProductId(), allocation);
        });

        needs.forEach((productId, need) -> {
            WarehouseAllocation allocation = allocations.get(productId);
            int changedQuantity = restore
                    ? allocation.getCurrentStockQuantity() + need.quantity()
                    : allocation.getCurrentStockQuantity() - need.quantity();
            allocation.adjustCurrentStock(changedQuantity);
        });
    }

    private Map<Long, ProductNeed> needsFromOrderItems(
            List<OrderItem> items) {
        Map<Long, ProductNeed> needs = new LinkedHashMap<>();
        items.forEach(item -> needs.merge(
                item.getProduct().getProductId(),
                new ProductNeed(item.getProduct(), item.getQuantity()),
                (left, right) -> new ProductNeed(
                        left.product(),
                        left.quantity() + right.quantity())));
        return needs;
    }

    private Map<Long, ProductNeed> needsFromShipmentItems(
            List<ShipmentItem> items) {
        Map<Long, ProductNeed> needs = new LinkedHashMap<>();
        items.forEach(item -> needs.merge(
                item.getProduct().getProductId(),
                new ProductNeed(
                        item.getProduct(),
                        item.getPickedQuantity()),
                (left, right) -> new ProductNeed(
                        left.product(),
                        left.quantity() + right.quantity())));
        return needs;
    }

    private List<String> regionPreference(String address) {
        String normalized = address == null ? "" : address;
        if (containsAny(normalized, "서울", "경기", "인천")) {
            return List.of("W04", "W01", "W02", "W03", "W05");
        }
        if (containsAny(normalized, "충남", "대전", "세종")) {
            return List.of("W01", "W04", "W02", "W03", "W05");
        }
        if (normalized.contains("충북")) {
            return List.of("W04", "W01", "W03", "W02", "W05");
        }
        if (normalized.contains("전북")) {
            return List.of("W02", "W05", "W01", "W04", "W03");
        }
        if (containsAny(normalized, "전남", "광주", "제주")) {
            return List.of("W05", "W02", "W01", "W04", "W03");
        }
        if (containsAny(
                normalized,
                "경북", "대구", "경남", "부산", "울산")) {
            return List.of("W03", "W04", "W02", "W01", "W05");
        }
        if (normalized.contains("강원")) {
            return List.of("W04", "W03", "W01", "W02", "W05");
        }
        return List.of("W04", "W01", "W02", "W03", "W05");
    }

    private int regionRank(
            List<String> preferences,
            String warehouseCode) {
        int index = preferences.indexOf(warehouseCode);
        return index < 0 ? Integer.MAX_VALUE : index;
    }

    private boolean containsAny(String text, String... values) {
        for (String value : values) {
            if (text.contains(value)) {
                return true;
            }
        }
        return false;
    }

    private boolean validCoordinates(Double latitude, Double longitude) {
        return latitude != null
                && longitude != null
                && latitude >= -90
                && latitude <= 90
                && longitude >= -180
                && longitude <= 180;
    }

    private double distanceKm(
            double fromLatitude,
            double fromLongitude,
            double toLatitude,
            double toLongitude) {
        double latitudeDistance =
                Math.toRadians(toLatitude - fromLatitude);
        double longitudeDistance =
                Math.toRadians(toLongitude - fromLongitude);
        double value = Math.sin(latitudeDistance / 2)
                * Math.sin(latitudeDistance / 2)
                + Math.cos(Math.toRadians(fromLatitude))
                * Math.cos(Math.toRadians(toLatitude))
                * Math.sin(longitudeDistance / 2)
                * Math.sin(longitudeDistance / 2);
        return EARTH_RADIUS_KM
                * 2
                * Math.atan2(Math.sqrt(value), Math.sqrt(1 - value));
    }

    private String stockKey(Long warehouseId, Long productId) {
        return warehouseId + ":" + productId;
    }
}
