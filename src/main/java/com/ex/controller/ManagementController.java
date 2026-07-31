package com.ex.controller;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.ex.entity.Delivery.DeliveryStatus;
import com.ex.entity.DefectRecord.DefectType;
import com.ex.entity.DefectRecord.OccurrenceStage;
import com.ex.entity.DefectRecord.ResolutionType;
import com.ex.entity.FarmCustomer.CustomerStatus;
import com.ex.entity.Product;
import com.ex.entity.ProductLot;
import com.ex.entity.StockLog;
import com.ex.entity.StockLog.ChangeType;
import com.ex.service.BarcodeService;
import com.ex.service.DistributionService;
import com.ex.service.DefectService;
import com.ex.service.FarmCustomerService;
import com.ex.service.InventoryService;
import com.ex.service.RecurringDeliveryService;
import com.ex.service.ShipmentService;
import com.ex.service.WarehouseManagementService;

@Controller
public class ManagementController {

    private final InventoryService inventoryService;
    private final DistributionService distributionService;
    private final RecurringDeliveryService recurringDeliveryService;
    private final DefectService defectService;
    private final ShipmentService shipmentService;
    private final BarcodeService barcodeService;
    private final WarehouseManagementService warehouseManagementService;
    private final FarmCustomerService farmCustomerService;

    public ManagementController(
            InventoryService inventoryService,
            DistributionService distributionService,
            RecurringDeliveryService recurringDeliveryService,
            DefectService defectService,
            ShipmentService shipmentService,
            BarcodeService barcodeService,
            WarehouseManagementService warehouseManagementService,
            FarmCustomerService farmCustomerService) {
        this.inventoryService = inventoryService;
        this.distributionService = distributionService;
        this.recurringDeliveryService = recurringDeliveryService;
        this.defectService = defectService;
        this.shipmentService = shipmentService;
        this.barcodeService = barcodeService;
        this.warehouseManagementService = warehouseManagementService;
        this.farmCustomerService = farmCustomerService;
    }

    @Value("${kakao.maps.javascript-key:}")
    private String kakaoMapsJavascriptKey;

    /*
     * 유통기한 임박 LOT 화면 출력용 객체
     */
    public record ExpiringLotView(
            ProductLot lot,
            long daysRemaining) {

        /*
         * 남은 일수가 0보다 작으면
         * 이미 유통기한이 지난 LOT입니다.
         */
        public boolean isExpired() {
            return daysRemaining < 0;
        }
    }

    public record LotExpiryStatus(
            String label,
            String cssClass,
            long daysRemaining) {
    }

    public record ProductLotSummary(
            long activeLotCount,
            int totalQuantity,
            ProductLot nearestExpirationLot) {
    }

    /*
     * 재고 관리 화면
     */
    @GetMapping("/inventory")
    public String inventory(Model model) {

        LocalDate today =
                LocalDate.now();

        /*
         * 오늘부터 한 달 뒤 날짜
         */
        LocalDate expirationLimit =
                today.plusMonths(1);

        /*
         * 전체 LOT 목록
         */
        List<ProductLot> lots =
                inventoryService.lots();

        List<Product> products =
                inventoryService.products();

        /*
         * 유통기한이 지났거나
         * 한 달 이내로 남은 LOT
         *
         * 재고가 1개 이상인 LOT만 표시합니다.
         */
        List<ExpiringLotView> expiringLots =
                lots.stream()
                    .filter(lot ->
                            lot.getLotQuantity() > 0)
                    .filter(lot ->
                            !lot.getExpirationDate()
                                .isAfter(expirationLimit))
                    .map(lot ->
                            new ExpiringLotView(
                                    lot,
                                    ChronoUnit.DAYS.between(
                                            today,
                                            lot.getExpirationDate())))
                    .toList();

        /*
         * 유통기한이 한 달보다 많이 남은
         * 일반 LOT
         */
        List<ProductLot> normalLots =
                lots.stream()
                    .filter(lot ->
                            lot.getLotQuantity() > 0)
                    .filter(lot ->
                            lot.getExpirationDate()
                                .isAfter(expirationLimit))
                    .toList();

        List<ProductLot> activeLots =
                lots.stream()
                    .filter(lot ->
                            lot.getLotQuantity() > 0)
                    .toList();

        Map<Long, ProductLotSummary> productLotSummaries =
                products.stream().collect(Collectors.toMap(
                        Product::getProductId,
                        product -> {
                            List<ProductLot> productLots = activeLots.stream()
                                    .filter(lot -> lot.getProduct().getProductId()
                                            .equals(product.getProductId()))
                                    .toList();
                            ProductLot nearestLot = productLots.stream()
                                    .min(java.util.Comparator.comparing(
                                            ProductLot::getExpirationDate))
                                    .orElse(null);
                            int totalQuantity = productLots.stream()
                                    .mapToInt(ProductLot::getLotQuantity)
                                    .sum();
                            return new ProductLotSummary(
                                    productLots.size(),
                                    totalQuantity,
                                    nearestLot);
                        }));

        Map<Long, LotExpiryStatus> lotExpiryStatuses =
                activeLots.stream().collect(Collectors.toMap(
                        ProductLot::getLotId,
                        lot -> lotExpiryStatus(lot, today)));

        long expiredLotCount = activeLots.stream()
                .filter(lot -> lot.getExpirationDate().isBefore(today))
                .count();
        long expiringSoonLotCount = activeLots.stream()
                .filter(lot -> !lot.getExpirationDate().isBefore(today))
                .filter(lot -> !lot.getExpirationDate().isAfter(today.plusDays(30)))
                .count();
        long unlocatedLotCount = activeLots.stream()
                .filter(lot -> lot.getWarehouseLocation() == null
                        || lot.getWarehouseLocation().isBlank())
                .count();

        model.addAttribute(
                "products",
                products);

        model.addAttribute("reservedLotStocks", inventoryService.reservedStockByLot());

        model.addAttribute(
                "lots",
                lots);

        model.addAttribute(
                "expiringLots",
                expiringLots);

        model.addAttribute(
                "normalLots",
                normalLots);

        model.addAttribute(
                "activeLots",
                activeLots);
        model.addAttribute("productLotSummaries", productLotSummaries);
        model.addAttribute("lotExpiryStatuses", lotExpiryStatuses);
        model.addAttribute("expiredLotCount", expiredLotCount);
        model.addAttribute("expiringSoonLotCount", expiringSoonLotCount);
        model.addAttribute("unlocatedLotCount", unlocatedLotCount);

        model.addAttribute(
                "recurringDeliveries",
                recurringDeliveryService.deliveries());

        model.addAttribute(
                "activeRecurringCount",
                recurringDeliveryService.activeCount());
        model.addAttribute(
                "recurringWarehouseSummaries",
                recurringDeliveryService.warehouseSummaries());

        model.addAttribute(
                "logs",
                inventoryService.recentLogs());

        model.addAttribute(
                "warehouses",
                warehouseManagementService.warehouses());
        model.addAttribute(
                "warehouseAllocations",
                warehouseManagementService.allocations());
        model.addAttribute(
                "warehouseSummaries",
                warehouseManagementService.summaries());
        model.addAttribute(
                "totalWarehouseMonthlyPlan",
                warehouseManagementService.totalMonthlyPlannedQuantity());
        model.addAttribute(
                "totalWarehouseTargetStock",
                warehouseManagementService.totalTargetStockQuantity());
        model.addAttribute(
                "totalWarehouseCurrentStock",
                warehouseManagementService.totalCurrentStockQuantity());
        model.addAttribute(
                "warehouseLowStockCount",
                warehouseManagementService.lowStockAllocationCount());
        model.addAttribute(
                "warehouseLowStockAllocations",
                warehouseManagementService.lowStockAllocations());

        addDefectAttributes(model, lots);
        addShipmentAttributes(model);

        return "inventory";
    }

    @PostMapping("/inventory/warehouses/allocations/update")
    public String updateWarehouseAllocation(
            @RequestParam("allocationId") Long allocationId,
            @RequestParam("monthlyPlannedQuantity")
            int monthlyPlannedQuantity,
            @RequestParam("targetStockQuantity")
            int targetStockQuantity,
            RedirectAttributes redirectAttributes) {
        return execute(
                () -> warehouseManagementService.updateAllocation(
                        allocationId,
                        monthlyPlannedQuantity,
                        targetStockQuantity),
                "/inventory?view=warehouses",
                "창고 상품 배치 계획이 수정되었습니다.",
                redirectAttributes);
    }

    @PostMapping("/inventory/warehouses/stock/update")
    public String updateWarehouseStock(
            @RequestParam("allocationId") Long allocationId,
            @RequestParam("currentStockQuantity")
            int currentStockQuantity,
            RedirectAttributes redirectAttributes) {
        return execute(
                () -> warehouseManagementService.adjustCurrentStock(
                        allocationId,
                        currentStockQuantity),
                "/inventory?view=stock",
                "창고 현재고가 수정되었습니다.",
                redirectAttributes);
    }

    private void addShipmentAttributes(Model model) {
        model.addAttribute("activeShipments", shipmentService.activeShipments());
        model.addAttribute("cancelledShipments", shipmentService.cancelledShipments());
        model.addAttribute("shipmentItems", shipmentService.itemsByShipment());
        model.addAttribute("activeShipmentCount", shipmentService.activeCount());
        model.addAttribute("activeOutboundLogs", inventoryService.activeOutboundLogs());
        model.addAttribute("cancelledOutboundLogs", inventoryService.cancelledOutboundLogs());
    }

    private void addDefectAttributes(Model model, List<ProductLot> lots) {
        Map<Long, Integer> reservedLots = inventoryService.reservedStockByLot();
        model.addAttribute("records", defectService.records());
        model.addAttribute("openDefectCount", defectService.unresolvedCount());
        model.addAttribute("defectLots", lots.stream()
                .filter(lot -> lot.getLotQuantity()
                        - reservedLots.getOrDefault(lot.getLotId(), 0) > 0)
                .toList());
        model.addAttribute("defectTypes", DefectType.values());
        model.addAttribute("occurrenceStages", OccurrenceStage.values());
        model.addAttribute("resolutionTypes", ResolutionType.values());
        model.addAttribute("reservedLotStocks", reservedLots);
    }

    private LotExpiryStatus lotExpiryStatus(ProductLot lot, LocalDate today) {
        long days = ChronoUnit.DAYS.between(today, lot.getExpirationDate());
        if (days < 0) {
            return new LotExpiryStatus("만료", "expired", days);
        }
        if (days <= 7) {
            return new LotExpiryStatus("7일 이내", "critical", days);
        }
        if (days <= 30) {
            return new LotExpiryStatus("30일 이내", "soon", days);
        }
        return new LotExpiryStatus("정상", "normal", days);
    }

    /*
     * 상품 상세 화면
     */
    @GetMapping("/inventory/defects")
    public String defects(Model model) {
        addDefectAttributes(model, inventoryService.lots());
        return "defects";
    }

    @PostMapping("/inventory/defects")
    public String registerDefect(
            @RequestParam("lotId") Long lotId,
            @RequestParam("quantity") int quantity,
            @RequestParam("defectType") DefectType defectType,
            @RequestParam("occurrenceStage") OccurrenceStage occurrenceStage,
            @RequestParam("description") String description,
            @RequestParam("reporter") String reporter,
            @RequestParam(name = "occurredAt", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime occurredAt,
            RedirectAttributes redirectAttributes) {
        return execute(
                () -> defectService.register(lotId, quantity, defectType,
                        occurrenceStage, description, reporter, occurredAt),
                "/inventory?view=defects",
                "불량품이 등록되어 가용 재고에서 격리되었습니다.",
                redirectAttributes);
    }

    @PostMapping("/inventory/defects/{defectId}/inspect")
    public String inspectDefect(@PathVariable("defectId") Long defectId,
            RedirectAttributes redirectAttributes) {
        return execute(() -> defectService.startInspection(defectId),
                "/inventory?view=defects", "불량품 상태를 검사 중으로 변경했습니다.",
                redirectAttributes);
    }

    @PostMapping("/inventory/defects/{defectId}/resolve")
    public String resolveDefect(
            @PathVariable("defectId") Long defectId,
            @RequestParam("resolutionType") ResolutionType resolutionType,
            @RequestParam("processor") String processor,
            @RequestParam("resolutionNote") String resolutionNote,
            RedirectAttributes redirectAttributes) {
        return execute(
                () -> defectService.resolve(
                        defectId, resolutionType, processor, resolutionNote),
                "/inventory?view=defects", "불량품 처리가 완료되었습니다.",
                redirectAttributes);
    }

    @GetMapping("/products/{productId}")
    public String productDetail(
            @PathVariable("productId") Long productId,
            Model model) {

        Product product = inventoryService.productDetail(productId);
        List<ProductLot> productLots = inventoryService.productLots(productId);

        model.addAttribute("product", product);
        model.addAttribute("productLots", productLots);
        model.addAttribute("productLotStatuses",
                productLots.stream().collect(Collectors.toMap(
                        ProductLot::getLotId,
                        lot -> lotExpiryStatus(lot, LocalDate.now()))));
        model.addAttribute("representativeLot",
                productLots.isEmpty() ? null : productLots.get(0));
        model.addAttribute("productImageUrl", productImageUrl(product));

        return "product-detail";
    }

    @GetMapping("/inventory/lots/{lotId}")
    public String lotDetail(
            @PathVariable("lotId") Long lotId,
            Model model) {

        ProductLot lot = inventoryService.lotDetail(lotId);
        List<StockLog> lotLogs = inventoryService.lotLogs(lotId);
        int reservedQuantity = inventoryService.reservedStockByLot()
                .getOrDefault(lotId, 0);

        model.addAttribute("lot", lot);
        model.addAttribute("lotStatus", lotExpiryStatus(lot, LocalDate.now()));
        model.addAttribute("lotLogs", lotLogs);
        model.addAttribute("inboundLogs", lotLogs.stream()
                .filter(log -> log.getChangeType() == ChangeType.INBOUND)
                .toList());
        model.addAttribute("defectRecords", defectService.recordsForLot(lotId));
        model.addAttribute("initialReceivedQuantity",
                inventoryService.initialReceivedQuantity(lotId));
        model.addAttribute("reservedQuantity", reservedQuantity);
        model.addAttribute("availableQuantity",
                lot.getLotQuantity() - reservedQuantity);
        model.addAttribute("barcodeDataUri",
                barcodeService.code39DataUri(lot.getLotNo()));

        return "lot-detail";
    }

    private String productImageUrl(Product product) {
        if (product.getImageUrl() != null && !product.getImageUrl().isBlank()) {
            return product.getImageUrl();
        }
        return switch (product.getAnimalType()) {
            case "소" -> "/images/products/cattle-feed-v2.png";
            case "돼지" -> "/images/products/pig-feed-v2.png";
            case "조류(닭/오리)" -> "/images/products/poultry-feed-v2.png";
            case "영양제" -> "/images/products/supplement-v2.png";
            default -> "/images/products/cattle-feed-v2.png";
        };
    }

    @PostMapping("/shipments/{shipmentId}/picking")
    public String startShipmentPicking(
            @PathVariable("shipmentId") Long shipmentId,
            @RequestParam("worker") String worker,
            RedirectAttributes redirectAttributes) {
        return execute(() -> shipmentService.startPicking(shipmentId, worker),
                "/inventory?view=shipments", "피킹 작업을 시작했습니다.", redirectAttributes);
    }

    @PostMapping("/shipments/{shipmentId}/inspect")
    public String inspectShipment(
            @PathVariable("shipmentId") Long shipmentId,
            @RequestParam("worker") String worker,
            RedirectAttributes redirectAttributes) {
        return execute(() -> shipmentService.inspect(shipmentId, worker),
                "/inventory?view=shipments", "출고 검수를 완료했습니다.", redirectAttributes);
    }

    @PostMapping("/shipments/{shipmentId}/complete")
    public String completeShipment(
            @PathVariable("shipmentId") Long shipmentId,
            @RequestParam("worker") String worker,
            RedirectAttributes redirectAttributes) {
        return execute(() -> shipmentService.complete(shipmentId, worker),
                "/inventory?view=shipments", "재고 차감과 출고 처리가 완료되었습니다.", redirectAttributes);
    }

    @PostMapping("/shipments/{shipmentId}/cancel")
    public String cancelShipment(
            @PathVariable("shipmentId") Long shipmentId,
            @RequestParam("note") String note,
            RedirectAttributes redirectAttributes) {
        return execute(() -> shipmentService.cancel(shipmentId, note),
                "/inventory?view=shipments&shipmentTab=cancelled",
                "출고 지시가 취소되었습니다.", redirectAttributes);
    }

    @PostMapping("/shipments/{shipmentId}/cancel-completed")
    public String cancelCompletedShipment(
            @PathVariable("shipmentId") Long shipmentId,
            @RequestParam("note") String note,
            RedirectAttributes redirectAttributes) {
        return execute(() -> shipmentService.cancelCompleted(shipmentId, note),
                "/inventory?view=shipments&shipmentTab=cancelled",
                "출고 완료가 취소되어 재고가 원복되었습니다.", redirectAttributes);
    }

    @PostMapping("/inventory/outbounds/{logId}/cancel")
    public String cancelDirectOutbound(
            @PathVariable("logId") Long logId,
            @RequestParam("cancelReason") String cancelReason,
            RedirectAttributes redirectAttributes) {
        return execute(() -> inventoryService.cancelDirectOutbound(logId, cancelReason),
                "/inventory?view=shipments&shipmentTab=cancelled",
                "직접 출고가 취소되어 재고가 원복되었습니다.", redirectAttributes);
    }

    /*
     * 신규 입고
     */
    @PostMapping("/inventory/receive")
    public String receive(
            @RequestParam("productId") Long productId,
            @RequestParam("lotNo") String lotNo,
            @RequestParam("manufacturedDate")
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE)
            LocalDate manufacturedDate,
            @RequestParam("expirationDate")
            @DateTimeFormat(
                    iso = DateTimeFormat.ISO.DATE)
            LocalDate expirationDate,
            @RequestParam("quantity") int quantity,
            @RequestParam("reason") String reason,
            RedirectAttributes redirectAttributes) {

        return execute(
                () -> inventoryService.receive(
                        productId,
                        lotNo,
                        manufacturedDate,
                        expirationDate,
                        quantity,
                        reason),
                "/inventory",
                "입고가 등록되었습니다.",
                redirectAttributes);
    }

    /*
     * FIFO 출고
     */
    @PostMapping("/inventory/release")
    public String release(
            @RequestParam("productId") Long productId,
            @RequestParam("quantity") int quantity,
            @RequestParam("reason") String reason,
            RedirectAttributes redirectAttributes) {

        return execute(
                () -> inventoryService.releaseFifo(
                        productId,
                        quantity,
                        reason),
                "/inventory?view=shipments",
                "FIFO 기준 출고가 완료되었습니다.",
                redirectAttributes);
    }

    /*
     * LOT 재고 조정
     */
    @PostMapping("/inventory/adjust")
    public String adjust(
            @RequestParam("lotId") Long lotId,
            @RequestParam("changedQty") int changedQty,
            @RequestParam("reason") String reason,
            RedirectAttributes redirectAttributes) {

        return execute(
                () -> inventoryService.adjust(
                        lotId,
                        changedQty,
                        reason),
                "/inventory",
                "재고가 조정되었습니다.",
                redirectAttributes);
    }

    @PostMapping("/inventory/lots/{lotId}/location")
    public String updateLotLocation(
            @PathVariable("lotId") Long lotId,
            @RequestParam("warehouseLocation") String warehouseLocation,
            RedirectAttributes redirectAttributes) {

        return execute(
                () -> inventoryService.updateWarehouseLocation(
                        lotId, warehouseLocation),
                "/inventory/lots/" + lotId,
                "창고 위치가 저장되었습니다.",
                redirectAttributes);
    }

    @PostMapping("/inventory/lots/{lotId}/audit")
    public String auditLot(
            @PathVariable("lotId") Long lotId,
            @RequestParam("actualQuantity") int actualQuantity,
            @RequestParam(name = "reason", required = false) String reason,
            RedirectAttributes redirectAttributes) {

        return execute(
                () -> inventoryService.auditLot(
                        lotId, actualQuantity, reason),
                "/inventory/lots/" + lotId,
                "재고 실사가 반영되었습니다.",
                redirectAttributes);
    }

    @PostMapping("/inventory/lots/{lotId}/inbounds/{logId}/cancel")
    public String cancelInbound(
            @PathVariable("lotId") Long lotId,
            @PathVariable("logId") Long logId,
            @RequestParam("cancelReason") String cancelReason,
            RedirectAttributes redirectAttributes) {

        return execute(
                () -> inventoryService.cancelInbound(
                        lotId, logId, cancelReason),
                "/inventory/lots/" + lotId,
                "입고가 취소되어 재고가 원복되었습니다.",
                redirectAttributes);
    }

    /*
     * 상품 삭제
     * 주문, LOT, 재고 이력은 보존하고 운영 상품 목록에서 제외합니다.
     */
    @PostMapping("/inventory/products/{productId}/delete")
    public String deleteProduct(
            @PathVariable("productId") Long productId,
            RedirectAttributes redirectAttributes) {

        return execute(
                () -> inventoryService.deleteProduct(productId),
                "/inventory?view=registered",
                "상품이 삭제되었습니다. 기존 LOT와 재고 이력은 보존됩니다.",
                redirectAttributes);
    }

    /*
     * 월간 정기 배송 일정 등록
     */
    @PostMapping("/inventory/recurring/create")
    public String createRecurringDelivery(
            @RequestParam("warehouseId") Long warehouseId,
            @RequestParam("productId") Long productId,
            @RequestParam("quantity") int quantity,
            @RequestParam("deliveryDay") int deliveryDay,
            @RequestParam("notes") String notes,
            RedirectAttributes redirectAttributes) {

        return execute(
                () -> recurringDeliveryService.create(
                        warehouseId,
                        productId,
                        quantity,
                        deliveryDay,
                        notes),
                "/inventory?view=recurring",
                "월간 정기 배송 일정이 등록되었습니다.",
                redirectAttributes);
    }

    /*
     * 정기 배송 도착분을 실제 재고로 입고
     */
    @PostMapping("/inventory/recurring/receive")
    public String receiveRecurringDelivery(
            @RequestParam("recurringDeliveryId")
            Long recurringDeliveryId,
            @RequestParam("manufacturedDate")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate manufacturedDate,
            RedirectAttributes redirectAttributes) {

        return execute(
                () -> recurringDeliveryService.receive(
                        recurringDeliveryId,
                        manufacturedDate),
                "/inventory?view=recurring",
                "정기 배송 상품이 재고에 입고되었습니다.",
                redirectAttributes);
    }

    /*
     * 영양제 안전재고 점검 완료
     */
    @PostMapping("/inventory/recurring/review")
    public String reviewRecurringDelivery(
            @RequestParam("recurringDeliveryId")
            Long recurringDeliveryId,
            RedirectAttributes redirectAttributes) {

        return execute(
                () -> recurringDeliveryService.review(
                        recurringDeliveryId),
                "/inventory?view=recurring",
                "현재 재고가 충분하여 다음 안전재고 점검일로 넘겼습니다.",
                redirectAttributes);
    }

    /*
     * 정기 배송 활성/중지 전환
     */
    @PostMapping("/inventory/recurring/toggle")
    public String toggleRecurringDelivery(
            @RequestParam("recurringDeliveryId")
            Long recurringDeliveryId,
            RedirectAttributes redirectAttributes) {

        return execute(
                () -> recurringDeliveryService.toggle(
                        recurringDeliveryId),
                "/inventory?view=recurring",
                "정기 배송 상태가 변경되었습니다.",
                redirectAttributes);
    }

    /*
     * 유통 관리 화면
     */
    @GetMapping("/distribution")
    public String distribution(Model model) {

        var deliveries = distributionService.deliveries();
        var orders = distributionService.orders();
        var cancelledOrders = orders.stream()
                .filter(order -> order.getStatus()
                        == com.ex.entity.CustomerOrder.OrderStatus.CANCELLED)
                .toList();
        var activeOrderDeliveries = deliveries.stream()
                .filter(delivery -> delivery.getOrder().getStatus()
                        != com.ex.entity.CustomerOrder.OrderStatus.CANCELLED)
                .toList();
        var shipmentByOrder = shipmentService.shipmentByOrder();
        var deliveryByOrder = deliveries.stream()
                .collect(Collectors.toMap(
                        delivery -> delivery.getOrder().getOrderId(),
                        delivery -> delivery));
        var readyDeliveryOrders = orders.stream()
                .filter(order -> {
                    return !deliveryByOrder.containsKey(order.getOrderId())
                            && order.getStatus() != com.ex.entity.CustomerOrder.OrderStatus.DELIVERED
                            && order.getStatus() != com.ex.entity.CustomerOrder.OrderStatus.CANCELLED;
                })
                .toList();
        var reservedLotStocks = inventoryService.reservedStockByLot();
        var warehouseOrderableProductIds = warehouseManagementService
                .allocations()
                .stream()
                .filter(allocation ->
                        allocation.getWarehouse().isActive())
                .filter(allocation ->
                        allocation.getCurrentStockQuantity() > 0)
                .map(allocation ->
                        allocation.getProduct().getProductId())
                .collect(Collectors.toSet());
        var orderableLots = inventoryService.lots().stream()
                .filter(lot -> lot.getLotQuantity()
                        - reservedLotStocks.getOrDefault(lot.getLotId(), 0) > 0)
                .filter(lot -> warehouseOrderableProductIds.contains(
                        lot.getProduct().getProductId()))
                .toList();

        model.addAttribute(
                "orders",
                orders);

        model.addAttribute(
                "deliveries",
                activeOrderDeliveries);

        model.addAttribute(
                "deliveryStatuses",
                DeliveryStatus.values());
        model.addAttribute("defectTypes", DefectType.values());

        model.addAttribute("shipmentByOrder", shipmentByOrder);
        model.addAttribute("deliveryByOrder", deliveryByOrder);
        model.addAttribute("readyDeliveryOrders", readyDeliveryOrders);
        model.addAttribute("cancelledOrders", cancelledOrders);
        model.addAttribute("cancelledOrderTotalAmount", cancelledOrders.stream()
                .map(order -> order.getFinalPrice())
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        model.addAttribute("cancelledOrderItemCounts", cancelledOrders.stream()
                .collect(Collectors.toMap(
                        order -> order.getOrderId(),
                        order -> distributionService.orderItemCount(
                                order.getOrderId()))));
        model.addAttribute("orderableLots", orderableLots);
        model.addAttribute("availableLotStocks", orderableLots.stream()
                .collect(Collectors.toMap(
                        ProductLot::getLotId,
                        lot -> lot.getLotQuantity()
                                - reservedLotStocks.getOrDefault(
                                        lot.getLotId(), 0))));
        model.addAttribute("allDeliveryCount", readyDeliveryOrders.size()
                + activeOrderDeliveries.stream()
                        .filter(delivery -> delivery.getStatus()
                                != DeliveryStatus.CANCELLED)
                        .count());
        model.addAttribute("readyDeliveryCount", readyDeliveryOrders.size());
        model.addAttribute("pickedUpDeliveryCount", activeOrderDeliveries.stream()
                .filter(delivery -> delivery.getStatus() == DeliveryStatus.PICKED_UP)
                .count());
        model.addAttribute("inTransitDeliveryCount", activeOrderDeliveries.stream()
                .filter(delivery -> delivery.getStatus() == DeliveryStatus.IN_TRANSIT)
                .count());
        model.addAttribute("deliveredDeliveryCount", activeOrderDeliveries.stream()
                .filter(delivery -> delivery.getStatus() == DeliveryStatus.DELIVERED)
                .count());
        model.addAttribute("delayedDeliveryCount", activeOrderDeliveries.stream()
                .filter(delivery -> delivery.isDelayed())
                .count());
        model.addAttribute("cancelledDeliveryCount", activeOrderDeliveries.stream()
                .filter(delivery -> delivery.getStatus() == DeliveryStatus.CANCELLED)
                .count());
        model.addAttribute("returnDeliveryCount", activeOrderDeliveries.stream()
                .filter(delivery -> delivery.getReturnStatus() != null)
                .count());
        model.addAttribute("cancelledOrderCount", cancelledOrders.size());

        long readyTotal = readyDeliveryOrders.size();
        long pickedTotal = activeOrderDeliveries.stream()
                .filter(delivery -> delivery.getStatus() == DeliveryStatus.PICKED_UP
                        || delivery.getStatus() == DeliveryStatus.IN_TRANSIT
                        || delivery.getStatus() == DeliveryStatus.DELIVERED)
                .count();
        long transitTotal = activeOrderDeliveries.stream()
                .filter(delivery -> delivery.getStatus() == DeliveryStatus.IN_TRANSIT
                        || delivery.getStatus() == DeliveryStatus.DELIVERED)
                .count();
        long deliveredTotal = activeOrderDeliveries.stream()
                .filter(delivery -> delivery.getStatus() != DeliveryStatus.CANCELLED)
                .count();
        long deliveredCompleted = activeOrderDeliveries.stream()
                .filter(delivery -> delivery.getStatus() == DeliveryStatus.DELIVERED)
                .count();
        long delayedCompleted = distributionService.deliveryHistoryCount(
                "도착 예정일 변경:");
        long delayedPending = activeOrderDeliveries.stream()
                .filter(delivery -> delivery.isDelayed())
                .count();
        long cancelledTotal = distributionService.deliveryHistoryCount(
                "배송 취소 ·");
        long cancelledCompleted = distributionService.deliveryHistoryCount(
                "재배송 등록 ·");
        long returnTotal = distributionService.deliveryHistoryCount(
                "회수 요청 ·");
        long returnCompleted = distributionService.deliveryHistoryCount(
                "회수 검수 완료 ·");

        model.addAttribute("allProgressCompleted", deliveredCompleted);
        model.addAttribute("allProgressTotal",
                readyTotal + deliveredTotal);
        model.addAttribute("readyProgressCompleted",
                deliveredTotal);
        model.addAttribute("readyProgressTotal",
                readyTotal + deliveredTotal);
        model.addAttribute("pickedProgressCompleted",
                activeOrderDeliveries.stream()
                        .filter(delivery -> delivery.getStatus()
                                == DeliveryStatus.IN_TRANSIT
                                || delivery.getStatus()
                                == DeliveryStatus.DELIVERED)
                        .count());
        model.addAttribute("pickedProgressTotal", pickedTotal);
        model.addAttribute("transitProgressCompleted", deliveredCompleted);
        model.addAttribute("transitProgressTotal", transitTotal);
        model.addAttribute("deliveredProgressCompleted", deliveredCompleted);
        model.addAttribute("deliveredProgressTotal",
                readyTotal + deliveredTotal);
        model.addAttribute("delayedProgressCompleted", delayedCompleted);
        model.addAttribute("delayedProgressTotal",
                delayedCompleted + delayedPending);
        model.addAttribute("cancelledProgressCompleted", cancelledCompleted);
        model.addAttribute("cancelledProgressTotal",
                Math.max(cancelledTotal, cancelledCompleted));
        model.addAttribute("returnProgressCompleted", returnCompleted);
        model.addAttribute("returnProgressTotal",
                Math.max(returnTotal, returnCompleted));
        model.addAttribute("cancelledOrderProgressCompleted",
                (long) cancelledOrders.size());
        model.addAttribute("cancelledOrderProgressTotal",
                (long) cancelledOrders.size());
        model.addAttribute("kakaoMapsJavascriptKey",
                kakaoMapsJavascriptKey);
        model.addAttribute("farmCustomers",
                farmCustomerService.customers());
        model.addAttribute("activeFarmCustomerCount",
                farmCustomerService.activeCount());
        model.addAttribute("farmMonthlyFeedTotal",
                farmCustomerService.totalMonthlyFeedQuantity());
        model.addAttribute("farmWarehouseSummaries",
                farmCustomerService.warehouseSummaries());
        model.addAttribute("farmWarehouses",
                warehouseManagementService.warehouses());

        return "distribution";
    }

    @PostMapping("/distribution/farm-customers/{farmCustomerId}/status")
    public String changeFarmCustomerStatus(
            @PathVariable("farmCustomerId") Long farmCustomerId,
            @RequestParam("status") CustomerStatus status,
            RedirectAttributes redirectAttributes) {
        return execute(
                () -> farmCustomerService.changeStatus(
                        farmCustomerId, status),
                "/distribution?view=farms",
                status == CustomerStatus.ACTIVE
                        ? "농장 고객사의 거래를 재개했습니다."
                        : "농장 고객사를 거래 보류로 변경했습니다.",
                redirectAttributes);
    }

    @PostMapping("/distribution/demo-orders")
    public String createDemoOrder(
            @RequestParam(name = "farmCustomerId", required = false)
            Long farmCustomerId,
            @RequestParam("lotId") Long lotId,
            @RequestParam("quantity") int quantity,
            @RequestParam(name = "discountPrice", defaultValue = "0")
            BigDecimal discountPrice,
            @RequestParam("recipientName") String recipientName,
            @RequestParam("recipientPhone") String recipientPhone,
            @RequestParam("postalCode") String postalCode,
            @RequestParam(name = "roadAddress", required = false)
            String roadAddress,
            @RequestParam(name = "jibunAddress", required = false)
            String jibunAddress,
            @RequestParam(name = "detailAddress", required = false)
            String detailAddress,
            @RequestParam(name = "latitude", required = false)
            Double latitude,
            @RequestParam(name = "longitude", required = false)
            Double longitude,
            @RequestParam(name = "deliveryRequest", required = false)
            String deliveryRequest,
            RedirectAttributes redirectAttributes) {

        return execute(
                () -> distributionService.createDemoOrder(
                        farmCustomerId,
                        lotId, quantity, discountPrice,
                        recipientName, recipientPhone,
                        postalCode, roadAddress, jibunAddress,
                        detailAddress, latitude, longitude,
                        deliveryRequest),
                "/distribution?view=ready",
                "주문이 생성되고 배송지에서 가장 가까운 가용 창고가 자동 배정되었습니다.",
                redirectAttributes);
    }

    @GetMapping("/distribution/deliveries/{deliveryId}")
    public String deliveryDetail(
            @PathVariable("deliveryId") Long deliveryId,
            Model model) {

        var delivery = distributionService.deliveryDetail(deliveryId);
        model.addAttribute("delivery", delivery);
        model.addAttribute("shipment",
                distributionService.shipmentForDelivery(deliveryId));
        model.addAttribute("shipmentItems",
                distributionService.shipmentItemsForDelivery(deliveryId));
        model.addAttribute("deliveryHistories",
                distributionService.deliveryHistories(deliveryId));
        model.addAttribute("deliveryStatuses", DeliveryStatus.values());
        model.addAttribute("defectTypes", DefectType.values());

        return "delivery-detail";
    }

    @PostMapping("/distribution/shipments")
    public String createShipment(
            @RequestParam("orderId") Long orderId,
            @RequestParam("worker") String worker,
            @RequestParam(name = "note", required = false) String note,
            RedirectAttributes redirectAttributes) {
        return execute(() -> shipmentService.create(orderId, worker, note),
                "/distribution", "출고 지시가 생성되었습니다.", redirectAttributes);
    }

    /*
     * 배송 정보 등록
     */
    @PostMapping("/distribution/register")
    public String registerDelivery(
            @RequestParam("orderId") Long orderId,
            @RequestParam("carrierName") String carrierName,
            @RequestParam("trackingNumber") String trackingNumber,
            RedirectAttributes redirectAttributes) {

        return execute(
                () -> distributionService.registerDelivery(
                        orderId,
                        carrierName,
                        trackingNumber),
                "/distribution",
                "배송 정보가 등록되었습니다.",
                redirectAttributes);
    }

    /*
     * 배송 상태 변경
     */
    @PostMapping("/distribution/status")
    public String updateStatus(
            @RequestParam("deliveryId") Long deliveryId,
            @RequestParam("status") DeliveryStatus status,
            @RequestParam(name = "note", required = false) String note,
            RedirectAttributes redirectAttributes) {

        return execute(
                () -> distributionService.updateDelivery(
                        deliveryId,
                        status,
                        note),
                "/distribution",
                "배송 상태가 변경되었습니다.",
                redirectAttributes);
    }

    @PostMapping("/distribution/deliveries/{deliveryId}/status")
    public String updateDeliveryDetailStatus(
            @PathVariable("deliveryId") Long deliveryId,
            @RequestParam("status") DeliveryStatus status,
            @RequestParam(name = "note", required = false) String note,
            RedirectAttributes redirectAttributes) {

        return execute(
                () -> distributionService.updateDelivery(
                        deliveryId, status, note),
                "/distribution/deliveries/" + deliveryId,
                "배송 상태가 변경되고 이력에 기록되었습니다.",
                redirectAttributes);
    }

    @PostMapping("/distribution/deliveries/{deliveryId}/cancel")
    public String cancelDelivery(
            @PathVariable("deliveryId") Long deliveryId,
            @RequestParam("reason") String reason,
            @RequestParam("manager") String manager,
            RedirectAttributes redirectAttributes) {

        return execute(
                () -> distributionService.cancelDelivery(
                        deliveryId, reason, manager),
                "/distribution?view=cancelled",
                "배송이 취소되고 취소 내역에 보관되었습니다.",
                redirectAttributes);
    }

    @PostMapping("/distribution/orders/{orderId}/cancel")
    public String cancelOrder(
            @PathVariable("orderId") Long orderId,
            @RequestParam("reason") String reason,
            @RequestParam("manager") String manager,
            RedirectAttributes redirectAttributes) {

        return execute(
                () -> distributionService.cancelOrder(
                        orderId, reason, manager),
                "/distribution?view=cancelled_orders",
                "주문이 취소되고 예약 또는 출고 재고가 자동 반영되었습니다.",
                redirectAttributes);
    }

    @PostMapping("/distribution/deliveries/{deliveryId}/reschedule")
    public String rescheduleDelivery(
            @PathVariable("deliveryId") Long deliveryId,
            @RequestParam("expectedAt")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime expectedAt,
            @RequestParam("reason") String reason,
            RedirectAttributes redirectAttributes) {

        return execute(
                () -> distributionService.rescheduleDelivery(
                        deliveryId, expectedAt, reason),
                "/distribution?view=delayed",
                "변경 도착 예정일과 지연 사유가 저장되었습니다.",
                redirectAttributes);
    }

    @PostMapping("/distribution/deliveries/{deliveryId}/reactivate")
    public String reactivateDelivery(
            @PathVariable("deliveryId") Long deliveryId,
            @RequestParam("carrierName") String carrierName,
            @RequestParam("trackingNumber") String trackingNumber,
            RedirectAttributes redirectAttributes) {

        return execute(
                () -> distributionService.reactivateDelivery(
                        deliveryId, carrierName, trackingNumber),
                "/distribution?view=picked_up",
                "새 운송장으로 재배송이 등록되었습니다.",
                redirectAttributes);
    }

    @PostMapping("/distribution/deliveries/{deliveryId}/tracking")
    public String updateDeliveryTracking(
            @PathVariable("deliveryId") Long deliveryId,
            @RequestParam("carrierName") String carrierName,
            @RequestParam("trackingNumber") String trackingNumber,
            RedirectAttributes redirectAttributes) {

        return execute(
                () -> distributionService.updateTracking(
                        deliveryId, carrierName, trackingNumber),
                "/distribution?view=picked_up",
                "운송사와 운송장 번호가 수정되었습니다.",
                redirectAttributes);
    }

    @PostMapping("/distribution/deliveries/{deliveryId}/returns")
    public String requestReturn(
            @PathVariable("deliveryId") Long deliveryId,
            @RequestParam("reason") String reason,
            @RequestParam("manager") String manager,
            RedirectAttributes redirectAttributes) {
        return execute(
                () -> distributionService.requestReturn(
                        deliveryId, reason, manager),
                "/distribution?view=returns",
                "배송 완료 상품의 회수가 요청되었습니다.",
                redirectAttributes);
    }

    @PostMapping("/distribution/deliveries/{deliveryId}/returns/start")
    public String startReturn(
            @PathVariable("deliveryId") Long deliveryId,
            RedirectAttributes redirectAttributes) {
        return execute(
                () -> distributionService.startReturn(deliveryId),
                "/distribution?view=returns",
                "회수 배송이 시작되었습니다.",
                redirectAttributes);
    }

    @PostMapping("/distribution/deliveries/{deliveryId}/returns/receive")
    public String receiveReturn(
            @PathVariable("deliveryId") Long deliveryId,
            RedirectAttributes redirectAttributes) {
        return execute(
                () -> distributionService.receiveReturn(deliveryId),
                "/distribution?view=returns",
                "회수품이 도착하여 검수 대기 상태로 변경되었습니다.",
                redirectAttributes);
    }

    @PostMapping("/distribution/deliveries/{deliveryId}/returns/inspect")
    public String inspectReturn(
            @PathVariable("deliveryId") Long deliveryId,
            @RequestParam("result") String result,
            @RequestParam(name = "defectType", required = false)
            DefectType defectType,
            @RequestParam("note") String note,
            @RequestParam("inspector") String inspector,
            RedirectAttributes redirectAttributes) {
        return execute(
                () -> distributionService.inspectReturn(
                        deliveryId, "NORMAL".equals(result),
                        defectType, note, inspector),
                "/distribution?view=returns",
                "회수품 검수 결과가 재고 또는 불량 관리에 반영되었습니다.",
                redirectAttributes);
    }

    /*
     * 공통 성공 및 오류 처리
     */
    private String execute(
            Runnable action,
            String redirect,
            String successMessage,
            RedirectAttributes redirectAttributes) {

        try {
            action.run();

            redirectAttributes.addFlashAttribute(
                    "message",
                    successMessage);

        } catch (RuntimeException exception) {

            redirectAttributes.addFlashAttribute(
                    "error",
                    exception.getMessage());
        }

        return "redirect:" + redirect;
    }
}
