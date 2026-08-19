package com.ex.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import com.ex.dto.CreateOrderRequest;
import com.ex.entity.BinInventory;
import com.ex.entity.BinPurpose;
import com.ex.entity.Manufacturer;
import com.ex.entity.PaymentMethod;
import com.ex.entity.Product;
import com.ex.entity.ProductLot;
import com.ex.entity.CustomerOrder;
import com.ex.entity.OrderItem;
import com.ex.entity.Shipment;
import com.ex.entity.ShipmentItem;
import com.ex.entity.Warehouse;
import com.ex.entity.WarehouseBin;
import com.ex.repository.BinInventoryRepository;
import com.ex.repository.ManufacturerRepository;
import com.ex.repository.ProductLotRepository;
import com.ex.repository.ProductRepository;
import com.ex.repository.WarehouseBinRepository;
import com.ex.repository.WarehouseRepository;
import com.ex.repository.WarehouseAllocationRepository;

@SpringBootTest
@ActiveProfiles("test")
class OrderInventoryIntegrityTest {

    @Autowired private OrderService orderService;
    @Autowired private ManufacturerRepository manufacturerRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductLotRepository lotRepository;
    @Autowired private WarehouseRepository warehouseRepository;
    @Autowired private WarehouseBinRepository binRepository;
    @Autowired private BinInventoryRepository inventoryRepository;
    @Autowired private WarehouseAllocationRepository allocationRepository;
    @Autowired private WarehouseFulfillmentService fulfillmentService;
    @Autowired private SellableStockQuery sellableStockQuery;
    @Autowired private TransactionTemplate transactionTemplate;

    @Test
    void partialLotAllocationFailureRollsBackEveryStockLedger() {
        Fixture fixture = createFixture(List.of(
                new StockSpec("정상 사료", 2, 2),
                new StockSpec("불일치 사료", 1, 2)));

        CreateOrderRequest request = request(
                "부분롤백농장",
                fixture.products().stream()
                        .map(product -> new CreateOrderRequest.OrderLineRequest(
                                product.productId(), 2))
                        .toList());

        assertThrows(IllegalStateException.class,
                () -> orderService.createOrder(request));

        transactionTemplate.executeWithoutResult(status -> {
            ProductLot firstLot = lotRepository.findById(
                    fixture.products().get(0).lotId()).orElseThrow();
            ProductLot secondLot = lotRepository.findById(
                    fixture.products().get(1).lotId()).orElseThrow();
            assertEquals(2, firstLot.getLotQuantity());
            assertEquals(1, secondLot.getLotQuantity());
            assertEquals(2, inventoryRepository.findById(
                    fixture.products().get(0).inventoryId())
                    .orElseThrow().getQuantity());
            assertEquals(2, inventoryRepository.findById(
                    fixture.products().get(1).inventoryId())
                    .orElseThrow().getQuantity());
            assertEquals(2, productRepository.findById(
                    fixture.products().get(0).productId())
                    .orElseThrow().getTotalStock());
        });
    }

    @Test
    void concurrentOrdersCannotOversellLastBag() throws Exception {
        Fixture fixture = createFixture(List.of(
                new StockSpec("동시주문 사료", 1, 1)));
        Long productId = fixture.products().getFirst().productId();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<?> first = executor.submit(() -> placeConcurrentOrder(
                    request("동시농장A", List.of(
                            new CreateOrderRequest.OrderLineRequest(productId, 1))),
                    ready, start, successes, failures));
            Future<?> second = executor.submit(() -> placeConcurrentOrder(
                    request("동시농장B", List.of(
                            new CreateOrderRequest.OrderLineRequest(productId, 1))),
                    ready, start, successes, failures));
            ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        }

        assertEquals(1, successes.get());
        assertEquals(1, failures.get());
        transactionTemplate.executeWithoutResult(status -> {
            ProductLot lot = lotRepository.findById(
                    fixture.products().getFirst().lotId()).orElseThrow();
            BinInventory inventory = inventoryRepository.findById(
                    fixture.products().getFirst().inventoryId()).orElseThrow();
            Product product = productRepository.findById(productId).orElseThrow();
            assertEquals(1, lot.getLotQuantity());
            assertEquals(1, inventory.getQuantity());
            assertEquals(1, product.getTotalStock());
            assertEquals(0, sellableStockQuery.sellable(productId));
        });
    }

    @Test
    void concurrentOrdersAcrossDifferentLotsKeepProductTotalConsistent()
            throws Exception {
        Long productId = createTwoLotProduct();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();

        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<?> first = executor.submit(() -> placeConcurrentOrder(
                    request("다중LOT농장A", List.of(
                            new CreateOrderRequest.OrderLineRequest(productId, 1))),
                    ready, start, successes, failures));
            Future<?> second = executor.submit(() -> placeConcurrentOrder(
                    request("다중LOT농장B", List.of(
                            new CreateOrderRequest.OrderLineRequest(productId, 1))),
                    ready, start, successes, failures));
            ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        }

        assertEquals(2, successes.get());
        assertEquals(0, failures.get());
        transactionTemplate.executeWithoutResult(status -> {
            Product product = productRepository.findById(productId).orElseThrow();
            int lotTotal = lotRepository
                    .findByProductProductIdOrderByExpirationDateAsc(productId)
                    .stream().mapToInt(ProductLot::getLotQuantity).sum();
            int binTotal = inventoryRepository
                    .findByLotProductProductIdAndQuantityGreaterThanOrderByBinBinCodeAsc(
                            productId, -1)
                    .stream().mapToInt(BinInventory::getQuantity).sum();
            assertEquals(2, product.getTotalStock());
            assertEquals(2, lotTotal);
            assertEquals(2, binTotal);
            assertEquals(0, sellableStockQuery.sellable(productId));
        });
    }

    @Test
    void shipmentCreatesMissingPlanningAllocationInsteadOfFailing() {
        Fixture fixture = createFixture(List.of(
                new StockSpec("신규상품 출고 사료", 3, 3)));

        transactionTemplate.executeWithoutResult(status -> {
            ProductFixture productFixture = fixture.products().getFirst();
            Product product = productRepository.findById(
                    productFixture.productId()).orElseThrow();
            ProductLot lot = lotRepository.findById(
                    productFixture.lotId()).orElseThrow();
            Warehouse warehouse = warehouseRepository.findById(
                    fixture.warehouseId()).orElseThrow();
            CustomerOrder order = CustomerOrder.storefront(
                    "ALLOC-" + UUID.randomUUID(), "신규상품농장",
                    "010-1111-2222", "서울특별시 테스트로 1", "101호",
                    "정문", "테스트 출고", PaymentMethod.BANK_TRANSFER,
                    false, product.getPrice(), BigDecimal.ZERO,
                    BigDecimal.ZERO);
            order.assignFulfillmentWarehouse(warehouse, 0.0, "테스트 배정");
            OrderItem orderItem = new OrderItem(
                    order, product, lot, 1, product.getPrice());
            Shipment shipment = new Shipment(order, "tester", "test");
            ShipmentItem shipmentItem = new ShipmentItem(shipment, orderItem);

            fulfillmentService.deductStock(order, List.of(shipmentItem));

            var allocation = allocationRepository
                    .findByWarehouseWarehouseIdAndProductProductId(
                            warehouse.getWarehouseId(), product.getProductId())
                    .orElseThrow();
            // 배치 행은 계획값이 아니라 현재 판매 가능한 구역 재고를 반영한다.
            assertEquals(3, allocation.getCurrentStockQuantity());
            assertEquals(0, allocation.getTargetStockQuantity());
        });
    }

    private void placeConcurrentOrder(
            CreateOrderRequest request,
            CountDownLatch ready,
            CountDownLatch start,
            AtomicInteger successes,
            AtomicInteger failures) {
        ready.countDown();
        try {
            start.await(5, TimeUnit.SECONDS);
            orderService.createOrder(request);
            successes.incrementAndGet();
        } catch (RuntimeException exception) {
            failures.incrementAndGet();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            failures.incrementAndGet();
        }
    }

    private Fixture createFixture(List<StockSpec> specs) {
        return transactionTemplate.execute(status -> {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            Manufacturer manufacturer = manufacturerRepository.save(
                    new Manufacturer("무결성 제조사 " + suffix, "담당자", "010-0000-0000"));
            Warehouse warehouse = warehouseRepository.save(new Warehouse(
                    "T" + suffix.substring(0, 7),
                    "무결성 창고 " + suffix,
                    "서울특별시 테스트로 1",
                    "테스트 권역",
                    "무결성 테스트",
                    37.5,
                    127.0,
                    900));
            WarehouseBin bin = binRepository.save(new WarehouseBin(
                    warehouse, "ST-01", "테스트", BinPurpose.STORAGE,
                    1, 1, 2, 2, 100, "주문 무결성 테스트"));

            List<ProductFixture> products = specs.stream().map(spec -> {
                Product product = new Product(
                        manufacturer, spec.name(), "소", BigDecimal.valueOf(25),
                        BigDecimal.valueOf(30_000), 0, 12, "테스트 상품");
                product.changeStock(spec.lotQuantity());
                product = productRepository.save(product);
                ProductLot lot = lotRepository.save(new ProductLot(
                        product,
                        "LOT-TEST-" + UUID.randomUUID(),
                        LocalDate.now().minusDays(1),
                        LocalDate.now().plusMonths(6),
                        spec.lotQuantity()));
                BinInventory inventory = inventoryRepository.save(
                        new BinInventory(lot, bin, spec.binQuantity()));
                return new ProductFixture(
                        product.getProductId(), lot.getLotId(),
                        inventory.getBinInventoryId());
            }).toList();
            products.forEach(product -> assertNotNull(product.productId()));
            return new Fixture(warehouse.getWarehouseId(), products);
        });
    }

    private Long createTwoLotProduct() {
        return transactionTemplate.execute(status -> {
            String suffix = UUID.randomUUID().toString().substring(0, 8);
            Manufacturer manufacturer = manufacturerRepository.save(
                    new Manufacturer("다중 LOT 제조사 " + suffix,
                            "담당자", "010-0000-0000"));
            Warehouse warehouse = warehouseRepository.save(new Warehouse(
                    "M" + suffix.substring(0, 7),
                    "다중 LOT 창고 " + suffix,
                    "서울특별시 테스트로 2",
                    "테스트 권역", "동시성 테스트",
                    37.5, 127.0, 901));
            WarehouseBin bin = binRepository.save(new WarehouseBin(
                    warehouse, "CT-01", "CT", BinPurpose.STORAGE,
                    1, 1, 2, 2, 100, "다중 LOT 테스트"));
            Product product = new Product(
                    manufacturer, "다중 LOT 동시주문 사료", "소",
                    BigDecimal.valueOf(25), BigDecimal.valueOf(30_000),
                    0, 12, "다중 LOT 테스트");
            product.changeStock(2);
            product = productRepository.save(product);
            for (int index = 0; index < 2; index++) {
                ProductLot lot = lotRepository.save(new ProductLot(
                        product,
                        "LOT-MULTI-" + suffix + "-" + index,
                        LocalDate.now().minusDays(2 - index),
                        LocalDate.now().plusMonths(5 + index),
                        1));
                inventoryRepository.save(new BinInventory(lot, bin, 1));
            }
            return product.getProductId();
        });
    }

    private CreateOrderRequest request(
            String customer,
            List<CreateOrderRequest.OrderLineRequest> items) {
        return new CreateOrderRequest(
                customer, "010-1111-2222", "서울특별시 테스트로 1",
                "101호", "정문", "테스트 주문",
                PaymentMethod.BANK_TRANSFER, false, items);
    }

    private record StockSpec(String name, int lotQuantity, int binQuantity) {}
    private record ProductFixture(Long productId, Long lotId, Long inventoryId) {}
    private record Fixture(Long warehouseId, List<ProductFixture> products) {}
}
