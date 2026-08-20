package com.ex.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ex.entity.FarmCustomer;
import com.ex.entity.InboundReplenishmentRequest;
import com.ex.entity.InboundReplenishmentRequest.Status;
import com.ex.entity.Product;
import com.ex.entity.Warehouse;
import com.ex.entity.WarehouseAllocation;
import com.ex.repository.InboundReplenishmentRequestRepository;
import com.ex.repository.WarehouseAllocationRepository;

class InboundReplenishmentRequestServiceTest {

    private InboundReplenishmentRequestRepository requestRepository;
    private WarehouseAllocationRepository allocationRepository;
    private WarehouseCapacityPlanningService capacityPlanningService;
    private InventoryService inventoryService;
    private SellableStockQuery sellableStockQuery;
    private InboundReplenishmentRequestService service;
    private Warehouse warehouse;
    private Product product;
    private FarmCustomer farm;

    @BeforeEach
    void setUp() {
        requestRepository = mock(InboundReplenishmentRequestRepository.class);
        allocationRepository = mock(WarehouseAllocationRepository.class);
        capacityPlanningService = mock(WarehouseCapacityPlanningService.class);
        inventoryService = mock(InventoryService.class);
        sellableStockQuery = mock(SellableStockQuery.class);
        service = new InboundReplenishmentRequestService(
                requestRepository,
                allocationRepository,
                capacityPlanningService,
                inventoryService,
                sellableStockQuery);
        warehouse = mock(Warehouse.class);
        product = mock(Product.class);
        farm = mock(FarmCustomer.class);
        when(warehouse.getWarehouseId()).thenReturn(1L);
        when(warehouse.getName()).thenReturn("예산 고덕 창고");
        when(product.getProductId()).thenReturn(10L);
        when(product.getName()).thenReturn("한우 성장 플러스");
        when(product.getEffectiveShelfLifeMonths()).thenReturn(6);
    }

    @Test
    void createsPendingRequestForGapToWarehouseTarget() {
        WarehouseAllocation allocation = mock(WarehouseAllocation.class);
        when(allocation.getTargetStockQuantity()).thenReturn(120);
        when(sellableStockQuery.sellableAtWarehouse(1L, 10L)).thenReturn(70);
        when(allocationRepository.findByWarehouseWarehouseIdAndProductProductId(1L, 10L))
                .thenReturn(Optional.of(allocation));
        when(requestRepository.findPendingForUpdate(1L, 10L, Status.PENDING))
                .thenReturn(Optional.empty());
        when(requestRepository.save(any(InboundReplenishmentRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InboundReplenishmentRequest request = service.request(
                warehouse, product, farm, 90, LocalDate.of(2026, 8, 20),
                "정기 납품 후 부족");

        assertEquals(Status.PENDING, request.getStatus());
        assertEquals(50, request.getRequestedQuantity());
        assertEquals("정기 납품 후 부족", request.getReason());
    }

    @Test
    void approvalCreatesLotAndInventoryOnlyAtApprovalTime() {
        InboundReplenishmentRequest request = new InboundReplenishmentRequest(
                warehouse, product, farm, 50, LocalDate.of(2026, 8, 20),
                "정기 납품 후 부족");
        WarehouseAllocation allocation = mock(WarehouseAllocation.class);
        when(allocation.getTargetStockQuantity()).thenReturn(120);
        when(requestRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(request));
        when(allocationRepository.findByWarehouseWarehouseIdAndProductProductId(1L, 10L))
                .thenReturn(Optional.of(allocation));
        when(sellableStockQuery.sellableAtWarehouse(1L, 10L)).thenReturn(70);
        when(inventoryService.createAutomaticLotNo(eq(10L), any(LocalDate.class)))
                .thenReturn("LOT-AUTO-001");

        String message = service.approve(7L, "admin");

        assertEquals(Status.APPROVED, request.getStatus());
        assertEquals("admin", request.getProcessedBy());
        verify(capacityPlanningService).ensureProductInboundCapacity(1L, product, 50);
        verify(inventoryService).receive(
                eq(10L), eq("LOT-AUTO-001"), any(LocalDate.class),
                any(LocalDate.class), eq(50),
                eq("정기배송 부족분 승인 입고 · 요청 #7"), eq(warehouse));
        assertEquals("예산 고덕 창고 · 한우 성장 플러스 50포를 입고했습니다.", message);
    }
}
