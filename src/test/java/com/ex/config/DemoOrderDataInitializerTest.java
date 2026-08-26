package com.ex.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.ex.entity.CustomerOrder;
import com.ex.entity.FarmCustomer;
import com.ex.entity.FarmCustomer.CustomerStatus;
import com.ex.entity.Shipment.ShipmentStatus;
import com.ex.repository.CustomerOrderRepository;
import com.ex.repository.FarmCustomerRepository;
import com.ex.repository.ShipmentRepository;
import com.ex.service.AdminFeedModelService;

@SpringBootTest(properties = "feedflow.demo.farm-deliveries-enabled=true")
@ActiveProfiles("test")
@Transactional
class DemoOrderDataInitializerTest {

    @Autowired
    private CustomerOrderRepository orderRepository;

    @Autowired
    private FarmCustomerRepository farmCustomerRepository;

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private AdminFeedModelService adminFeedModelService;

    @Autowired
    private DemoOrderDataInitializer initializer;

    @Test
    void seedsIdempotentCompletedDeliveriesFromActiveDemoFarms()
            throws Exception {
        List<FarmCustomer> farms = activeDemoFarms();
        Map<YearMonth, Long> expected = expectedMonthlyQuantities(farms);
        List<CustomerOrder> seededOrders = seededOrders();

        assertThat(farms).hasSize(40);
        for (String warehouseCode : List.of("W01", "W02", "W03", "W04", "W05")) {
            assertThat(farms.stream()
                    .filter(farm -> farm.getAssignedWarehouse().getCode()
                            .equals(warehouseCode)))
                    .hasSize(8);
        }
        assertThat(seededOrders).hasSize((int) expectedDeliveryCount(farms));
        assertThat(seededOrders).allSatisfy(order -> {
            assertThat(order.getFarmCustomer()).isNotNull();
            assertThat(order.getFarmCustomer().getStatus())
                    .isEqualTo(CustomerStatus.ACTIVE);
            assertThat(order.getStatus())
                    .isEqualTo(CustomerOrder.OrderStatus.DELIVERED);
            assertThat(shipmentRepository
                    .findByOrderOrderId(order.getOrderId())
                    .orElseThrow()
                    .getStatus()).isEqualTo(ShipmentStatus.SHIPPED);
        });

        List<Long> analyticsQuantities = adminFeedModelService.analytics()
                .monthlyDeliveries().stream()
                .map(AdminFeedModelService.ChartPoint::value)
                .toList();
        assertThat(analyticsQuantities)
                .containsExactlyElementsOf(expected.values());

        initializer.run(new DefaultApplicationArguments(new String[0]));
        assertThat(seededOrders()).hasSameSizeAs(seededOrders);
    }

    private List<FarmCustomer> activeDemoFarms() {
        return farmCustomerRepository
                .findAllByOrderByAssignedWarehouseDisplayOrderAscFarmNameAsc()
                .stream()
                .filter(FarmCustomer::isDemoData)
                .filter(farm -> farm.getStatus() == CustomerStatus.ACTIVE)
                .filter(farm -> farm.getMonthlyFeedQuantity() > 0)
                .toList();
    }

    private Map<YearMonth, Long> expectedMonthlyQuantities(
            List<FarmCustomer> farms) {
        Map<YearMonth, Long> result = new LinkedHashMap<>();
        YearMonth current = YearMonth.now();
        LocalDate today = LocalDate.now();
        for (int monthIndex = 0; monthIndex < 6; monthIndex++) {
            YearMonth month = current.minusMonths(5L - monthIndex);
            long quantity = 0;
            for (int farmIndex = 0; farmIndex < farms.size(); farmIndex++) {
                FarmCustomer farm = farms.get(farmIndex);
                if (!DemoOrderDataInitializer.deliveryDate(month, farm)
                        .isAfter(today)) {
                    quantity += DemoOrderDataInitializer.deliveredQuantity(
                            farm.getMonthlyFeedQuantity(),
                            monthIndex,
                            farmIndex);
                }
            }
            result.put(month, quantity);
        }
        return result;
    }

    private long expectedDeliveryCount(List<FarmCustomer> farms) {
        YearMonth current = YearMonth.now();
        LocalDate today = LocalDate.now();
        long count = 0;
        for (int monthIndex = 0; monthIndex < 6; monthIndex++) {
            YearMonth month = current.minusMonths(5L - monthIndex);
            count += farms.stream()
                    .filter(farm -> !DemoOrderDataInitializer
                            .deliveryDate(month, farm).isAfter(today))
                    .count();
        }
        return count;
    }

    private List<CustomerOrder> seededOrders() {
        return orderRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(order -> order.getOrderNumber() != null)
                .filter(order -> order.getOrderNumber().startsWith("FF-FARM-"))
                .toList();
    }
}
