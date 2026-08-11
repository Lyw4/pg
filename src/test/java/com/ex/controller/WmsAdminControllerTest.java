package com.ex.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.ex.entity.BinPurpose;
import com.ex.repository.BinInventoryRepository;
import com.ex.repository.ProductLotRepository;
import com.ex.repository.WarehouseBinRepository;
import com.ex.service.WmsOperationsService;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser(username = "admin", roles = "ADMIN")
class WmsAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WmsOperationsService wmsOperationsService;

    @Autowired
    private WarehouseBinRepository binRepository;

    @Autowired
    private BinInventoryRepository binInventoryRepository;

    @Autowired
    private ProductLotRepository lotRepository;

    @Test
    void allWmsViewsRenderInsideAdminDashboard() throws Exception {
        List<String> views = List.of(
                "map",
                "bins",
                "inbound",
                "move",
                "disposal",
                "movements",
                "traceability",
                "sync",
                "directOutbound",
                "scan",
                "labels",
                "network");

        for (String view : views) {
            mockMvc.perform(get("/admin/wms").queryParam("view", view))
                    .andExpect(status().isOk())
                    .andExpect(content().string(
                            org.hamcrest.Matchers.containsString(
                                    "현장 작업")));
        }
    }

    @Test
    void fieldScanRoutesRenderCameraActionsAndQrLabels() throws Exception {
        mockMvc.perform(get("/admin/scan"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString(
                                "카메라 선택")))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString(
                                "샘플 코드로 테스트")));

        mockMvc.perform(get("/admin/scan/labels"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString(
                                "QR 라벨")))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString(
                                "data:image/svg+xml;base64,")))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString(
                                "품목 라벨")));
    }

    @Test
    void warehouseMapCombinesNationalNetworkAndInteractiveFloorPlan()
            throws Exception {
        var warehouses = wmsOperationsService.warehouses();
        var selected = warehouses.getLast();

        mockMvc.perform(get("/admin/warehouse-map")
                        .queryParam(
                                "centerId",
                                selected.getWarehouseId().toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString(
                                "전국 거점 지도")))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString(
                                selected.getName() + " 2D 도면")))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString(
                                "구역별 적재 현황")))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString(
                                "구역 상세")))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString(
                                "NJ-PL-01")))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString(
                                "ff-facility-inspection")));

        var summary = wmsOperationsService.warehouseMap(
                selected.getWarehouseId());
        assertEquals(
                wmsOperationsService.bins(selected.getWarehouseId())
                        .stream()
                        .filter(bin -> bin.getPurpose().isPhysicalSpace())
                        .count(),
                summary.binItems().size());
        assertTrue(summary.storageCapacity() > 0);
    }

    @Test
    void lotAndProductCodesReturnDetailedScanResults() {
        var lot = lotRepository.findAllByOrderByExpirationDateAsc()
                .stream()
                .findFirst()
                .orElseThrow();
        var lotResult = wmsOperationsService.scan(
                "LOT:" + lot.getLotNo());
        var productCode = wmsOperationsService.productCode(
                lot.getProduct());
        var productResult = wmsOperationsService.scan(
                "PRODUCT:" + productCode);

        assertEquals("LOT", lotResult.type());
        assertEquals(lot.getLotId(), lotResult.lotId());
        assertTrue(lotResult.found());
        assertEquals("PRODUCT", productResult.type());
        assertEquals(lot.getProduct().getProductId(),
                productResult.productId());
        assertEquals(productCode, productResult.productCode());
    }

    @Test
    void initializerCreatesTeammateFloorPlanAndLocatesAllLotStock() {
        assertEquals(51, wmsOperationsService.bins().size());
        var firstWarehouse = wmsOperationsService.warehouses().getFirst();
        var firstCenterBins = wmsOperationsService.bins(
                firstWarehouse.getWarehouseId());
        assertEquals(12, firstCenterBins.size());
        assertTrue(firstCenterBins.stream().anyMatch(bin ->
                "YS-PL-01".equals(bin.getBinCode())
                        && "01".equals(bin.getRack())
                        && Integer.valueOf(1).equals(bin.getBinLevel())
                        && bin.getPosX() == 6
                        && bin.getPosY() == 1));
        assertTrue(wmsOperationsService.warehouseMap(
                        firstWarehouse.getWarehouseId())
                .binItems().stream()
                .filter(item -> item.bin().isActive())
                .filter(item -> item.bin().getPurpose()
                        == BinPurpose.STORAGE)
                .allMatch(item -> item.quantity() > 0
                        // Usage rate includes the configured vertical capacity.
                        && item.usageRate() > 0));
        int lotStock = lotRepository.findAll().stream()
                .mapToInt(lot -> lot.getLotQuantity())
                .sum();
        int locatedStock = binInventoryRepository.findAll().stream()
                .mapToInt(inventory -> inventory.getQuantity())
                .sum();
        assertEquals(lotStock, locatedStock);
        assertTrue(wmsOperationsService.consistencyRows().stream()
                .allMatch(WmsOperationsService.ConsistencyRow::consistent));
    }

    @Test
    void warehouseBinDetailApiReturnsLocationAndLotRows() throws Exception {
        var inventory = binInventoryRepository
                .findAllByOrderByBinBinCodeAsc()
                .stream()
                .filter(item -> item.getQuantity() > 0)
                .findFirst()
                .orElseThrow();

        mockMvc.perform(get("/api/admin/warehouse-map/bins/{binId}",
                        inventory.getBin().getBinId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bin.binCode")
                        .value(inventory.getBin().getBinCode()))
                .andExpect(jsonPath("$.bin.locationLabel").isNotEmpty())
                .andExpect(jsonPath("$.inventories[0].productName")
                        .isNotEmpty())
                .andExpect(jsonPath("$.inventories[0].lotNo")
                        .isNotEmpty());
    }

    @Test
    @Transactional
    void stockCanMoveBetweenBinsWithoutChangingLotTotal() {
        var sourceInventory = binInventoryRepository
                .findAllByOrderByBinBinCodeAsc()
                .stream()
                .filter(inventory -> inventory.getQuantity() > 0)
                .findFirst()
                .orElseThrow();
        var source = sourceInventory.getBin();
        var destination = binRepository
                .findByWarehouseWarehouseIdAndActiveTrueOrderByBinCodeAsc(
                        source.getWarehouse().getWarehouseId())
                .stream()
                .filter(bin -> bin.getPurpose() == BinPurpose.STORAGE)
                .filter(bin -> !bin.getBinId().equals(source.getBinId()))
                .findFirst()
                .orElseThrow();
        int lotQuantity = sourceInventory.getLot().getLotQuantity();

        wmsOperationsService.move(
                sourceInventory.getLot().getLotId(),
                source.getBinId(),
                destination.getBinId(),
                1,
                "구역 이동 테스트",
                "테스트 관리자");

        int located = binInventoryRepository
                .findByLotLotIdAndQuantityGreaterThanOrderByBinBinCodeAsc(
                        sourceInventory.getLot().getLotId(), 0)
                .stream()
                .mapToInt(inventory -> inventory.getQuantity())
                .sum();
        assertEquals(lotQuantity, located);
    }

    @Test
    @Transactional
    void inboundUpdatesProductLotAndBinTogether() {
        var lot = lotRepository.findAllByOrderByExpirationDateAsc()
                .stream()
                .findFirst()
                .orElseThrow();
        var receivingBin = wmsOperationsService.bins().stream()
                .filter(bin -> bin.getPurpose() == BinPurpose.RECEIVING)
                .findFirst()
                .orElseThrow();
        int productBefore = lot.getProduct().getTotalStock();
        int lotBefore = lot.getLotQuantity();

        wmsOperationsService.receive(
                lot.getLotId(),
                null,
                null,
                null,
                null,
                3,
                receivingBin.getBinId(),
                "추가 입고 테스트",
                "테스트 관리자");

        assertEquals(lotBefore + 3, lot.getLotQuantity());
        assertEquals(productBefore + 3, lot.getProduct().getTotalStock());
        assertTrue(wmsOperationsService.consistencyRows().stream()
                .filter(row -> row.product().getProductId()
                        .equals(lot.getProduct().getProductId()))
                .allMatch(WmsOperationsService.ConsistencyRow::consistent));
    }

    @Test
    @Transactional
    void scanOutboundUpdatesLotProductAndLocationTogether() {
        var inventory = binInventoryRepository
                .findAllByOrderByBinBinCodeAsc()
                .stream()
                .filter(item -> item.getQuantity() > 0)
                .findFirst()
                .orElseThrow();
        int locationBefore = inventory.getQuantity();
        int lotBefore = inventory.getLot().getLotQuantity();
        int productBefore = inventory.getLot().getProduct().getTotalStock();

        wmsOperationsService.ship(
                inventory.getLot().getLotId(),
                inventory.getBin().getBinId(),
                1,
                "스캔 출고 테스트",
                "테스트 관리자");

        assertEquals(locationBefore - 1, inventory.getQuantity());
        assertEquals(lotBefore - 1, inventory.getLot().getLotQuantity());
        assertEquals(productBefore - 1,
                inventory.getLot().getProduct().getTotalStock());
    }

    @Test
    @Transactional
    void directOutboundAllocatesNearestExpirationStockFirst() {
        var productView = wmsOperationsService.directOutboundProducts()
                .stream()
                .filter(item -> item.availableQuantity() >= 2)
                .findFirst()
                .orElseThrow();
        var before = binInventoryRepository
                .findByLotProductProductIdAndQuantityGreaterThanOrderByBinBinCodeAsc(
                        productView.product().getProductId(),
                        0)
                .stream()
                .filter(item -> !item.getLot().getExpirationDate()
                        .isBefore(java.time.LocalDate.now()))
                .mapToInt(item -> item.getQuantity())
                .sum();

        var allocations = wmsOperationsService.shipProductFefo(
                productView.product().getProductId(),
                2,
                "직접 출고 테스트",
                "test-admin");

        assertEquals(2, allocations.stream()
                .mapToInt(WmsOperationsService.OutboundAllocation::quantity)
                .sum());
        assertEquals(productView.nearestExpirationDate(),
                allocations.getFirst().expirationDate());
        var after = binInventoryRepository
                .findByLotProductProductIdAndQuantityGreaterThanOrderByBinBinCodeAsc(
                        productView.product().getProductId(),
                        0)
                .stream()
                .filter(item -> !item.getLot().getExpirationDate()
                        .isBefore(java.time.LocalDate.now()))
                .mapToInt(item -> item.getQuantity())
                .sum();
        assertEquals(before - 2, after);
    }
}
