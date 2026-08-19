package com.ex.controller;

import java.time.LocalDate;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ex.entity.BinPurpose;
import com.ex.entity.DisposalReason;
import com.ex.service.WmsOperationsService;
import com.ex.service.DemandPlanService;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class WmsAdminController {

    private static final int BIN_PAGE_SIZE = 10;
    private static final int TRACE_PAGE_SIZE = 10;

    private static final Set<String> VIEWS = Set.of(
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
            "labels");

    private static final Map<String, String> TITLES = Map.ofEntries(
            Map.entry("directOutbound", "직접 출고"),
            Map.entry("map", "창고 2D 도면"),
            Map.entry("bins", "창고 구역 관리"),
            Map.entry("inbound", "WMS 입고 등록"),
            Map.entry("move", "구역·센터 이관"),
            Map.entry("disposal", "WMS 재고 폐기"),
            Map.entry("movements", "입출고·이동 이력"),
            Map.entry("traceability", "LOT 이력 추적"),
            Map.entry("sync", "재고 정합성 점검"),
            Map.entry("scan", "바코드 스캔"),
            Map.entry("labels", "QR·바코드 라벨 출력"));

    private final WmsOperationsService wmsOperationsService;
    private final DemandPlanService demandPlanService;

    @GetMapping("/admin/wms")
    public String page(
            @RequestParam(name = "view", defaultValue = "map") String view,
            @RequestParam(name = "warehouseId", required = false) Long warehouseId,
            @RequestParam(name = "binWarehouseId", required = false) Long binWarehouseId,
            @RequestParam(name = "binPage", defaultValue = "0") int binPage,
            @RequestParam(name = "lotId", required = false) Long lotId,
            @RequestParam(name = "traceWarehouseId", required = false) Long traceWarehouseId,
            @RequestParam(name = "tracePage", defaultValue = "0") int tracePage,
            @RequestParam(name = "scanValue", required = false) String scanValue,
            Model model) {
        String selectedView = VIEWS.contains(view) ? view : "map";
        var warehouses = wmsOperationsService.warehouses();
        Long requestedWarehouseId = warehouseId;
        boolean warehouseExists = requestedWarehouseId != null
                && warehouses.stream().anyMatch(warehouse ->
                        warehouse.getWarehouseId().equals(
                                requestedWarehouseId));
        Long selectedWarehouseId = warehouseExists
                ? requestedWarehouseId
                : (warehouses.isEmpty()
                        ? null
                        : warehouses.getFirst().getWarehouseId());
        var lots = wmsOperationsService.lots();
        var lowStockLots = lots.stream()
                .filter(lot -> lot.getLotQuantity() <= 10 || lot.getProduct().isLowStock())
                .toList();
        var regularLots = lots.stream()
                .filter(lot -> !lowStockLots.contains(lot))
                .toList();
        Long selectedLotId = lotId;
        if (selectedLotId == null && !lots.isEmpty()) {
            selectedLotId = lots.getFirst().getLotId();
        }

        model.addAttribute("menu", "wms");
        model.addAttribute("currentView", selectedView);
        model.addAttribute("pageTitle", TITLES.get(selectedView));
        model.addAttribute(
                "sectionTitle",
                "map".equals(selectedView) ? "기준 정보" : "WMS 운영");
        model.addAttribute("warehouses", warehouses);
        model.addAttribute("selectedWarehouseId", selectedWarehouseId);
        model.addAttribute(
                "bins",
                wmsOperationsService.bins(selectedWarehouseId));
        model.addAttribute(
                "allBins",
                wmsOperationsService.bins());
        Long selectedBinWarehouseId = warehouses.stream()
                .anyMatch(warehouse -> warehouse.getWarehouseId().equals(binWarehouseId))
                ? binWarehouseId : null;
        Long selectedTraceWarehouseId = warehouses.stream()
                .anyMatch(warehouse -> warehouse.getWarehouseId().equals(traceWarehouseId))
                ? traceWarehouseId : null;
        model.addAttribute("selectedBinWarehouseId", selectedBinWarehouseId);
        model.addAttribute("selectedTraceWarehouseId", selectedTraceWarehouseId);
        var filteredBins = selectedBinWarehouseId == null
                ? wmsOperationsService.bins()
                : wmsOperationsService.bins(selectedBinWarehouseId);
        int binTotalCount = filteredBins.size();
        int binPageCount = selectedBinWarehouseId == null
                ? (binTotalCount + BIN_PAGE_SIZE - 1) / BIN_PAGE_SIZE
                : 1;
        int selectedBinPage = selectedBinWarehouseId == null
                ? Math.max(0, binPage)
                : 0;
        if (binPageCount > 0) {
            selectedBinPage = Math.min(selectedBinPage, binPageCount - 1);
        } else {
            selectedBinPage = 0;
        }
        int binFromIndex = selectedBinWarehouseId == null
                ? Math.min(selectedBinPage * BIN_PAGE_SIZE, binTotalCount)
                : 0;
        int binToIndex = selectedBinWarehouseId == null
                ? Math.min(binFromIndex + BIN_PAGE_SIZE, binTotalCount)
                : binTotalCount;
        model.addAttribute(
                "displayBins",
                filteredBins.subList(binFromIndex, binToIndex));
        model.addAttribute("binTotalCount", binTotalCount);
        model.addAttribute("binPage", selectedBinPage);
        model.addAttribute("binPageCount", binPageCount);
        model.addAttribute(
                "selectableBins",
                wmsOperationsService.selectableBins());
        model.addAttribute(
                "inventories",
                wmsOperationsService.inventories(
                        "map".equals(selectedView)
                                ? selectedWarehouseId
                                : null));
        model.addAttribute("products", wmsOperationsService.products());
        model.addAttribute(
                "warehouseAllocations",
                wmsOperationsService.warehouseAllocations());
        model.addAttribute(
                "directOutboundProducts",
                wmsOperationsService.directOutboundProducts());
        model.addAttribute(
                "productCodes",
                wmsOperationsService.products().stream().collect(
                        java.util.stream.Collectors.toMap(
                                product -> product.getProductId(),
                                wmsOperationsService::productCode)));
        model.addAttribute("lots", lots);
        model.addAttribute("lowStockLots", lowStockLots);
        model.addAttribute("regularLots", regularLots);
        Map<Long, Long> lotPreferredBins = new LinkedHashMap<>();
        wmsOperationsService.inventories(null).forEach(inventory ->
                lotPreferredBins.putIfAbsent(inventory.getLot().getLotId(), inventory.getBin().getBinId()));
        model.addAttribute("lotPreferredBins", lotPreferredBins);
        var movements = wmsOperationsService.movements();
        model.addAttribute("movements", movements);
        model.addAttribute(
                "movementBuyerNames",
                wmsOperationsService.movementBuyerNames(movements));
        model.addAttribute("overview", wmsOperationsService.overview());
        model.addAttribute(
                "binQuantities",
                wmsOperationsService.binQuantities());
        model.addAttribute(
                "warehouseQuantities",
                wmsOperationsService.warehousePhysicalQuantities());
        model.addAttribute(
                "consistencyRows",
                wmsOperationsService.consistencyRows());
        model.addAttribute("binPurposes", BinPurpose.values());
        model.addAttribute("disposalReasons", DisposalReason.values());
        model.addAttribute("selectedLotId", selectedLotId);
        model.addAttribute("today", LocalDate.now());
        if ("inbound".equals(selectedView)) {
            var demandPlan = demandPlanService.plan(LocalDate.now());
            model.addAttribute("demandPlan", demandPlan);
            Map<String, Long> demandProductIds = new LinkedHashMap<>();
            demandPlan.warehouses().forEach(warehousePlan ->
                    warehousePlan.rows().stream()
                            .filter(DemandPlanService.CoverageRow::needsAction)
                            .forEach(row -> {
                                try {
                                    demandProductIds.put(
                                            warehousePlan.warehouse().getWarehouseId()
                                                    + "|" + row.animalType(),
                                            wmsOperationsService.recommendedDemandProductId(
                                                    warehousePlan.warehouse().getWarehouseId(),
                                                    row.animalType()));
                                } catch (RuntimeException ignored) {
                                    // 유효 LOT/취급 상품이 없으면 기존 오류 안내 흐름을 사용한다.
                                }
                            }));
            model.addAttribute("demandProductIds", demandProductIds);
        }
        if ("map".equals(selectedView)
                && selectedWarehouseId != null) {
            model.addAttribute(
                    "warehouseMap",
                    wmsOperationsService.warehouseMap(selectedWarehouseId));
        }
        if (!lots.isEmpty()) {
            model.addAttribute("sampleLotNo", lots.getFirst().getLotNo());
        }
        if (!wmsOperationsService.products().isEmpty()) {
            model.addAttribute(
                    "sampleProductCode",
                    wmsOperationsService.productCode(
                            wmsOperationsService.products().getFirst()));
        }

        if ("traceability".equals(selectedView)) {
            var allTraceInventories = wmsOperationsService.inventories(selectedTraceWarehouseId);
            if (lotId == null && !allTraceInventories.isEmpty()) {
                selectedLotId = allTraceInventories.getFirst().getLot().getLotId();
                model.addAttribute("selectedLotId", selectedLotId);
            }
            int traceTotalCount = allTraceInventories.size();
            int tracePageCount = (traceTotalCount + TRACE_PAGE_SIZE - 1) / TRACE_PAGE_SIZE;
            int selectedTracePage = Math.max(0, tracePage);
            if (tracePageCount > 0) {
                selectedTracePage = Math.min(selectedTracePage, tracePageCount - 1);
            } else {
                selectedTracePage = 0;
            }
            int traceFromIndex = Math.min(
                    selectedTracePage * TRACE_PAGE_SIZE,
                    traceTotalCount);
            int traceToIndex = Math.min(
                    traceFromIndex + TRACE_PAGE_SIZE,
                    traceTotalCount);
            model.addAttribute(
                    "traceInventories",
                    allTraceInventories.subList(traceFromIndex, traceToIndex));
            model.addAttribute("traceTotalCount", traceTotalCount);
            model.addAttribute("tracePage", selectedTracePage);
            model.addAttribute("tracePageCount", tracePageCount);
        }
        if ("traceability".equals(selectedView)
                && selectedLotId != null) {
            var traceability = wmsOperationsService.traceability(selectedLotId);
            if (selectedTraceWarehouseId != null) {
                traceability = new WmsOperationsService.Traceability(
                        traceability.lot(),
                        traceability.inventories().stream()
                                .filter(inventory -> inventory.getBin()
                                        .getWarehouse().getWarehouseId()
                                        .equals(selectedTraceWarehouseId))
                                .toList(),
                        traceability.movements(),
                        traceability.locatedQuantity());
            }
            model.addAttribute("traceability", traceability);
        }
        if ("labels".equals(selectedView)) {
            model.addAttribute(
                    "lotLabels",
                    wmsOperationsService.lotLabels());
            model.addAttribute(
                    "productLabels",
                    wmsOperationsService.productLabels());
        }
        if ("scan".equals(selectedView)
                && scanValue != null
                && !scanValue.isBlank()) {
            model.addAttribute(
                    "scanResult",
                    wmsOperationsService.scan(scanValue));
            model.addAttribute("scanValue", scanValue);
        }
        return "admin/wms";
    }

    @GetMapping("/admin/scan")
    public String scanPage(
            @RequestParam(name = "scanValue", required = false) String scanValue,
            Model model) {
        return page("scan", null, null, 0, null, null, 0, scanValue, model);
    }

    @GetMapping("/admin/scan/labels")
    public String labelPage(Model model) {
        return page("labels", null, null, 0, null, null, 0, null, model);
    }

    @GetMapping("/admin/warehouse-map")
    public String warehouseMapPage(
            @RequestParam(name = "centerId", required = false)
            Long warehouseId,
            Model model) {
        return page("map", warehouseId, null, 0, null, null, 0, null, model);
    }

    @GetMapping("/admin/outbound/direct")
    public String directOutboundPage(Model model) {
        return page("directOutbound", null, null, 0, null, null, 0, null, model);
    }

    @PostMapping("/admin/wms/bins")
    public String createBin(
            @RequestParam(name = "warehouseId") Long warehouseId,
            @RequestParam(name = "productId") Long productId,
            @RequestParam(name = "plannedQuantity") int plannedQuantity,
            @RequestParam(name = "memo", required = false) String memo,
            RedirectAttributes redirectAttributes) {
        try {
            var createdBin = wmsOperationsService.createAutomaticProductBin(
                    warehouseId,
                    productId,
                    plannedQuantity,
                    memo);
            success(redirectAttributes, "창고 구역 " + createdBin.getBinCode()
                    + "을(를) 빈 도면 위치에 자동 등록했습니다.");
        } catch (RuntimeException exception) {
            error(redirectAttributes, exception);
        }
        return "redirect:/admin/wms?view=bins&binWarehouseId="
                + warehouseId;
    }

    @PostMapping("/admin/wms/bins/{binId}")
    public String updateBin(
            @PathVariable(name = "binId") Long binId,
            @RequestParam(name = "warehouseId") Long warehouseId,
            @RequestParam(name = "binCode") String binCode,
            @RequestParam(name = "zone") String zone,
            @RequestParam(name = "purpose") BinPurpose purpose,
            @RequestParam(name = "maxCapacity") int maxCapacity,
            @RequestParam(name = "posX") int posX,
            @RequestParam(name = "posY") int posY,
            @RequestParam(name = "posWidth") int posWidth,
            @RequestParam(name = "posHeight") int posHeight,
            @RequestParam(name = "memo", required = false) String memo,
            @RequestParam(name = "active", defaultValue = "false") boolean active,
            RedirectAttributes redirectAttributes) {
        try {
            wmsOperationsService.updateBin(
                    binId,
                    binCode,
                    zone,
                    purpose,
                    maxCapacity,
                    posX,
                    posY,
                    posWidth,
                    posHeight,
                    memo,
                    active);
            success(redirectAttributes, "창고 구역 정보를 수정했습니다.");
        } catch (RuntimeException exception) {
            error(redirectAttributes, exception);
        }
        return "redirect:/admin/wms?view=bins&binWarehouseId="
                + warehouseId;
    }

    @PostMapping("/admin/wms/bins/{binId}/delete")
    public String deleteBin(
            @PathVariable(name = "binId") Long binId,
            @RequestParam(name = "warehouseId") Long warehouseId,
            RedirectAttributes redirectAttributes) {
        try {
            wmsOperationsService.deleteBin(binId);
            success(redirectAttributes, "창고 구역을 삭제했습니다.");
        } catch (RuntimeException exception) {
            error(redirectAttributes, exception);
        }
        return "redirect:/admin/wms?view=bins&binWarehouseId=" + warehouseId;
    }

    @PostMapping("/admin/wms/inbound")
    public String inbound(
            @RequestParam(name = "existingLotId", required = false) Long existingLotId,
            @RequestParam(name = "productId", required = false) Long productId,
            @RequestParam(name = "lotNo", required = false) String lotNo,
            @RequestParam(name = "manufacturedDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate manufacturedDate,
            @RequestParam(name = "expirationDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate expirationDate,
            @RequestParam(name = "quantity") int quantity,
            @RequestParam(name = "binId") Long binId,
            @RequestParam(name = "memo", required = false) String memo,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        try {
            var lot = wmsOperationsService.receive(
                    existingLotId,
                    productId,
                    lotNo,
                    manufacturedDate,
                    expirationDate,
                    quantity,
                    binId,
                    memo,
                    operator(authentication));
            success(
                    redirectAttributes,
                    lot.getLotNo() + " LOT를 " + quantity
                            + "포대 입고했습니다.");
        } catch (RuntimeException exception) {
            error(redirectAttributes, exception);
        }
        return "redirect:/admin/wms?view=inbound";
    }

    @PostMapping("/admin/wms/inbound/bulk")
    public String bulkInbound(
            @RequestParam(name = "targets", required = false) List<String> targets,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        List<String> successes = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        if (targets == null || targets.isEmpty()) {
            failures.add("일괄 입고할 부족 항목을 선택해 주세요.");
        } else {
            var currentPlan = demandPlanService.plan(LocalDate.now());
            for (String target : targets) {
                try {
                    String[] parts = target.split("\\|", 2);
                    if (parts.length != 2) throw new IllegalArgumentException("잘못된 선택 항목입니다.");
                    Long warehouseId = Long.valueOf(parts[0]);
                    String animalType = parts[1];
                    var warehousePlan = currentPlan.warehouses().stream()
                            .filter(plan -> plan.warehouse().getWarehouseId().equals(warehouseId))
                            .findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("창고 수요 계획을 찾을 수 없습니다."));
                    var row = warehousePlan.rows().stream()
                            .filter(item -> item.animalType().equals(animalType))
                            .findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("축종 수요 계획을 찾을 수 없습니다."));
                    int quantity = row.recommendedInboundQuantity();
                    if (quantity <= 0) throw new IllegalStateException("이미 적정 재고를 충족한 항목입니다.");
                    var result = wmsOperationsService.receiveDemandReplenishment(
                            warehouseId, animalType, quantity, operator(authentication));
                    successes.add(result.warehouseName() + " · " + result.animalType()
                            + " · " + result.quantity() + "포 · LOT " + result.lotNo()
                            + " → " + String.join(", ", result.binCodes()));
                } catch (RuntimeException exception) {
                    failures.add(target + " · " + exception.getMessage());
                }
            }
        }
        redirectAttributes.addFlashAttribute("bulkInboundResults", successes);
        redirectAttributes.addFlashAttribute("bulkInboundErrors", failures);
        return "redirect:/admin/wms?view=inbound";
    }

    @PostMapping("/admin/wms/inbound/adjust")
    public String adjustedDemandInbound(
            @RequestParam(name = "warehouseId") Long warehouseId,
            @RequestParam(name = "animalType") String animalType,
            @RequestParam(name = "quantity") int quantity,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        try {
            var result = wmsOperationsService.receiveDemandReplenishment(
                    warehouseId, animalType, quantity, operator(authentication));
            success(redirectAttributes,
                    result.warehouseName() + " · " + result.animalType()
                            + " " + result.quantity() + "포를 입고했습니다. LOT "
                            + result.lotNo() + " → "
                            + String.join(", ", result.binCodes()));
        } catch (RuntimeException exception) {
            error(redirectAttributes, exception);
        }
        return "redirect:/admin/wms?view=inbound";
    }

    @PostMapping("/admin/wms/move")
    public String move(
            @RequestParam(name = "lotId") Long lotId,
            @RequestParam(name = "sourceBinId") Long sourceBinId,
            @RequestParam(name = "destinationBinId") Long destinationBinId,
            @RequestParam(name = "quantity") int quantity,
            @RequestParam(name = "memo", required = false) String memo,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        try {
            wmsOperationsService.move(
                    lotId,
                    sourceBinId,
                    destinationBinId,
                    quantity,
                    memo,
                    operator(authentication));
            success(redirectAttributes, "구역 재고 이동을 완료했습니다.");
        } catch (RuntimeException exception) {
            error(redirectAttributes, exception);
        }
        return "redirect:/admin/wms?view=move";
    }

    @PostMapping("/admin/scan/inbound")
    public String scanInbound(
            @RequestParam(name = "existingLotId", required = false) Long existingLotId,
            @RequestParam(name = "productId", required = false) Long productId,
            @RequestParam(name = "manufacturedDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate manufacturedDate,
            @RequestParam(name = "quantity") int quantity,
            @RequestParam(name = "binId") Long binId,
            @RequestParam(name = "memo", required = false) String memo,
            @RequestParam(name = "scanValue") String scanValue,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        try {
            var lot = existingLotId != null
                    ? wmsOperationsService.receive(
                            existingLotId,
                            null,
                            null,
                            null,
                            null,
                            quantity,
                            binId,
                            memo,
                            operator(authentication))
                    : wmsOperationsService.receiveScannedProduct(
                            productId,
                            manufacturedDate,
                            quantity,
                            binId,
                            memo,
                            operator(authentication));
            success(
                    redirectAttributes,
                    lot.getLotNo() + " LOT를 " + quantity
                            + "포대 입고했습니다.");
        } catch (RuntimeException exception) {
            error(redirectAttributes, exception);
        }
        redirectAttributes.addAttribute("scanValue", scanValue);
        return "redirect:/admin/scan";
    }

    @PostMapping("/admin/scan/outbound")
    public String scanOutbound(
            @RequestParam(name = "stockLocation") String stockLocation,
            @RequestParam(name = "quantity") int quantity,
            @RequestParam(name = "memo", required = false) String memo,
            @RequestParam(name = "scanValue") String scanValue,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        try {
            String[] identifiers = stockLocation.split(":", 2);
            if (identifiers.length != 2) {
                throw new IllegalArgumentException(
                        "출고할 LOT와 구역을 선택해 주세요.");
            }
            wmsOperationsService.ship(
                    Long.valueOf(identifiers[0]),
                    Long.valueOf(identifiers[1]),
                    quantity,
                    memo,
                    operator(authentication));
            success(
                    redirectAttributes,
                    quantity + "포대 출고 처리를 완료했습니다.");
        } catch (RuntimeException exception) {
            error(redirectAttributes, exception);
        }
        redirectAttributes.addAttribute("scanValue", scanValue);
        return "redirect:/admin/scan";
    }

    @PostMapping("/admin/wms/disposal")
    public String dispose(
            @RequestParam(name = "lotId") Long lotId,
            @RequestParam(name = "binId") Long binId,
            @RequestParam(name = "quantity") int quantity,
            @RequestParam(name = "reason") DisposalReason reason,
            @RequestParam(name = "memo", required = false) String memo,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        try {
            wmsOperationsService.dispose(
                    lotId,
                    binId,
                    quantity,
                    reason,
                    memo,
                    operator(authentication));
            success(redirectAttributes, "재고 폐기 처리를 완료했습니다.");
        } catch (RuntimeException exception) {
            error(redirectAttributes, exception);
        }
        return "redirect:/admin/wms?view=disposal";
    }

    @PostMapping("/admin/wms/sync")
    public String synchronize(
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        try {
            wmsOperationsService.synchronizeAll(operator(authentication));
            success(
                    redirectAttributes,
                    "상품·LOT·구역 재고를 LOT 실재고 기준으로 동기화했습니다.");
        } catch (RuntimeException exception) {
            error(redirectAttributes, exception);
        }
        return "redirect:/admin/wms?view=sync";
    }

    @PostMapping("/admin/wms/outbound/direct")
    public String directOutbound(
            @RequestParam(name = "productId") Long productId,
            @RequestParam(name = "quantity") int quantity,
            @RequestParam(name = "memo", required = false) String memo,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        try {
            var allocations = wmsOperationsService.shipProductFefo(
                    productId,
                    quantity,
                    memo,
                    operator(authentication));
            String allocationSummary = allocations.stream()
                    .map(allocation -> allocation.lotNo() + " "
                            + allocation.quantity() + "포대")
                    .collect(java.util.stream.Collectors.joining(", "));
            success(
                    redirectAttributes,
                    quantity + "포대 직접 출고를 완료했습니다. (FEFO: "
                            + allocationSummary + ")");
        } catch (RuntimeException exception) {
            error(redirectAttributes, exception);
        }
        return "redirect:/admin/outbound/direct";
    }

    private String operator(Authentication authentication) {
        return authentication == null ? "관리자" : authentication.getName();
    }

    private void success(
            RedirectAttributes redirectAttributes,
            String message) {
        redirectAttributes.addFlashAttribute("wmsMessage", message);
    }

    private void error(
            RedirectAttributes redirectAttributes,
            RuntimeException exception) {
        redirectAttributes.addFlashAttribute(
                "wmsError",
                exception.getMessage() == null
                        ? "작업을 처리할 수 없습니다."
                        : exception.getMessage());
    }
}
