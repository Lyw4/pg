package com.ex.config;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import lombok.extern.slf4j.Slf4j;

import com.ex.entity.CustomerOrder;
import com.ex.entity.Manufacturer;
import com.ex.entity.OrderItem;
import com.ex.entity.Product;
import com.ex.entity.ProductLot;
import com.ex.entity.RecurringDelivery;
import com.ex.entity.StockLog;
import com.ex.entity.StockLog.ChangeType;
import com.ex.repository.CustomerOrderRepository;
import com.ex.repository.ManufacturerRepository;
import com.ex.repository.OrderItemRepository;
import com.ex.repository.ProductLotRepository;
import com.ex.repository.ProductRepository;
import com.ex.repository.RecurringDeliveryRepository;
import com.ex.repository.StockLogRepository;
import com.ex.service.FarmCustomerSeeder;
import com.ex.service.WarehouseFulfillmentService;
import com.ex.service.WarehousePlanSeeder;
import com.ex.service.WarehouseRecurringDeliverySeeder;

@Configuration
@Slf4j
public class DataInitializer {

    /*
     * name             상품명
     * category         카테고리
     * manufacturer     제조사
     * weightKg         중량
     * price            가격
     * stock            초기재고
     * safetyStock      안전재고
     * shelfLifeMonths  유통기한(개월)
     * description      상품설명
     */
    private record ProductSeed(
            String name,
            String category,
            String manufacturer,
            String weightKg,
            int price,
            int stock,
            int safetyStock,
            int shelfLifeMonths,
            String description) {
    }

    /**
     * 재고·유통의 40개 기준 상품을 판매 홈페이지에도 그대로 노출하기 위한
     * 고객용 표시 정보입니다. 별도의 판매 전용 상품을 만들지 않습니다.
     */
    private record StorefrontProfile(
            String stage,
            BigDecimal protein,
            BigDecimal fat,
            BigDecimal fiber,
            BigDecimal calcium,
            Integer originalPrice,
            String badge,
            String tone,
            String shape,
            String imageUrl) {
    }

    private static final List<ProductSeed> PRODUCT_SEEDS = List.of(

            // =====================================================
            // 소 사료 10개
            // =====================================================

            new ProductSeed(
                    "한우 송아지 스타터",
                    "소",
                    "한빛사료",
                    "20",
                    31500,
                    80,
                    25,
                    6,
                    "송아지 초기 성장용 고단백 배합사료"),

            new ProductSeed(
                    "한우 성장 플러스",
                    "소",
                    "한빛사료",
                    "20",
                    32800,
                    75,
                    25,
                    6,
                    "육성기 한우의 골격과 근육 발달용 사료"),

            new ProductSeed(
                    "한우 비육 전기",
                    "소",
                    "대농피드",
                    "25",
                    35200,
                    90,
                    30,
                    7,
                    "비육 전기 섭취량과 증체 개선용 사료"),

            new ProductSeed(
                    "한우 비육 후기",
                    "소",
                    "대농피드",
                    "25",
                    36800,
                    70,
                    25,
                    7,
                    "육질과 마블링 향상을 위한 후기 사료"),

            new ProductSeed(
                    "낙농 송아지 케어",
                    "소",
                    "그린팜영양",
                    "20",
                    33400,
                    55,
                    20,
                    6,
                    "젖소 송아지 면역과 성장 관리용 사료"),

            new ProductSeed(
                    "젖소 착유우 밸런스",
                    "소",
                    "그린팜영양",
                    "25",
                    38600,
                    65,
                    25,
                    6,
                    "착유우의 유량과 유질 균형 관리용 사료"),

            new ProductSeed(
                    "번식우 컨디션",
                    "소",
                    "한빛사료",
                    "20",
                    34500,
                    60,
                    20,
                    6,
                    "번식우의 체형과 번식 컨디션 관리용 사료"),

            new ProductSeed(
                    "육우 고효율 사료",
                    "소",
                    "대농피드",
                    "25",
                    35900,
                    85,
                    30,
                    7,
                    "육우의 사료 효율과 증체 개선용 사료"),

            new ProductSeed(
                    "반추위 안정화 사료",
                    "소",
                    "그린팜영양",
                    "20",
                    37200,
                    50,
                    20,
                    6,
                    "반추위 환경과 소화 균형 관리용 사료"),

            new ProductSeed(
                    "한우 프리미엄 마블",
                    "소",
                    "한빛사료",
                    "25",
                    41500,
                    45,
                    20,
                    7,
                    "출하 전 육질 향상을 위한 프리미엄 사료"),

            // =====================================================
            // 돼지 사료 10개
            // =====================================================

            new ProductSeed(
                    "자돈 스타터 1호",
                    "돼지",
                    "대농피드",
                    "20",
                    34800,
                    90,
                    30,
                    5,
                    "이유 초기 자돈의 적응을 위한 사료"),

            new ProductSeed(
                    "자돈 스타터 2호",
                    "돼지",
                    "대농피드",
                    "20",
                    33900,
                    85,
                    30,
                    5,
                    "이유 후 자돈 성장과 장 건강 관리용 사료"),

            new ProductSeed(
                    "육성돈 그로우",
                    "돼지",
                    "한빛사료",
                    "25",
                    36500,
                    110,
                    35,
                    6,
                    "육성돈의 균일한 성장과 증체용 사료"),

            new ProductSeed(
                    "비육돈 피니셔",
                    "돼지",
                    "한빛사료",
                    "25",
                    37200,
                    100,
                    35,
                    6,
                    "비육 후기 사료 효율 개선용 사료"),

            new ProductSeed(
                    "모돈 임신기 케어",
                    "돼지",
                    "그린팜영양",
                    "20",
                    38500,
                    55,
                    20,
                    6,
                    "임신돈 체형과 태아 성장 관리용 사료"),

            new ProductSeed(
                    "모돈 포유기 파워",
                    "돼지",
                    "그린팜영양",
                    "20",
                    39800,
                    60,
                    20,
                    6,
                    "포유돈의 유량과 체력 유지용 사료"),

            new ProductSeed(
                    "웅돈 컨디션 플러스",
                    "돼지",
                    "대농피드",
                    "20",
                    37600,
                    40,
                    15,
                    6,
                    "종돈의 번식 컨디션 관리용 사료"),

            new ProductSeed(
                    "양돈 장건강 프로",
                    "돼지",
                    "그린팜영양",
                    "20",
                    39200,
                    65,
                    25,
                    5,
                    "장내 환경과 소화율 개선용 사료"),

            new ProductSeed(
                    "양돈 저단백 밸런스",
                    "돼지",
                    "한빛사료",
                    "25",
                    38100,
                    75,
                    25,
                    6,
                    "질소 배출 저감을 고려한 균형 사료"),

            new ProductSeed(
                    "비육돈 프리미엄 골드",
                    "돼지",
                    "대농피드",
                    "25",
                    40800,
                    50,
                    20,
                    6,
                    "출하돈 육질과 생산성 관리용 사료"),

            // =====================================================
            // 조류(닭/오리) 사료 10개
            // =====================================================

            new ProductSeed(
                    "병아리 초이 사료",
                    "조류(닭/오리)",
                    "새봄애니멀",
                    "10",
                    22800,
                    120,
                    40,
                    4,
                    "병아리 초기 성장과 면역 관리용 사료"),

            new ProductSeed(
                    "육계 전기 사료",
                    "조류(닭/오리)",
                    "새봄애니멀",
                    "20",
                    30500,
                    130,
                    45,
                    4,
                    "육계 전기 빠른 성장과 골격 발달용 사료"),

            new ProductSeed(
                    "육계 후기 사료",
                    "조류(닭/오리)",
                    "새봄애니멀",
                    "20",
                    31800,
                    125,
                    45,
                    4,
                    "육계 후기 증체와 사료 효율 관리용 사료"),

            new ProductSeed(
                    "산란계 육성 사료",
                    "조류(닭/오리)",
                    "한빛사료",
                    "20",
                    31200,
                    95,
                    35,
                    5,
                    "산란 전 육성계 균일 성장용 사료"),

            new ProductSeed(
                    "산란계 산란 피크",
                    "조류(닭/오리)",
                    "한빛사료",
                    "20",
                    33400,
                    100,
                    35,
                    5,
                    "산란 피크의 산란율과 난각 관리용 사료"),

            new ProductSeed(
                    "토종닭 건강 사료",
                    "조류(닭/오리)",
                    "새봄애니멀",
                    "20",
                    32600,
                    80,
                    30,
                    5,
                    "토종닭의 건강한 장기 사육용 사료"),

            new ProductSeed(
                    "오리 새끼 스타터",
                    "조류(닭/오리)",
                    "대농피드",
                    "20",
                    31900,
                    90,
                    30,
                    4,
                    "어린 오리의 초기 성장 관리용 사료"),

            new ProductSeed(
                    "육용오리 그로워",
                    "조류(닭/오리)",
                    "대농피드",
                    "20",
                    32900,
                    105,
                    35,
                    4,
                    "육용오리 증체와 균일도 관리용 사료"),

            new ProductSeed(
                    "조류 면역 밸런스",
                    "조류(닭/오리)",
                    "그린팜영양",
                    "10",
                    27600,
                    70,
                    25,
                    4,
                    "닭과 오리의 면역 균형 보조 사료"),

            new ProductSeed(
                    "가금 프리미엄 믹스",
                    "조류(닭/오리)",
                    "새봄애니멀",
                    "20",
                    34800,
                    60,
                    25,
                    5,
                    "중소 농가용 범용 프리미엄 가금 사료"),

            // =====================================================
            // 영양제 10개
            // =====================================================

            new ProductSeed(
                    "멀티 비타민 프리믹스",
                    "영양제",
                    "그린팜영양",
                    "5",
                    28500,
                    70,
                    20,
                    12,
                    "가축 공통 종합 비타민 프리믹스"),

            new ProductSeed(
                    "미네랄 밸런스 플러스",
                    "영양제",
                    "그린팜영양",
                    "5",
                    29800,
                    65,
                    20,
                    12,
                    "칼슘과 미량광물질 균형 보충제"),

            new ProductSeed(
                    "유산균 장건강 파우더",
                    "영양제",
                    "바이오피드랩",
                    "2",
                    32400,
                    80,
                    25,
                    10,
                    "장내 유익균 환경을 위한 생균제"),

            new ProductSeed(
                    "면역 부스터 베타",
                    "영양제",
                    "바이오피드랩",
                    "2",
                    36800,
                    55,
                    20,
                    10,
                    "환절기 면역 컨디션 보조제"),

            new ProductSeed(
                    "전해질 리커버리",
                    "영양제",
                    "새봄애니멀",
                    "3",
                    25200,
                    75,
                    25,
                    12,
                    "고온 스트레스와 탈수 회복 보조제"),

            new ProductSeed(
                    "간기능 케어믹스",
                    "영양제",
                    "바이오피드랩",
                    "2",
                    34600,
                    45,
                    15,
                    10,
                    "대사 부담 완화를 위한 간 건강 보조제"),

            new ProductSeed(
                    "칼슘 난각 강화제",
                    "영양제",
                    "그린팜영양",
                    "5",
                    27200,
                    60,
                    20,
                    12,
                    "산란계 난각 강도와 칼슘 보충용"),

            new ProductSeed(
                    "번식 비타민 ADE",
                    "영양제",
                    "바이오피드랩",
                    "2",
                    33500,
                    50,
                    15,
                    12,
                    "번식축의 비타민 A·D·E 보충제"),

            new ProductSeed(
                    "사료효율 효소제",
                    "영양제",
                    "그린팜영양",
                    "3",
                    38900,
                    55,
                    20,
                    10,
                    "영양소 이용률 향상을 위한 복합 효소제"),

            new ProductSeed(
                    "곰팡이독소 흡착제",
                    "영양제",
                    "바이오피드랩",
                    "5",
                    41800,
                    40,
                    15,
                    12,
                    "사료 내 곰팡이독소 위험 저감 보조제")
    );

    @Bean
    @Order(100)
    CommandLineRunner seedData(
            ManufacturerRepository manufacturerRepository,
            ProductRepository productRepository,
            ProductLotRepository lotRepository,
            StockLogRepository logRepository,
            CustomerOrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            WarehousePlanSeeder warehousePlanSeeder,
            FarmCustomerSeeder farmCustomerSeeder,
            WarehouseFulfillmentService warehouseFulfillmentService,
            WarehouseRecurringDeliverySeeder
            warehouseRecurringDeliverySeeder) {

        return args -> {

            for (int index = 0; index < PRODUCT_SEEDS.size(); index++) {

                ProductSeed seed = PRODUCT_SEEDS.get(index);

                /*
                 * 같은 상품이 이미 존재하면 생성하지 않습니다.
                 * 서버를 여러 번 실행해도 중복 데이터가 생기지 않습니다.
                 */
                if (productRepository.existsByName(seed.name())) {
                    continue;
                }

                Manufacturer manufacturer = findOrCreateManufacturer(
                        manufacturerRepository,
                        seed.manufacturer());

                Product product = new Product(
                        manufacturer,
                        seed.name(),
                        seed.category(),
                        new BigDecimal(seed.weightKg()),
                        BigDecimal.valueOf(seed.price()),
                        seed.safetyStock(),
                        seed.shelfLifeMonths(),
                        seed.description());

                productRepository.save(product);

                LocalDate manufacturedDate =
                        LocalDate.now().minusDays((index % 20) + 1);

                String lotNo = createLotNo(
                        seed.category(),
                        manufacturedDate,
                        index + 1);

                LocalDate expirationDate =
                        manufacturedDate.plusMonths(
                                seed.shelfLifeMonths());

                ProductLot lot = new ProductLot(
                        product,
                        lotNo,
                        manufacturedDate,
                        expirationDate,
                        seed.stock());

                lotRepository.save(lot);

                // 상품의 전체 재고 증가
                product.changeStock(seed.stock());
                productRepository.save(product);

                // 최초 입고 이력 저장
                StockLog stockLog = new StockLog(
                        lot,
                        1L,
                        ChangeType.INBOUND,
                        seed.stock(),
                        "더미 상품 초기 입고");

                logRepository.save(stockLog);
            }

            warehousePlanSeeder.seed();

            farmCustomerSeeder.seed();

            warehouseRecurringDeliverySeeder.seed();

            /*
             * 기존 DB를 재사용하더라도 재고·유통 기준 40개 상품의 제조사,
             * 가격, 설명과 판매 상세정보를 매번 기준 데이터로 맞춥니다.
             * 과거 판매 전용 초기화가 같은 이름의 상품을 수정했던 경우도
             * 여기에서 원래 상품으로 복구됩니다.
             */
            for (int index = 0; index < PRODUCT_SEEDS.size(); index++) {
                ProductSeed seed = PRODUCT_SEEDS.get(index);
                Product product = productRepository.findByName(seed.name())
                        .orElseThrow(() -> new IllegalStateException(
                                "기준 상품을 찾을 수 없습니다: " + seed.name()));
                Manufacturer manufacturer = findOrCreateManufacturer(
                        manufacturerRepository,
                        seed.manufacturer());
                StorefrontProfile profile = storefrontProfile(seed, index);

                product.updateForStorefront(
                        manufacturer,
                        seed.name(),
                        seed.category(),
                        profile.stage(),
                        seed.description(),
                        new BigDecimal(seed.weightKg()),
                        BigDecimal.valueOf(seed.price()),
                        profile.originalPrice(),
                        profile.protein(),
                        profile.fat(),
                        profile.fiber(),
                        profile.calcium(),
                        profile.imageUrl(),
                        profile.badge(),
                        profile.tone(),
                        profile.shape());
                product.configureShelfLife(seed.shelfLifeMonths());
                productRepository.save(product);
            }

            // 과거 초기 LOT의 날짜 부분을 실제 생산일 기준으로 보정합니다.
            lotRepository.findAllByOrderByExpirationDateAsc().forEach(lot -> {
                if (lot.getLotNo().matches(
                        "LOT-(CATTLE|PIG|BIRD|SUP)-\\d{8}-\\d{3}")) {
                    String[] parts = lot.getLotNo().split("-");
                    String manufacturedDate = lot.getManufacturedDate()
                            .toString().replace("-", "");
                    String corrected = "%s-%s-%s-%s".formatted(
                            parts[0], parts[1], manufacturedDate, parts[3]);
                    if (!corrected.equals(lot.getLotNo())
                            && !lotRepository.existsByLotNo(corrected)) {
                        lot.changeLotNo(corrected);
                        lotRepository.save(lot);
                    }
                }
                if (lot.getWarehouseLocation() == null
                        || lot.getWarehouseLocation().isBlank()) {
                    String zone = switch (lot.getProduct().getAnimalType()) {
                        case "소" -> "A";
                        case "돼지" -> "B";
                        case "조류(닭/오리)" -> "C";
                        default -> "D";
                    };
                    long shelf = ((lot.getLotId() - 1) % 12) + 1;
                    lot.changeWarehouseLocation(
                            "%s창고-%02d번 선반".formatted(zone, shelf));
                    lotRepository.save(lot);
                }
            });

            // 주문 데이터가 하나도 없을 때만 예제 주문 생성
            if (orderRepository.count() == 0) {

                CustomerOrder order = new CustomerOrder(
                        1L,
                        new BigDecimal("96000"),
                        BigDecimal.ZERO,
                        "서울시 강남구 테헤란로 123");

                orderRepository.save(order);
            }

            // 예제 주문에도 출고 지시를 시험할 수 있는 주문 품목을 연결합니다.
            if (orderItemRepository.count() == 0 && orderRepository.count() > 0) {
                CustomerOrder order = orderRepository.findAll().get(0);
                ProductLot lot = lotRepository.findAllByOrderByExpirationDateAsc()
                        .stream()
                        .filter(item -> item.getLotQuantity() >= 3)
                        .findFirst()
                        .orElseThrow();
                orderItemRepository.save(new OrderItem(
                        order,
                        lot.getProduct(),
                        lot,
                        3,
                        lot.getProduct().getPrice()));
            }

            int failedAssignments = warehouseFulfillmentService
                    .assignUnassignedOrders();
            if (failedAssignments > 0) {
                log.warn("기존 주문 창고 자동 배정 실패 {}건", failedAssignments);
            }
        };
    }

    /*
     * 카테고리별 추천 월간 정기 배송 일정입니다.
     *
     * - 소: 매월 1일·15일, 월 약 2톤
     * - 돼지: 매월 5일·20일, 월 약 2톤
     * - 조류: 매월 10일·25일, 월 약 2톤
     * - 영양제: 매월 15일 안전재고 점검 후 부족분만 입고
     *
     * 월 2톤은 해당 카테고리 상품에 동일한 중량 비율로 나눕니다.
     * 포장 중량 때문에 실제 합계에는 소폭의 반올림 차이가 생길 수 있습니다.
     */
    private void seedRecommendedRecurringDeliveries(
            ProductRepository productRepository,
            RecurringDeliveryRepository recurringDeliveryRepository) {

        if (recurringDeliveryRepository.count() > 0) {
            return;
        }

        List<Product> products =
                productRepository.findAllByOrderByNameAsc();

        List<RecurringDelivery> schedules =
                new ArrayList<>();

        addFixedCategorySchedules(
                schedules,
                products,
                "소",
                1,
                15);

        addFixedCategorySchedules(
                schedules,
                products,
                "돼지",
                5,
                20);

        addFixedCategorySchedules(
                schedules,
                products,
                "조류(닭/오리)",
                10,
                25);

        products.stream()
                .filter(product ->
                        "영양제".equals(
                                product.getAnimalType()))
                .forEach(product ->
                        schedules.add(
                                new RecurringDelivery(
                                        product.getManufacturer(),
                                        product,
                                        0,
                                        15,
                                        nextDeliveryDate(15),
                                        true,
                                        "매월 안전재고 점검 후 부족분만 입고")));

        recurringDeliveryRepository.saveAll(schedules);
    }

    private void addFixedCategorySchedules(
            List<RecurringDelivery> schedules,
            List<Product> products,
            String category,
            int firstDeliveryDay,
            int secondDeliveryDay) {

        List<Product> categoryProducts =
                products.stream()
                        .filter(product ->
                                category.equals(
                                        product.getAnimalType()))
                        .toList();

        if (categoryProducts.isEmpty()) {
            return;
        }

        BigDecimal targetKgPerProduct =
                BigDecimal.valueOf(2_000)
                        .divide(
                                BigDecimal.valueOf(
                                        categoryProducts.size()),
                                6,
                                RoundingMode.HALF_UP);

        for (int index = 0;
                index < categoryProducts.size();
                index++) {

            Product product =
                    categoryProducts.get(index);

            int quantity =
                    Math.max(
                            1,
                            targetKgPerProduct
                                    .divide(
                                            product.getWeightKg(),
                                            0,
                                            RoundingMode.HALF_UP)
                                    .intValue());

            int deliveryDay =
                    index % 2 == 0
                        ? firstDeliveryDay
                        : secondDeliveryDay;

            schedules.add(
                    new RecurringDelivery(
                            product.getManufacturer(),
                            product,
                            quantity,
                            deliveryDay,
                            nextDeliveryDate(
                                    deliveryDay),
                            false,
                            "추천 초기 일정 · 카테고리 월 약 2톤 균등배분"));
        }
    }

    private LocalDate nextDeliveryDate(
            int deliveryDay) {

        LocalDate today =
                LocalDate.now();

        YearMonth targetMonth =
                YearMonth.from(today);

        LocalDate candidate =
                targetMonth.atDay(deliveryDay);

        if (candidate.isBefore(today)) {
            candidate =
                    targetMonth
                            .plusMonths(1)
                            .atDay(deliveryDay);
        }

        return candidate;
    }

    /*
     * 같은 제조사가 있으면 기존 제조사를 사용하고,
     * 없으면 새로운 제조사를 생성합니다.
     */
    private Manufacturer findOrCreateManufacturer(
            ManufacturerRepository repository,
            String companyName) {

        return repository.findByCompanyName(companyName)
                .orElseGet(() -> repository.save(
                        new Manufacturer(
                                companyName,
                                "유통 담당자",
                                "02-0000-0000")));
    }

    private StorefrontProfile storefrontProfile(
            ProductSeed seed,
            int index) {
        int categoryIndex = index % 10;
        String category = seed.category();
        BigDecimal[] protein;
        BigDecimal[] fat;
        BigDecimal[] fiber;
        BigDecimal[] calcium;

        if ("소".equals(category)) {
            protein = decimals("18", "16", "14", "13", "20", "18", "15", "13.5", "12.5", "13");
            fat = decimals("4.0", "3.8", "4.2", "4.5", "4.5", "4.2", "3.5", "4.0", "3.2", "4.8");
            fiber = decimals("7.0", "8.0", "9.0", "8.5", "6.0", "7.5", "9.0", "8.0", "10.0", "8.0");
            calcium = decimals("0.9", "0.8", "0.8", "0.75", "1.0", "0.9", "0.9", "0.8", "0.9", "0.75");
        } else if ("돼지".equals(category)) {
            protein = decimals("20", "19", "18", "17", "15", "17", "16", "18", "16", "17");
            fat = decimals("5.0", "4.8", "5.0", "5.2", "4.0", "5.0", "4.2", "4.5", "4.0", "5.3");
            fiber = decimals("5.0", "5.5", "6.0", "6.5", "7.0", "6.0", "7.0", "5.5", "6.5", "6.0");
            calcium = decimals("0.9", "0.85", "0.8", "0.75", "0.9", "0.95", "0.85", "0.8", "0.75", "0.8");
        } else if ("조류(닭/오리)".equals(category)) {
            protein = decimals("21", "20", "19", "17", "16.5", "17.5", "21", "18.5", "18", "18");
            fat = decimals("4.5", "4.2", "4.5", "3.8", "4.0", "4.0", "4.5", "4.2", "3.5", "4.3");
            fiber = decimals("4.5", "5.0", "5.5", "6.0", "5.5", "6.0", "4.5", "5.5", "5.0", "5.5");
            calcium = decimals("1.0", "0.95", "0.9", "1.1", "3.8", "1.0", "1.0", "0.95", "1.0", "1.1");
        } else {
            protein = decimals("8", "4", "2", "3", "1", "2", "1", "3", "6", "4");
            fat = decimals("2", "1", "1", "1", "0.5", "1", "0.5", "1", "1", "1");
            fiber = decimals("4", "3", "2", "2", "1", "2", "1", "2", "3", "2");
            calcium = decimals("1.2", "12", "0.5", "0.8", "0.6", "0.5", "24", "0.9", "1.0", "0.7");
        }

        String[] tones = {"amber", "blue", "coral", "gold", "lime", "mint", "navy", "rose", "green", "slate"};
        String categoryCode = switch (category) {
            case "소" -> "CATTLE";
            case "돼지" -> "PIG";
            case "조류(닭/오리)" -> "BIRD";
            default -> "SUP";
        };
        String imageVariant = categoryIndex % 2 == 0 ? "-v2" : "";
        String imageUrl = switch (category) {
            case "소" -> "/images/products/cattle-feed%s.png".formatted(imageVariant);
            case "돼지" -> "/images/products/pig-feed%s.png".formatted(imageVariant);
            case "조류(닭/오리)" -> "/images/products/poultry-feed%s.png".formatted(imageVariant);
            default -> "/images/products/supplement%s.png".formatted(imageVariant);
        };

        return new StorefrontProfile(
                feedStage(seed.name(), category),
                protein[categoryIndex],
                fat[categoryIndex],
                fiber[categoryIndex],
                calcium[categoryIndex],
                categoryIndex % 4 == 0 ? seed.price() + 3_000 : null,
                categoryIndex == 0 ? "카테고리 추천"
                        : categoryIndex == 9 ? "프리미엄" : null,
                tones[categoryIndex],
                "%s-%02d".formatted(categoryCode, categoryIndex + 1),
                imageUrl);
    }

    private String feedStage(String name, String category) {
        if ("영양제".equals(category)) {
            return name.contains("난각") ? "산란계"
                    : name.contains("번식") ? "번식축"
                    : "전 축종";
        }
        if (name.contains("송아지") || name.contains("병아리")
                || name.contains("새끼") || name.contains("자돈")) {
            return "초기 성장";
        }
        if (name.contains("비육 전기") || name.contains("육계 전기")) {
            return "육성 전기";
        }
        if (name.contains("비육 후기") || name.contains("피니셔")
                || name.contains("프리미엄")) {
            return "비육 후기";
        }
        if (name.contains("착유")) {
            return "착유기";
        }
        if (name.contains("임신")) {
            return "임신기";
        }
        if (name.contains("포유")) {
            return "포유기";
        }
        if (name.contains("번식") || name.contains("웅돈")) {
            return "번식기";
        }
        if (name.contains("산란 피크")) {
            return "산란기";
        }
        if (name.contains("육성") || name.contains("성장")
                || name.contains("그로우") || name.contains("그로워")) {
            return "육성기";
        }
        return "전 성장단계";
    }

    private BigDecimal[] decimals(String... values) {
        BigDecimal[] result = new BigDecimal[values.length];
        for (int index = 0; index < values.length; index++) {
            result[index] = new BigDecimal(values[index]);
        }
        return result;
    }

    /*
     * 카테고리별 LOT 번호를 생성합니다.
     *
     * 예:
     * LOT-CATTLE-20260727-001
     * LOT-PIG-20260727-011
     * LOT-BIRD-20260727-021
     * LOT-SUP-20260727-031
     */
    private String createLotNo(
            String category,
            LocalDate manufacturedDate,
            int sequence) {

        String categoryCode = switch (category) {
            case "소" -> "CATTLE";
            case "돼지" -> "PIG";
            case "조류(닭/오리)" -> "BIRD";
            default -> "SUP";
        };

        String productionDate =
                manufacturedDate
                        .toString()
                        .replace("-", "");

        return "LOT-%s-%s-%03d".formatted(
                categoryCode,
                productionDate,
                sequence);
    }
}
