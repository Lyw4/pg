package com.ex.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.YearMonth;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.ex.entity.CustomerOrder;
import com.ex.repository.CustomerOrderRepository;
import com.ex.service.AdminFeedModelService;

@SpringBootTest(properties = "feedflow.demo.monthly-orders-enabled=true")
@ActiveProfiles("test")
class DemoOrderDataInitializerTest {

    private static final List<Long> EXPECTED_COUNTS =
            List.of(9L, 14L, 12L, 18L, 23L, 21L);

    @Autowired
    private CustomerOrderRepository orderRepository;

    @Autowired
    private AdminFeedModelService adminFeedModelService;

    @Autowired
    private DemoOrderDataInitializer initializer;

    @Test
    void seedsSixMonthsOfIdempotentDemoOrdersForAnalytics() throws Exception {
        List<CustomerOrder> demoOrders = demoOrders();
        assertThat(demoOrders).hasSize(97);

        Map<YearMonth, Long> countsByMonth = demoOrders.stream()
                .collect(Collectors.groupingBy(
                        order -> YearMonth.from(order.getCreatedAt()),
                        TreeMap::new,
                        Collectors.counting()));
        assertThat(countsByMonth.values()).containsExactlyElementsOf(
                EXPECTED_COUNTS);

        List<Long> analyticsCounts = adminFeedModelService.analytics()
                .monthlyOrders().stream()
                .map(AdminFeedModelService.ChartPoint::value)
                .toList();
        for (int index = 0; index < EXPECTED_COUNTS.size(); index++) {
            assertThat(analyticsCounts.get(index))
                    .isGreaterThanOrEqualTo(EXPECTED_COUNTS.get(index));
        }

        initializer.run(new DefaultApplicationArguments(new String[0]));
        assertThat(demoOrders()).hasSize(97);
    }

    private List<CustomerOrder> demoOrders() {
        YearMonth currentMonth = YearMonth.now();
        LocalDate today = LocalDate.now();
        List<CustomerOrder> result = new ArrayList<>();
        for (int monthIndex = 0;
                monthIndex < EXPECTED_COUNTS.size();
                monthIndex++) {
            YearMonth month = currentMonth.minusMonths(
                    EXPECTED_COUNTS.size() - 1L - monthIndex);
            int count = EXPECTED_COUNTS.get(monthIndex).intValue();
            int availableDays = month.equals(currentMonth)
                    ? today.getDayOfMonth()
                    : month.lengthOfMonth();
            for (int orderIndex = 0; orderIndex < count; orderIndex++) {
                LocalDateTime orderedAt = DemoOrderDataInitializer.orderedAt(
                        month, availableDays, orderIndex, count);
                String orderNumber = DemoOrderDataInitializer.orderNumber(
                        orderedAt, month, orderIndex);
                result.add(orderRepository.findByOrderNumber(orderNumber)
                        .orElseThrow());
            }
        }
        return result;
    }
}
