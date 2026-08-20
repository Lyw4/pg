package com.ex.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ex.entity.FarmCustomer;
import com.ex.entity.InboundReplenishmentRequest;
import com.ex.entity.InboundReplenishmentRequest.Status;
import com.ex.entity.Product;
import com.ex.entity.Warehouse;
import com.ex.repository.InboundReplenishmentRequestRepository;
import com.ex.repository.WarehouseAllocationRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InboundReplenishmentRequestService {

    private final InboundReplenishmentRequestRepository requestRepository;
    private final WarehouseAllocationRepository allocationRepository;
    private final WarehouseCapacityPlanningService capacityPlanningService;
    private final InventoryService inventoryService;
    private final SellableStockQuery sellableStockQuery;

    public List<InboundReplenishmentRequest> requests() {
        return requestRepository.findAllByOrderByRequestedAtDesc();
    }

    public long pendingCount() {
        return requests().stream()
                .filter(request -> request.getStatus() == Status.PENDING)
                .count();
    }

    public boolean hasPending(Long warehouseId, Long productId) {
        return requestRepository
                .existsByWarehouseWarehouseIdAndProductProductIdAndStatus(
                        warehouseId, productId, Status.PENDING);
    }

    /** 권장재고 또는 당일 납품 필요량보다 부족한 수량을 승인 대기 요청으로 합칩니다. */
    @Transactional
    public InboundReplenishmentRequest request(
            Warehouse warehouse,
            Product product,
            FarmCustomer farmCustomer,
            int minimumRequiredQuantity,
            LocalDate referenceDate,
            String reason) {
        int sellable = sellableStockQuery.sellableAtWarehouse(
                warehouse.getWarehouseId(), product.getProductId());
        int target = allocationRepository
                .findByWarehouseWarehouseIdAndProductProductId(
                        warehouse.getWarehouseId(), product.getProductId())
                .map(allocation -> allocation.getTargetStockQuantity())
                .orElse(0);
        int shortage = Math.max(
                Math.max(0, minimumRequiredQuantity - sellable),
                Math.max(0, target - sellable));
        if (shortage <= 0) return null;

        var pending = requestRepository.findPendingForUpdate(
                warehouse.getWarehouseId(), product.getProductId(), Status.PENDING);
        if (pending.isPresent()) {
            pending.get().refresh(shortage, farmCustomer, reason);
            return pending.get();
        }
        return requestRepository.save(new InboundReplenishmentRequest(
                warehouse, product, farmCustomer, shortage,
                referenceDate == null ? LocalDate.now() : referenceDate,
                reason));
    }

    @Transactional
    public String approve(Long requestId, String operator) {
        InboundReplenishmentRequest request = findPending(requestId);
        Product product = request.getProduct();
        Warehouse warehouse = request.getWarehouse();
        int sellable = sellableStockQuery.sellableAtWarehouse(
                warehouse.getWarehouseId(), product.getProductId());
        int currentShortage = allocationRepository
                .findByWarehouseWarehouseIdAndProductProductId(
                        warehouse.getWarehouseId(), product.getProductId())
                .map(allocation -> Math.max(
                        0, allocation.getTargetStockQuantity() - sellable))
                .orElse(request.getRequestedQuantity());
        int quantity = Math.min(request.getRequestedQuantity(), currentShortage);
        if (quantity <= 0) {
            throw new IllegalStateException(
                    "현재 재고가 이미 권장 수량을 충족합니다. 요청을 반려해 주세요.");
        }
        LocalDate manufacturedDate = LocalDate.now();

        capacityPlanningService.ensureProductInboundCapacity(
                warehouse.getWarehouseId(), product, quantity);
        inventoryService.receive(
                product.getProductId(),
                inventoryService.createAutomaticLotNo(
                        product.getProductId(), manufacturedDate),
                manufacturedDate,
                manufacturedDate.plusMonths(product.getEffectiveShelfLifeMonths()),
                quantity,
                "정기배송 부족분 승인 입고 · 요청 #" + requestId,
                warehouse);
        request.approve(operator);
        return warehouse.getName() + " · " + product.getName() + " "
                + quantity + "포를 입고했습니다.";
    }

    @Transactional
    public String reject(Long requestId, String operator) {
        InboundReplenishmentRequest request = findPending(requestId);
        request.reject(operator);
        return request.getWarehouse().getName() + " · "
                + request.getProduct().getName() + " 입고 요청을 반려했습니다.";
    }

    private InboundReplenishmentRequest findPending(Long requestId) {
        InboundReplenishmentRequest request = requestRepository
                .findByIdForUpdate(requestId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "입고 요청을 찾을 수 없습니다."));
        if (request.getStatus() != Status.PENDING) {
            throw new IllegalStateException("이미 처리된 입고 요청입니다.");
        }
        return request;
    }
}
