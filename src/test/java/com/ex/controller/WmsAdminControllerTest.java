package com.ex.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
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
import com.ex.entity.WarehouseBin;
import com.ex.repository.BinInventoryRepository;
import com.ex.repository.ProductLotRepository;
import com.ex.repository.WarehouseBinRepository;
import com.ex.repository.WarehouseAllocationRepository;
import com.ex.service.WmsOperationsService;
import com.ex.service.SellableStockQuery;

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

    @Autowired
    private WarehouseAllocationRepository allocationRepository;

    @Autowired
    private SellableStockQuery sellableStockQuery;

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
    void allWarehouseBinsArePagedButSingleWarehouseShowsAllBins()
            throws Exception {
        int totalBins = (int) wmsOperationsService.bins().stream()
                .filter(WarehouseBin::isActive)
                .count();
        int expectedPageCount = (totalBins + 9) / 10;

        mockMvc.perform(get("/admin/wms")
                        .queryParam("view", "bins"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("binTotalCount", totalBins))
                .andExpect(model().attribute("binPage", 0))
                .andExpect(model().attribute("binPageCount", expectedPageCount))
                .andExpect(model().attribute(
                        "displayBins",
                        org.hamcrest.Matchers.hasSize(Math.min(10, totalBins))))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString(
                                "전체 창고 구역 페이지 이동")));

        mockMvc.perform(get("/admin/wms")
                        .queryParam("view", "bins")
                        .queryParam("binPage", "1"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("binPage", 1))
                .andExpect(model().attribute(
                        "displayBins",
                        org.hamcrest.Matchers.hasSize(
                                Math.min(10, Math.max(0, totalBins - 10)))));

        var warehouse = wmsOperationsService.warehouses().getFirst();
        int warehouseBinCount = (int) wmsOperationsService
                .bins(warehouse.getWarehouseId()).stream()
                .filter(WarehouseBin::isActive)
                .count();
        mockMvc.perform(get("/admin/wms")
                        .queryParam("view", "bins")
                        .queryParam("binWarehouseId",
                                warehouse.getWarehouseId().toString())
                        .queryParam("binPage", "99"))
                .andExpect(status().isOk())
                .andExpect(model().attribute("binPage", 0))
                .andExpect(model().attribute("binPageCount", 1))
                .andExpect(model().attribute(
                        "displayBins",
                        org.hamcrest.Matchers.hasSize(warehouseBinCount)))
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString(
                                        "전체 창고 구역 페이지 이동"))));
    }

    @Test
    void moveViewPagesLocationInventoryByTenItems() throws Exception {
        mockMvc.perform(get("/admin/wms")
                        .queryParam("view", "move"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString(
                                "페이지당 10건")))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString(
                                "id=\"wmsLocationInventoryPagination\"")))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString(
                                "data-page-size=\"10\"")));
    }

    @Test
    void fieldScanRoutesRenderCameraActionsAndQrLabels() throws Exception {
        mockMvc.perform(get("/admin/scan"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString(
                                "QR 스캔")))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString(
                                "카메라 선택")))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString(
                                "샘플 코드로 테스트")))
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString(
                                        "바코드"))));

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
                        .filter(WarehouseBin::isActive)
                        .count(),
                summary.binItems().size());
        assertTrue(summary.storageCapacity() > 0);
    }

    @Test
    void lotAndProductQrCodesReturnDetailedScanResults() {
        var lot = lotRepository.findAllByOrderByExpirationDateAsc()
                .stream()
                .findFirst()
                .orElseThrow();
        var lotResult = wmsOperationsService.lookupScanInput(
                "LOT:" + lot.getLotNo());
        var productCode = wmsOperationsService.productCode(
                lot.getProduct());
        var productResult = wmsOperationsService.lookupScanInput(
                "PRODUCT:" + productCode);
        var productNameResult = wmsOperationsService.lookupScanInput(
                lot.getProduct().getName());
        var unsupportedResult = wmsOperationsService.lookupScanInput(
                lot.getLotNo());

        assertEquals("LOT", lotResult.type());
        assertEquals(lot.getLotId(), lotResult.lotId());
        assertTrue(lotResult.found());
        assertEquals(lot.getProduct().getName(),
                lotResult.product().getName());
        assertEquals("LOT 재고 " + lot.getLotQuantity() + "포대",
                lotResult.detail());
        assertEquals("PRODUCT", productResult.type());
        assertEquals(lot.getProduct().getProductId(),
                productResult.productId());
        assertEquals(productCode, productResult.productCode());
        assertEquals("PRODUCT", productNameResult.type());
        assertEquals(lot.getProduct().getProductId(),
                productNameResult.productId());
        assertEquals("UNKNOWN", unsupportedResult.type());
    }

    @Test
    void initializerCreatesTeammateFloorPlanAndLocatesAllLotStock() {
        assertEquals(57, wmsOperationsService.bins().size());
        var firstWarehouse = wmsOperationsService.warehouses().getFirst();
        var firstCenterBins = wmsOperationsService.bins(
                firstWarehouse.getWarehouseId());
        assertEquals(13, firstCenterBins.size());
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
    void warehouseZonesCoverDemandAndColdKeepsGenerousSupplementCapacity() {
        wmsOperationsService.warehouses().forEach(warehouse -> {
            var allocations = allocationRepository
                    .findByWarehouseWarehouseId(warehouse.getWarehouseId());
            for (String zone : List.of("CT", "PG", "PL", "COLD")) {
                int monthlyDemand = allocations.stream()
                        .filter(item -> zone.equals(zoneFor(
                                item.getProduct().getAnimalType())))
                        .mapToInt(item -> item.getMonthlyPlannedQuantity())
                        .sum();
                int recommendedStock = allocations.stream()
                        .filter(item -> zone.equals(zoneFor(
                                item.getProduct().getAnimalType())))
                        .mapToInt(item -> item.getTargetStockQuantity())
                        .sum();
                if (monthlyDemand <= 0) continue;
                int capacity = wmsOperationsService
                        .bins(warehouse.getWarehouseId())
                        .stream()
                        .filter(bin -> bin.isActive())
                        .filter(bin -> bin.getPurpose() == BinPurpose.STORAGE)
                        .filter(bin -> zone.equalsIgnoreCase(bin.getZone()))
                        .mapToInt(bin -> bin.getEffectiveMaxCapacity())
                        .sum();
                int currentQuantity = wmsOperationsService
                        .bins(warehouse.getWarehouseId())
                        .stream()
                        .filter(bin -> bin.getPurpose() == BinPurpose.STORAGE)
                        .filter(bin -> zone.equalsIgnoreCase(bin.getZone()))
                        .flatMap(bin -> binInventoryRepository
                                .findByBinBinId(bin.getBinId()).stream())
                        .mapToInt(item -> item.getQuantity())
                        .sum();
                int shortage = allocations.stream()
                        .filter(item -> zone.equals(zoneFor(
                                item.getProduct().getAnimalType())))
                        .mapToInt(item -> Math.max(0,
                                item.getTargetStockQuantity()
                                        - sellableStockQuery.sellableAtWarehouse(
                                                warehouse.getWarehouseId(),
                                                item.getProduct().getProductId())))
                        .sum();
                int demandPercent = "COLD".equals(zone) ? 300 : 130;
                int stockPercent = "COLD".equals(zone) ? 200 : 130;
                int required = Math.max(
                        (monthlyDemand * demandPercent + 99) / 100,
                        (recommendedStock * stockPercent + 99) / 100);
                if ("COLD".equals(zone)) {
                    required = Math.max(required,
                            ((currentQuantity + shortage) * 150 + 99) / 100);
                }
                assertTrue(capacity >= required,
                        warehouse.getName() + " " + zone
                                + " 계획 용량 부족: " + capacity
                                + " / " + required);
            }
        });
    }

    @Test
    @Transactional
    void capacityButtonExpandsExistingStorageBins() throws Exception {
        var allocation = allocationRepository
                .findAllByOrderByWarehouseDisplayOrderAscProductAnimalTypeAscProductNameAsc()
                .getFirst();

        mockMvc.perform(post("/admin/wms/inbound/capacity")
                        .with(csrf())
                        .param("warehouseId", allocation.getWarehouse().getWarehouseId().toString())
                        .param("productId", allocation.getProduct().getProductId().toString())
                        .param("quantity", "500"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/wms?view=inbound"))
                .andExpect(flash().attributeExists("wmsMessage"));

        String zone = zoneFor(allocation.getProduct().getAnimalType());
        int capacity = wmsOperationsService.bins(allocation.getWarehouse().getWarehouseId())
                .stream()
                .filter(bin -> bin.isActive() && bin.getPurpose() == BinPurpose.STORAGE)
                .filter(bin -> zone.equalsIgnoreCase(bin.getZone()))
                .mapToInt(bin -> bin.getEffectiveMaxCapacity())
                .sum();
        int current = wmsOperationsService.bins(allocation.getWarehouse().getWarehouseId())
                .stream()
                .filter(bin -> bin.isActive() && bin.getPurpose() == BinPurpose.STORAGE)
                .filter(bin -> zone.equalsIgnoreCase(bin.getZone()))
                .flatMap(bin -> binInventoryRepository.findByBinBinId(bin.getBinId()).stream())
                .mapToInt(item -> item.getQuantity()).sum();
        assertTrue(capacity >= current + 500);
    }

    private String zoneFor(String animalType) {
        return switch (animalType == null ? "" : animalType.trim()) {
            case "소" -> "CT";
            case "돼지" -> "PG";
            case "조류", "조류(닭/오리)" -> "PL";
            default -> "COLD";
        };
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
                .andExpect(jsonPath("$.bin.deletable").value(false))
                .andExpect(jsonPath("$.bin.deleteBlockedReason").isNotEmpty())
                .andExpect(jsonPath("$.inventories[0].productName")
                        .isNotEmpty())
                .andExpect(jsonPath("$.inventories[0].lotNo")
                        .isNotEmpty());
    }

    @Test
    @Transactional
    void emptyBinCanBeDeletedFromWarehouseMapAndDisappearsFromActiveData()
            throws Exception {
        var warehouse = wmsOperationsService.warehouses().getFirst();
        var bin = binRepository.save(new WarehouseBin(
                warehouse,
                "DELETE-MAP-TEST",
                "CT",
                BinPurpose.STORAGE,
                21,
                14,
                1,
                1,
                10,
                "도면 삭제 연동 테스트"));

        mockMvc.perform(post("/admin/wms/bins/{binId}/delete", bin.getBinId())
                        .with(csrf())
                        .param("warehouseId", warehouse.getWarehouseId().toString())
                        .param("returnView", "map"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/admin/warehouse-map?centerId="
                                + warehouse.getWarehouseId()))
                .andExpect(flash().attributeExists("wmsMessage"));

        assertTrue(!binRepository.findById(bin.getBinId())
                .orElseThrow()
                .isActive());
    }

    @Test
    @Transactional
    void stockCanMoveBetweenBinsWithoutChangingLotTotal() {
        var sourceInventory = binInventoryRepository
                .findAllByOrderByBinBinCodeAsc()
                .stream()
                .filter(inventory -> inventory.getQuantity() > 0)
                .filter(inventory -> binRepository
                        .findByWarehouseWarehouseIdAndActiveTrueOrderByBinCodeAsc(
                                inventory.getBin().getWarehouse().getWarehouseId())
                        .stream()
                        .anyMatch(bin -> bin.getPurpose() == BinPurpose.STORAGE
                                && !bin.getBinId().equals(
                                        inventory.getBin().getBinId())
                                && java.util.Objects.equals(
                                        bin.getZone(), inventory.getBin().getZone())))
                .findFirst()
                .orElseThrow();
        var source = sourceInventory.getBin();
        var destination = binRepository
                .findByWarehouseWarehouseIdAndActiveTrueOrderByBinCodeAsc(
                        source.getWarehouse().getWarehouseId())
                .stream()
                .filter(bin -> bin.getPurpose() == BinPurpose.STORAGE)
                .filter(bin -> !bin.getBinId().equals(source.getBinId()))
                .filter(bin -> java.util.Objects.equals(
                        bin.getZone(), source.getZone()))
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
    void cattleFeedCannotBeStoredInColdZone() {
        var cattleLot = lotRepository.findAllByOrderByExpirationDateAsc()
                .stream()
                .filter(lot -> "소".equals(lot.getProduct().getAnimalType()))
                .findFirst()
                .orElseThrow();
        var coldBin = wmsOperationsService.bins().stream()
                .filter(bin -> bin.getPurpose() == BinPurpose.STORAGE)
                .filter(bin -> "COLD".equals(bin.getZone()))
                .findFirst()
                .orElseThrow();

        assertThrows(IllegalArgumentException.class, () ->
                wmsOperationsService.receive(
                        cattleLot.getLotId(), null, null, null, null,
                        1, coldBin.getBinId(), "축종 구역 검증", "테스트 관리자"));
    }

    @Test
    @Transactional
    void selectedLowStockAllocationIsFilledToRecommendedStock() {
        var allocation = allocationRepository
                .findAllByOrderByWarehouseDisplayOrderAscProductAnimalTypeAscProductNameAsc()
                .stream()
                .filter(item -> lotRepository
                        .findByProductProductIdOrderByExpirationDateAsc(
                                item.getProduct().getProductId())
                        .stream()
                        .anyMatch(lot -> lot.getExpirationDate() == null
                                || !lot.getExpirationDate().isBefore(
                                        java.time.LocalDate.now().plusDays(
                                                SellableStockQuery.MINIMUM_SELLABLE_DAYS))))
                .findFirst()
                .orElseThrow();
        int current = sellableStockQuery.sellableAtWarehouse(
                allocation.getWarehouse().getWarehouseId(),
                allocation.getProduct().getProductId());
        int replenishment = 3;
        allocation.changePlan(
                allocation.getMonthlyPlannedQuantity(),
                current + replenishment);

        var result = wmsOperationsService.receiveAllocationReplenishment(
                allocation.getAllocationId(), "테스트 관리자");

        assertEquals(replenishment, result.quantity());
        assertEquals(current + replenishment,
                sellableStockQuery.sellableAtWarehouse(
                allocation.getWarehouse().getWarehouseId(),
                allocation.getProduct().getProductId()));
        assertTrue(!result.binCodes().isEmpty());
    }

    @Test
    @Transactional
    void automaticallyCreatedBinAvoidsExistingAreas() {
        var allocation = allocationRepository
                .findAllByOrderByWarehouseDisplayOrderAscProductAnimalTypeAscProductNameAsc()
                .stream()
                .findFirst()
                .orElseThrow();
        var mapBefore = wmsOperationsService.warehouseMap(
                allocation.getWarehouse().getWarehouseId());

        WarehouseBin created = wmsOperationsService.createAutomaticProductBin(
                allocation.getWarehouse().getWarehouseId(),
                allocation.getProduct().getProductId(),
                500,
                "자동 정렬 테스트");

        assertTrue(mapBefore.binItems().stream().noneMatch(item ->
                created.getPosX() < item.bin().getPosX()
                        + item.bin().getPosWidth()
                        && created.getPosX() + created.getPosWidth()
                                > item.bin().getPosX()
                        && created.getPosY() < item.bin().getPosY()
                                + item.bin().getPosHeight()
                        && created.getPosY() + created.getPosHeight()
                                > item.bin().getPosY()));
        assertTrue(mapBefore.facilities().stream().noneMatch(facility ->
                created.getPosX() < facility.posX() + facility.posWidth()
                        && created.getPosX() + created.getPosWidth()
                                > facility.posX()
                        && created.getPosY() < facility.posY()
                                + facility.posHeight()
                        && created.getPosY() + created.getPosHeight()
                                > facility.posY()));
    }

    @Test
    @Transactional
    void userCanChooseAnEmptyPositionForAutomaticBin() {
        var allocation = allocationRepository
                .findAllByOrderByWarehouseDisplayOrderAscProductAnimalTypeAscProductNameAsc()
                .stream()
                .findFirst()
                .orElseThrow();
        var map = wmsOperationsService.warehouseMap(
                allocation.getWarehouse().getWarehouseId());
        int selectedX = -1;
        int selectedY = -1;
        findPosition:
        for (int y = 1; y <= 14; y++) {
            for (int x = 1; x <= 26; x++) {
                final int candidateX = x;
                final int candidateY = y;
                boolean occupied = map.binItems().stream().anyMatch(item ->
                        candidateX >= item.bin().getPosX()
                                && candidateX < item.bin().getPosX()
                                        + item.bin().getPosWidth()
                                && candidateY >= item.bin().getPosY()
                                && candidateY < item.bin().getPosY()
                                        + item.bin().getPosHeight())
                        || map.facilities().stream().anyMatch(facility ->
                                candidateX >= facility.posX()
                                        && candidateX < facility.posX()
                                                + facility.posWidth()
                                        && candidateY >= facility.posY()
                                        && candidateY < facility.posY()
                                                + facility.posHeight());
                if (!occupied) {
                    selectedX = x;
                    selectedY = y;
                    break findPosition;
                }
            }
        }
        assertTrue(selectedX > 0 && selectedY > 0);
        final int chosenX = selectedX;
        final int chosenY = selectedY;

        WarehouseBin created = wmsOperationsService.createAutomaticProductBin(
                allocation.getWarehouse().getWarehouseId(),
                allocation.getProduct().getProductId(),
                2000,
                chosenX,
                chosenY,
                1,
                1,
                "사용자 위치 선택 테스트");

        assertEquals(chosenX, created.getPosX());
        assertEquals(chosenY, created.getPosY());
        assertEquals(1, created.getPosWidth());
        assertEquals(1, created.getPosHeight());
        assertTrue(created.getEffectiveMaxCapacity() >= 2000);
        assertThrows(IllegalArgumentException.class, () ->
                wmsOperationsService.createAutomaticProductBin(
                        allocation.getWarehouse().getWarehouseId(),
                        allocation.getProduct().getProductId(),
                        2000,
                        chosenX,
                        chosenY,
                        1,
                        1,
                        "겹침 방지 테스트"));
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
