package com.ex.service;

import java.util.Comparator;
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
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class WarehouseFulfillmentService {

    private static final double EARTH_RADIUS_KM = 6371.0088;

    private record ProductNeed(
            Product product,
            int quantity,
            List<Long> restrictedLotIds) {
    }

    public record ProductRequest(
            Product product,
            int quantity,
            List<Long> restrictedLotIds) {
        public ProductRequest(Product product, int quantity) {
            this(product, quantity, List.of());
        }

        public ProductRequest {
            if (product == null || quantity <= 0) {
                throw new IllegalArgumentException(
                        "창고 배정 상품과 수량을 확인해 주세요.");
            }
            restrictedLotIds = restrictedLotIds == null
                    ? List.of()
                    : List.copyOf(restrictedLotIds);
        }
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
    private final SellableStockQuery sellableStockQuery;

    @Transactional
    public Warehouse assignPreferredOrNearestForProducts(
            CustomerOrder order,
            List<ProductRequest> requests,
            Warehouse preferredWarehouse) {
        Map<Long, ProductNeed> needs = productNeeds(requests);
        if (preferredWarehouse != null && preferredWarehouse.isActive()) {
            Map<String, Integer> sellable = sellableStockQuery
                    .sellableByWarehouseAndProductIds(needs.keySet());
            if (hasEnoughStock(preferredWarehouse, needs, sellable)) {
                order.assignFulfillmentWarehouse(
                        preferredWarehouse,
                        order.getFarmCustomer() == null
                                ? null
                                : order.getFarmCustomer().getDistanceKm(),
                        "회원가입 시 지정된 담당 창고");
                return preferredWarehouse;
            }
        }
        return assignNearest(order, needs);
    }

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
                new ProductNeed(product, quantity, List.of()));
        return assignNearest(order, needs);
    }

    @Transactional
    public Warehouse assignNearest(
            CustomerOrder order,
            List<OrderItem> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException(
                    "창고를 배정할 주문 상품이 없습니다.");
        }
        return assignNearest(order, needsFromOrderItems(items));
    }

    @Transactional
    public int assignUnassignedOrders() {
        int failedCount = 0;
        List<CustomerOrder> targets = orderRepository
                .findAllByOrderByCreatedAtDesc().stream()
                .filter(order -> order.getFulfillmentWarehouse() == null)
                .filter(order -> order.getStatus() == OrderStatus.PAID
                        || order.getStatus() == OrderStatus.PREPARING)
                .toList();
        for (CustomerOrder order : targets) {
            List<OrderItem> items = orderItemRepository
                    .findByOrderOrderId(order.getOrderId());
            if (items.isEmpty()) continue;
            try {
                assignNearest(order, needsFromOrderItems(items));
            } catch (IllegalStateException exception) {
                failedCount++;
                log.warn("창고 자동 배정 실패 orderId={} reason={}",
                        order.getOrderId(), exception.getMessage());
            }
        }
        return failedCount;
    }

    @Transactional
    public void deductStock(
            CustomerOrder order,
            List<ShipmentItem> items) {
        Map<Long, ProductNeed> needs = needsFromShipmentItems(items);
        if (order.getFulfillmentWarehouse() == null) {
            assignNearest(order, needs);
        }
        refreshAllocations(order, needs);
    }

    @Transactional
    public void restoreStock(
            CustomerOrder order,
            List<ShipmentItem> items) {
        if (order.getFulfillmentWarehouse() == null) {
            return;
        }
        refreshAllocations(order, needsFromShipmentItems(items));
    }

    private Warehouse assignNearest(
            CustomerOrder order,
            Map<Long, ProductNeed> needs) {
        if (needs.isEmpty()) {
            throw new IllegalStateException(
                    "창고를 배정할 주문 상품이 없습니다.");
        }

        Map<String, Integer> sellable = sellableStockQuery
                .sellableByWarehouseAndProductIds(needs.keySet());
        List<String> regionPreference = regionPreference(
                order.getShippingAddress());
        boolean hasCustomerCoordinates =
                validCoordinates(order.getLatitude(), order.getLongitude());

        Candidate candidate = warehouseRepository
                .findAllByActiveTrueOrderByDisplayOrderAsc()
                .stream()
                .filter(warehouse -> hasEnoughStock(
                        warehouse, needs, sellable))
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
                // 재고가 모자랄 때도 이 분기를 타므로, 창고 운영 문제로만
                // 읽히지 않게 재고 부족 가능성을 함께 알려 줍니다.
                .orElseThrow(() -> new IllegalStateException(
                        "주문 수량만큼 출고할 수 있는 창고가 없습니다. "
                                + "판매 가능 재고가 부족하거나 운영 중인 창고가 없습니다."));

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
            Map<String, Integer> sellable) {
        return needs.values().stream().allMatch(need -> {
            if (!need.restrictedLotIds().isEmpty()) {
                int restrictedSellable = sellableStockQuery
                        .sellablePerLot(
                                need.restrictedLotIds(),
                                warehouse.getWarehouseId())
                        .values().stream()
                        .mapToInt(Integer::intValue)
                        .sum();
                return restrictedSellable >= need.quantity();
            }
            String key = stockKey(
                    warehouse.getWarehouseId(), need.product().getProductId());
            return sellable.getOrDefault(key, 0) >= need.quantity();
        });
    }


    /**
     * 창고별 판매 가능 재고를 다시 계산해 {@link WarehouseAllocation} 집계에
     * 옮겨 적습니다. 로트 잔량을 직접 더하거나 빼지는 않습니다.
     *
     * <p>물리 재고 변경은 출고를 확정하는 {@code ShipmentService}가 담당하고,
     * 이 메서드는 그 결과와 미출고 주문의 예약분을 반영한 값을 집계 테이블에
     * 반영하는 역할만 합니다. 이전에는 사용되지 않는 {@code restore} 플래그를
     * 받아 차감과 복구가 구분되는 것처럼 보였으나 실제 동작은 동일했습니다.
     */
    private void refreshAllocations(
            CustomerOrder order,
            Map<Long, ProductNeed> needs) {
        Warehouse warehouse = order.getFulfillmentWarehouse();
        if (warehouse == null) {
            throw new IllegalStateException(
                    "출고 창고가 배정되지 않아 재고 집계를 갱신할 수 없습니다.");
        }
        Map<String, Integer> sellable = sellableStockQuery
                .sellableByWarehouseAndProductIds(needs.keySet());

        needs.values().forEach(need -> {
            Long productId = need.product().getProductId();
            WarehouseAllocation allocation = allocationRepository
                    .findByWarehouseWarehouseIdAndProductProductId(
                            warehouse.getWarehouseId(), productId)
                    .orElseGet(() -> allocationRepository.save(
                            new WarehouseAllocation(
                                    warehouse, need.product(), 0, 0)));
            allocation.adjustCurrentStock(sellable.getOrDefault(
                    stockKey(warehouse.getWarehouseId(), productId), 0));
        });
    }

    @Transactional
    public void syncStock(CustomerOrder order, List<OrderItem> items) {
        if (order.getFulfillmentWarehouse() == null
                || items == null || items.isEmpty()) {
            return;
        }
        refreshAllocations(order, needsFromOrderItems(items));
    }

    private Map<Long, ProductNeed> needsFromOrderItems(
            List<OrderItem> items) {
        Map<Long, ProductNeed> needs = new LinkedHashMap<>();
        items.forEach(item -> needs.merge(
                item.getProduct().getProductId(),
                new ProductNeed(
                        item.getProduct(), item.getQuantity(), List.of()),
                (left, right) -> new ProductNeed(
                        left.product(),
                        left.quantity() + right.quantity(),
                        mergeLotRestrictions(
                                left.restrictedLotIds(),
                                right.restrictedLotIds()))));
        return needs;
    }

    private Map<Long, ProductNeed> needsFromShipmentItems(
            List<ShipmentItem> items) {
        Map<Long, ProductNeed> needs = new LinkedHashMap<>();
        items.forEach(item -> needs.merge(
                item.getProduct().getProductId(),
                new ProductNeed(
                        item.getProduct(),
                        item.getPickedQuantity(),
                        List.of()),
                (left, right) -> new ProductNeed(
                        left.product(),
                        left.quantity() + right.quantity(),
                        mergeLotRestrictions(
                                left.restrictedLotIds(),
                                right.restrictedLotIds()))));
        return needs;
    }

    private Map<Long, ProductNeed> productNeeds(List<ProductRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new IllegalArgumentException("창고를 배정할 주문 상품이 없습니다.");
        }
        Map<Long, ProductNeed> needs = new LinkedHashMap<>();
        requests.forEach(request -> needs.merge(
                request.product().getProductId(),
                new ProductNeed(
                        request.product(), request.quantity(),
                        request.restrictedLotIds()),
                (left, right) -> new ProductNeed(
                        left.product(), left.quantity() + right.quantity(),
                        mergeLotRestrictions(
                                left.restrictedLotIds(),
                                right.restrictedLotIds()))));
        return needs;
    }

    private List<Long> mergeLotRestrictions(
            List<Long> left,
            List<Long> right) {
        if (left.isEmpty()) return right;
        if (right.isEmpty()) return left;
        return java.util.stream.Stream.concat(left.stream(), right.stream())
                .distinct()
                .toList();
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
