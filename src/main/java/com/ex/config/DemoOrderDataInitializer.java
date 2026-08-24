package com.ex.config;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ex.entity.CustomerOrder;
import com.ex.entity.OrderItem;
import com.ex.entity.PaymentMethod;
import com.ex.entity.Product;
import com.ex.repository.CustomerOrderRepository;
import com.ex.repository.ProductRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** 발표 화면에서 최근 6개월의 주문 흐름을 확인하기 위한 로컬 시연 데이터입니다. */
@Slf4j
@Component
@Order(110)
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "feedflow.demo.monthly-orders-enabled",
        havingValue = "true")
public class DemoOrderDataInitializer implements ApplicationRunner {

    private static final int[] MONTHLY_ORDER_COUNTS = {9, 14, 12, 18, 23, 21};
    private static final List<String> CUSTOMER_NAMES = List.of(
            "푸른목장", "한결축산", "새봄농장", "대지양돈", "해뜰농원",
            "정다운목장", "우리축산", "늘푸른농장");
    private static final List<String> ADDRESSES = List.of(
            "경기도 이천시", "충청남도 홍성군", "전북특별자치도 정읍시",
            "경상북도 상주시", "강원특별자치도 횡성군", "경상남도 밀양시");
    private static final List<String> DETAIL_ADDRESSES = List.of(
            "농장 정문 오른쪽 창고", "제2사료창고", "축사 관리동",
            "관리사무소 뒤편", "저온창고 앞", "축사 동문 하역장");

    private final CustomerOrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<Product> products = productRepository
                .findAllByActiveTrueOrderByProductIdAsc();
        if (products.isEmpty()) {
            log.warn("시연 주문을 생성할 활성 상품이 없습니다.");
            return;
        }

        YearMonth currentMonth = YearMonth.now();
        LocalDate today = LocalDate.now();
        int created = 0;

        for (int monthIndex = 0;
                monthIndex < MONTHLY_ORDER_COUNTS.length;
                monthIndex++) {
            YearMonth month = currentMonth.minusMonths(
                    MONTHLY_ORDER_COUNTS.length - 1L - monthIndex);
            int count = MONTHLY_ORDER_COUNTS[monthIndex];
            int availableDays = month.equals(currentMonth)
                    ? today.getDayOfMonth()
                    : month.lengthOfMonth();

            for (int orderIndex = 0; orderIndex < count; orderIndex++) {
                LocalDateTime orderedAt = orderedAt(
                        month, availableDays, orderIndex, count);
                String orderNumber = orderNumber(orderedAt, month, orderIndex);
                String legacyOrderNumber = "DEMO-%s-%03d".formatted(
                        month.toString().replace("-", ""), orderIndex + 1);
                if (orderRepository.findByOrderNumber(orderNumber).isPresent()) {
                    continue;
                }

                String phone = phoneNumber(monthIndex, orderIndex);
                String detailAddress = DETAIL_ADDRESSES.get(
                        (monthIndex + orderIndex * 2) % DETAIL_ADDRESSES.size());
                if (orderRepository.findByOrderNumber(legacyOrderNumber)
                        .isPresent()) {
                    jdbcTemplate.update("""
                            update customer_order
                            set order_number = ?,
                                phone = ?,
                                recipient_phone = ?,
                                detail_address = ?
                            where order_number = ?
                            """,
                            orderNumber,
                            phone,
                            phone,
                            detailAddress,
                            legacyOrderNumber);
                    created++;
                    continue;
                }

                Product product = products.get(
                        (monthIndex * 7 + orderIndex) % products.size());
                int quantity = 2 + (orderIndex % 7);
                BigDecimal productAmount = product.getPrice()
                        .multiply(BigDecimal.valueOf(quantity));
                BigDecimal discount = orderIndex % 5 == 0
                        ? BigDecimal.valueOf(5_000)
                        : BigDecimal.ZERO;
                String customerName = CUSTOMER_NAMES.get(
                        (monthIndex + orderIndex) % CUSTOMER_NAMES.size());
                String address = ADDRESSES.get(
                        (monthIndex * 2 + orderIndex) % ADDRESSES.size());

                CustomerOrder order = CustomerOrder.storefront(
                        orderNumber,
                        customerName,
                        phone,
                        address,
                        detailAddress,
                        "창고 앞 하차",
                        "도착 전 연락 부탁드립니다.",
                        paymentMethod(orderIndex),
                        false,
                        productAmount,
                        BigDecimal.ZERO,
                        discount);
                order.changeStatus(status(month, currentMonth, orderIndex));
                order.addItem(new OrderItem(
                        order, product, quantity, product.getPrice()));
                orderRepository.saveAndFlush(order);

                jdbcTemplate.update("""
                        update customer_order
                        set created_at = ?, updated_at = ?
                        where order_id = ?
                        """,
                        Timestamp.valueOf(orderedAt),
                        Timestamp.valueOf(orderedAt.plusMinutes(5)),
                        order.getOrderId());
                created++;
            }
        }

        log.info("최근 6개월 발표용 주문 데이터 {}건을 생성했습니다.", created);
    }

    static LocalDateTime orderedAt(
            YearMonth month,
            int availableDays,
            int orderIndex,
            int count) {
        int day = count <= 1
                ? 1
                : 1 + (orderIndex * (availableDays - 1) / (count - 1));
        return month.atDay(Math.max(1, day))
                .atTime(9 + orderIndex % 9, (orderIndex * 11) % 60);
    }

    static String orderNumber(
            LocalDateTime orderedAt,
            YearMonth month,
            int orderIndex) {
        String source = "feedflow|%s|%03d".formatted(month, orderIndex + 1);
        String code = UUID.nameUUIDFromBytes(
                        source.getBytes(StandardCharsets.UTF_8))
                .toString()
                .replace("-", "")
                .substring(0, 6)
                .toUpperCase();
        return "FF-" + orderedAt.toLocalDate().format(
                DateTimeFormatter.BASIC_ISO_DATE) + "-" + code;
    }

    private String phoneNumber(int monthIndex, int orderIndex) {
        int middle = 2100 + Math.floorMod(
                monthIndex * 137 + orderIndex * 83, 7000);
        int last = 1000 + Math.floorMod(
                monthIndex * 997 + orderIndex * 317, 9000);
        return "010-%04d-%04d".formatted(middle, last);
    }

    private PaymentMethod paymentMethod(int orderIndex) {
        return switch (orderIndex % 3) {
            case 1 -> PaymentMethod.KAKAO_PAY;
            case 2 -> PaymentMethod.BANK_TRANSFER;
            default -> PaymentMethod.CARD;
        };
    }

    private CustomerOrder.OrderStatus status(
            YearMonth orderMonth,
            YearMonth currentMonth,
            int orderIndex) {
        if (!orderMonth.equals(currentMonth)) {
            return CustomerOrder.OrderStatus.DELIVERED;
        }
        return switch (orderIndex % 4) {
            case 1 -> CustomerOrder.OrderStatus.SHIPPING;
            case 2 -> CustomerOrder.OrderStatus.PREPARING;
            case 3 -> CustomerOrder.OrderStatus.PAID;
            default -> CustomerOrder.OrderStatus.DELIVERED;
        };
    }
}
