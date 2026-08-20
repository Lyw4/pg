package com.ex.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import com.ex.dto.CreateOrderRequest;
import com.ex.dto.OrderResponse;
import com.ex.entity.BinInventory;
import com.ex.entity.BinPurpose;
import com.ex.entity.CustomerOrder;
import com.ex.entity.CustomerOrder.OrderStatus;
import com.ex.entity.Manufacturer;
import com.ex.entity.PaymentMethod;
import com.ex.entity.PaymentStatus;
import com.ex.entity.Product;
import com.ex.entity.ProductLot;
import com.ex.entity.Shipment;
import com.ex.entity.Warehouse;
import com.ex.entity.WarehouseBin;
import com.ex.repository.BinInventoryRepository;
import com.ex.repository.CustomerOrderRepository;
import com.ex.repository.ManufacturerRepository;
import com.ex.repository.ProductLotRepository;
import com.ex.repository.ProductRepository;
import com.ex.repository.ShipmentRepository;
import com.ex.repository.WarehouseBinRepository;
import com.ex.repository.WarehouseRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 주문 버튼을 누른 뒤 결제와 출고까지 이어지는 전체 흐름을 검증합니다.
 *
 * <p>재고가 두 단계로 관리되는 것이 이 시스템의 핵심입니다. 주문 시점에는 로트
 * 잔량을 건드리지 않고 판매 가능 수량에서만 빼두고(예약), 실제 차감은 출고를
 * 확정할 때 일어납니다. 각 단계에서 물리 재고와 판매 가능 재고가 어떻게
 * 달라지는지 함께 검증합니다.
 */
@SpringBootTest
@ActiveProfiles("test")
class OrderFulfillmentScenarioTest {

    @Autowired private OrderService orderService;
    @Autowired private PaymentApplyService paymentApplyService;
    @Autowired private ShipmentService shipmentService;
    @Autowired private SellableStockQuery sellableStockQuery;
    @Autowired private ManufacturerRepository manufacturerRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductLotRepository lotRepository;
    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private WarehouseBinRepository binRepository;
    @Autowired private BinInventoryRepository inventoryRepository;
    @Autowired private CustomerOrderRepository orderRepository;
    @Autowired private ShipmentRepository shipmentRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private ObjectMapper objectMapper;

    private static final int STOCK = 10;
    private static final int ORDER_QUANTITY = 3;

    @Test
    void orderReservesStockThenShipmentDeductsItOnce() {
        Fixture fixture = createFixture();

        // 1단계: 주문 접수. 물리 재고는 그대로이고 판매 가능 수량만 줄어듭니다.
        OrderResponse created = orderService.createOrder(
                request(fixture.productId(), ORDER_QUANTITY));

        assertEquals(OrderStatus.PAYMENT_PENDING, created.status());
        assertEquals(PaymentStatus.READY, created.paymentStatus());
        transactionTemplate.executeWithoutResult(status -> {
            assertEquals(STOCK, lotQuantity(fixture));
            assertEquals(STOCK, binQuantity(fixture));
            assertEquals(STOCK - ORDER_QUANTITY,
                    sellableStockQuery.sellable(fixture.productId()));
            assertFalse(order(created).isInventoryCommitted());
        });

        // 2단계: 결제 승인. 재고 상태는 그대로이고 주문만 결제 완료로 넘어갑니다.
        approvePayment(created);

        transactionTemplate.executeWithoutResult(status -> {
            CustomerOrder paid = order(created);
            assertEquals(OrderStatus.PAID, paid.getStatus());
            assertEquals(PaymentStatus.DONE, paid.getPaymentStatus());
            assertEquals(STOCK, lotQuantity(fixture));
            assertEquals(STOCK - ORDER_QUANTITY,
                    sellableStockQuery.sellable(fixture.productId()));
            assertFalse(paid.isInventoryCommitted());
        });

        // 3단계: 출고 지시부터 완료까지. 이 시점에 물리 재고가 실제로 빠집니다.
        Long shipmentId = completeShipment(created);

        transactionTemplate.executeWithoutResult(status -> {
            CustomerOrder shipped = order(created);
            assertEquals(OrderStatus.SHIPPING, shipped.getStatus());
            assertTrue(shipped.isInventoryCommitted());
            assertEquals(STOCK - ORDER_QUANTITY, lotQuantity(fixture));
            assertEquals(STOCK - ORDER_QUANTITY, binQuantity(fixture));
            assertEquals(STOCK - ORDER_QUANTITY, productStock(fixture));
            // 물리 재고가 줄고 예약이 해제되므로 판매 가능 수량은 유지됩니다.
            // 이 값이 4가 되면 재고를 두 번 뺀 것입니다.
            assertEquals(STOCK - ORDER_QUANTITY,
                    sellableStockQuery.sellable(fixture.productId()));
            assertEquals(Shipment.ShipmentStatus.SHIPPED,
                    shipmentRepository.findById(shipmentId)
                            .orElseThrow().getStatus());
        });
    }

    @Test
    void shipmentIsRejectedUntilPaymentIsApproved() {
        Fixture fixture = createFixture();
        OrderResponse created = orderService.createOrder(
                request(fixture.productId(), ORDER_QUANTITY));

        // 결제 전 주문은 출고 지시를 만들 수 없어야 합니다. 결제 없이 출고되면
        // 재고만 빠지고 대금은 회수되지 않습니다.
        Long orderId = created.id();
        assertThrows(IllegalStateException.class,
                () -> shipmentService.create(orderId, "출고담당", "결제 전 출고 시도"));

        transactionTemplate.executeWithoutResult(status ->
                assertEquals(STOCK, lotQuantity(fixture)));
    }

    @Test
    void cancellingBeforeShipmentReleasesReservationWithoutTouchingLotQuantity() {
        Fixture fixture = createFixture();
        OrderResponse created = orderService.createOrder(
                request(fixture.productId(), ORDER_QUANTITY));
        approvePayment(created);

        paymentApplyService.applyForWebhook(
                created.orderNumber(),
                providerTransactionId(created),
                payment(created, providerTransactionId(created), "cancelled"));

        transactionTemplate.executeWithoutResult(status -> {
            CustomerOrder cancelled = order(created);
            assertEquals(OrderStatus.CANCELLED, cancelled.getStatus());
            assertEquals(PaymentStatus.CANCELLED, cancelled.getPaymentStatus());
            // 출고 전이므로 로트 잔량은 처음부터 줄지 않았습니다. 취소로 예약만
            // 풀리고 판매 가능 수량이 원래대로 돌아옵니다.
            assertEquals(STOCK, lotQuantity(fixture));
            assertEquals(STOCK, binQuantity(fixture));
            assertEquals(STOCK, sellableStockQuery.sellable(fixture.productId()));
        });
    }

    @Test
    void cancellingCompletedShipmentRestoresDeductedStock() {
        Fixture fixture = createFixture();
        OrderResponse created = orderService.createOrder(
                request(fixture.productId(), ORDER_QUANTITY));
        approvePayment(created);
        Long shipmentId = completeShipment(created);

        shipmentService.cancelCompleted(shipmentId, "고객 반품 요청");

        transactionTemplate.executeWithoutResult(status -> {
            assertEquals(STOCK, lotQuantity(fixture));
            assertEquals(STOCK, binQuantity(fixture));
            assertEquals(STOCK, productStock(fixture));
            assertFalse(order(created).isInventoryCommitted());
            // 주문이 결제 완료 상태로 되돌아가므로 다시 예약이 잡힙니다.
            assertEquals(OrderStatus.PAID, order(created).getStatus());
            assertEquals(STOCK - ORDER_QUANTITY,
                    sellableStockQuery.sellable(fixture.productId()));
        });
    }

    @Test
    void orderIsRejectedWhenRequestedQuantityExceedsSellableStock() {
        Fixture fixture = createFixture();

        // 재고보다 많이 주문하면 출고 창고를 배정하는 단계에서 먼저 걸립니다.
        // commitInventory의 재고 검사까지 가지 않기 때문에 여기서 나오는 예외는
        // IllegalStateException입니다.
        assertThrows(IllegalStateException.class,
                () -> orderService.createOrder(
                        request(fixture.productId(), STOCK + 1)));

        transactionTemplate.executeWithoutResult(status -> {
            assertEquals(STOCK, lotQuantity(fixture));
            assertEquals(STOCK, sellableStockQuery.sellable(fixture.productId()));
        });
    }

    private Long completeShipment(OrderResponse created) {
        shipmentService.create(created.id(), "출고담당", "시나리오 검증");
        Long shipmentId = transactionTemplate.execute(status ->
                shipmentRepository.findByOrderOrderId(created.id())
                        .orElseThrow().getShipmentId());
        shipmentService.startPicking(shipmentId, "피킹담당");
        shipmentService.inspect(shipmentId, "검수담당");
        shipmentService.complete(shipmentId, "출고담당");
        return shipmentId;
    }

    private void approvePayment(OrderResponse created) {
        String impUid = "imp_" + UUID.randomUUID();
        paymentApplyService.applyForWebhook(
                created.orderNumber(), impUid,
                payment(created, impUid, "paid"));
    }

    private String providerTransactionId(OrderResponse created) {
        return transactionTemplate.execute(status ->
                order(created).getProviderTransactionId());
    }

    private CustomerOrder order(OrderResponse created) {
        return orderRepository.findById(created.id()).orElseThrow();
    }

    private int lotQuantity(Fixture fixture) {
        return lotRepository.findById(fixture.lotId())
                .orElseThrow().getLotQuantity();
    }

    private int binQuantity(Fixture fixture) {
        return inventoryRepository.findById(fixture.inventoryId())
                .orElseThrow().getQuantity();
    }

    private int productStock(Fixture fixture) {
        return productRepository.findById(fixture.productId())
                .orElseThrow().getTotalStock();
    }

    private ObjectNode payment(
            OrderResponse order,
            String impUid,
            String status) {
        ObjectNode payment = objectMapper.createObjectNode();
        payment.put("imp_uid", impUid);
        payment.put("merchant_uid", order.orderNumber());
        payment.put("amount", order.totalAmount());
        payment.put("status", status);
        payment.put("pay_method", "card");
        return payment;
    }

    private CreateOrderRequest request(Long productId, int quantity) {
        return new CreateOrderRequest(
                "시나리오농장",
                "010-3333-4444",
                "서울특별시 테스트로 9",
                "201호",
                "정문",
                "문 앞에 놓아주세요",
                PaymentMethod.CARD,
                false,
                List.of(new CreateOrderRequest.OrderLineRequest(
                        productId, quantity)));
    }

    private Fixture createFixture() {
        return transactionTemplate.execute(status -> {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            Manufacturer manufacturer = manufacturerRepository.save(
                    new Manufacturer(
                            "시나리오 제조사 " + suffix, "담당자", "010-0000-0000"));
            Warehouse warehouse = warehouseRepository.save(new Warehouse(
                    "S" + suffix.substring(0, 7),
                    "시나리오 창고 " + suffix,
                    "서울특별시 테스트로 9",
                    "테스트 권역",
                    "주문 시나리오 검증",
                    37.5,
                    127.0,
                    950));
            // 보관 구역은 축종 전용 존만 허용합니다(WmsZonePolicy). 소 사료는
            // CT 구역이어야 반품 재입고까지 정상 동작합니다.
            WarehouseBin bin = binRepository.save(new WarehouseBin(
                    warehouse, "SC-01", "CT", BinPurpose.STORAGE,
                    1, 1, 2, 2, 100, "주문 시나리오 테스트"));

            Product product = new Product(
                    manufacturer, "시나리오 검증 사료 " + suffix, "소",
                    BigDecimal.valueOf(25), BigDecimal.valueOf(30_000),
                    0, 12, "주문 시나리오 테스트");
            product.changeStock(STOCK);
            product = productRepository.save(product);
            ProductLot lot = lotRepository.save(new ProductLot(
                    product,
                    "LOT-SCENARIO-" + UUID.randomUUID(),
                    LocalDate.now().minusDays(1),
                    LocalDate.now().plusMonths(6),
                    STOCK));
            product.addLot(lot);
            BinInventory inventory = inventoryRepository.save(
                    new BinInventory(lot, bin, STOCK));
            return new Fixture(
                    product.getProductId(),
                    lot.getLotId(),
                    inventory.getBinInventoryId());
        });
    }

    private record Fixture(Long productId, Long lotId, Long inventoryId) {
    }
}
