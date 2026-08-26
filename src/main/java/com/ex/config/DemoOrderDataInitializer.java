package com.ex.config;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ex.entity.CustomerOrder;
import com.ex.entity.FarmCustomer;
import com.ex.entity.FarmCustomer.CustomerStatus;
import com.ex.entity.OrderItem;
import com.ex.entity.PaymentMethod;
import com.ex.entity.Product;
import com.ex.entity.ProductLot;
import com.ex.entity.Shipment;
import com.ex.entity.ShipmentItem;
import com.ex.repository.CustomerOrderRepository;
import com.ex.repository.FarmCustomerRepository;
import com.ex.repository.ProductRepository;
import com.ex.repository.ShipmentItemRepository;
import com.ex.repository.ShipmentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 발표·기능 검증용 가상 농장에 최근 6개월의 출고 완료 이력을 만듭니다.
 *
 * <p>농장에 등록된 월 예상 사료량을 기준으로 계절 변동을 적용하며,
 * 실제 가입 농장에는 임의의 구매·출고 이력을 만들지 않습니다.</p>
 */
@Slf4j
@Component
@Order(110)
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "feedflow.demo.farm-deliveries-enabled",
        havingValue = "true")
public class DemoOrderDataInitializer implements ApplicationRunner {

    private static final double[] MONTHLY_DELIVERY_FACTORS = {
            .93, .97, 1.01, .99, 1.04, 1.08
    };
    private static final DateTimeFormatter COMPACT_MONTH =
            DateTimeFormatter.ofPattern("yyyyMM");
    private static final DateTimeFormatter COMPACT_DATE =
            DateTimeFormatter.BASIC_ISO_DATE;

    private final CustomerOrderRepository orderRepository;
    private final FarmCustomerRepository farmCustomerRepository;
    private final ProductRepository productRepository;
    private final ShipmentRepository shipmentRepository;
    private final ShipmentItemRepository shipmentItemRepository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<FarmCustomer> farms = farmCustomerRepository
                .findAllByOrderByAssignedWarehouseDisplayOrderAscFarmNameAsc()
                .stream()
                .filter(FarmCustomer::isDemoData)
                .filter(farm -> farm.getStatus() == CustomerStatus.ACTIVE)
                .filter(farm -> farm.getMonthlyFeedQuantity() > 0)
                .toList();
        List<Product> products = productRepository
                .findAllByActiveTrueOrderByProductIdAsc();
        if (farms.isEmpty() || products.isEmpty()) {
            log.warn("농장 납품 이력을 생성할 기준 농장 또는 상품이 없습니다.");
            return;
        }

        YearMonth currentMonth = YearMonth.now();
        LocalDate today = LocalDate.now();
        int createdOrders = 0;
        long createdQuantity = 0;

        for (int monthIndex = 0;
                monthIndex < MONTHLY_DELIVERY_FACTORS.length;
                monthIndex++) {
            YearMonth month = currentMonth.minusMonths(
                    MONTHLY_DELIVERY_FACTORS.length - 1L - monthIndex);
            for (int farmIndex = 0; farmIndex < farms.size(); farmIndex++) {
                FarmCustomer farm = farms.get(farmIndex);
                LocalDate deliveredOn = deliveryDate(month, farm);
                if (deliveredOn.isAfter(today)) {
                    continue;
                }
                String orderNumber = orderNumber(month, farm.getFarmCode());
                if (orderRepository.findByOrderNumber(orderNumber).isPresent()
                        || orderRepository
                                .findByFarmCustomerFarmCustomerIdAndScheduledDeliveryDate(
                                        farm.getFarmCustomerId(), deliveredOn)
                                .isPresent()) {
                    continue;
                }

                Product product = productFor(farm, products);
                ProductLot lot = product.getLots().stream()
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException(
                                "농장 납품 이력에 사용할 LOT가 없습니다: "
                                        + product.getName()));
                int quantity = deliveredQuantity(
                        farm.getMonthlyFeedQuantity(), monthIndex, farmIndex);
                BigDecimal productAmount = product.getPrice()
                        .multiply(BigDecimal.valueOf(quantity));

                CustomerOrder order = CustomerOrder.storefront(
                        orderNumber,
                        farm.getFarmName(),
                        farm.getPhone(),
                        farm.getAddress(),
                        "농장 사료창고",
                        "지정 하역장",
                        "정기 납품",
                        PaymentMethod.BANK_TRANSFER,
                        true,
                        productAmount,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO);
                order.linkFarmCustomer(farm);
                order.assignFulfillmentWarehouse(
                        farm.getAssignedWarehouse(),
                        farm.getDistanceKm(),
                        "농장 담당 창고");
                order.markScheduledDelivery(deliveredOn, "DEMO_HISTORY");
                order.changeStatus(CustomerOrder.OrderStatus.DELIVERED);
                OrderItem orderItem = new OrderItem(
                        order, product, lot, quantity, product.getPrice());
                order.addItem(orderItem);
                orderRepository.saveAndFlush(order);

                Shipment shipment = shipmentRepository.saveAndFlush(
                        new Shipment(order, "시연 출고 담당자", "농장 정기 납품 이력"));
                ShipmentItem shipmentItem = new ShipmentItem(
                        shipment, orderItem);
                shipment.startPicking("시연 피킹 담당자");
                shipmentItem.completePicking();
                shipment.inspect("시연 검수 담당자");
                shipment.complete("시연 출고 담당자");
                shipmentItemRepository.save(shipmentItem);
                shipmentRepository.flush();

                LocalDateTime orderedAt = deliveredOn.minusDays(2)
                        .atTime(10 + farmIndex % 5, 10);
                LocalDateTime shippedAt = deliveredOn
                        .atTime(8 + farmIndex % 4, 30);
                jdbcTemplate.update("""
                        update customer_order
                        set created_at = ?, updated_at = ?,
                            payment_approved_at = ?, order_channel = 'FARM'
                        where order_id = ?
                        """,
                        Timestamp.valueOf(orderedAt),
                        Timestamp.valueOf(shippedAt),
                        Timestamp.valueOf(orderedAt.plusMinutes(5)),
                        order.getOrderId());
                jdbcTemplate.update("""
                        update shipment
                        set shipment_no = ?, created_at = ?, shipped_at = ?
                        where shipment_id = ?
                        """,
                        shipmentNumber(deliveredOn, shipment.getShipmentId()),
                        Timestamp.valueOf(deliveredOn.minusDays(1).atTime(9, 0)),
                        Timestamp.valueOf(shippedAt),
                        shipment.getShipmentId());
                createdOrders++;
                createdQuantity += quantity;
            }
        }

        log.info("가상 농장 최근 6개월 출고 완료 이력 {}건, {}포를 생성했습니다.",
                createdOrders, createdQuantity);
    }

    static int deliveredQuantity(
            int monthlyFeedQuantity,
            int monthIndex,
            int farmIndex) {
        double farmVariation = 1 + ((farmIndex % 5) - 2) * .005;
        return Math.max(1, (int) Math.round(
                monthlyFeedQuantity
                        * MONTHLY_DELIVERY_FACTORS[monthIndex]
                        * farmVariation));
    }

    static LocalDate deliveryDate(YearMonth month, FarmCustomer farm) {
        int requestedDay = Math.max(1, farm.getRecurringDeliveryDay());
        return month.atDay(Math.min(requestedDay, month.lengthOfMonth()));
    }

    static String orderNumber(YearMonth month, String farmCode) {
        return "FF-FARM-" + month.format(COMPACT_MONTH) + "-" + farmCode;
    }

    private Product productFor(FarmCustomer farm, List<Product> products) {
        return products.stream()
                .filter(product -> product.getName().equals(
                        farm.getPreferredFeed()))
                .findFirst()
                .orElseGet(() -> products.stream()
                        .filter(product -> product.getAnimalType().equals(
                                farm.getAnimalType()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException(
                                "농장 축종에 맞는 납품 상품이 없습니다: "
                                        + farm.getFarmName())));
    }

    private String shipmentNumber(LocalDate deliveredOn, Long shipmentId) {
        return "SHP-" + deliveredOn.format(COMPACT_DATE) + "-" + shipmentId;
    }
}
