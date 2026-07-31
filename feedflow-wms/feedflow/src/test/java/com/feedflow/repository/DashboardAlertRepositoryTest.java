package com.feedflow.repository;

import com.feedflow.domain.AnimalType;
import com.feedflow.domain.Product;
import com.feedflow.domain.ProductLot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 대시보드 경고 쿼리(JPQL) 검증 테스트.
 * <p>
 * H2 인메모리 DB 에 직접 데이터를 넣고 <b>실제 JPQL 이 정확한 데이터만 필터링하는지</b> 확인한다.
 * 초기 데이터(data.sql)가 섞이면 검증이 불가능하므로 sql.init 을 비활성화한다.
 *
 * <h3>검증 포인트</h3>
 * <ul>
 *     <li>재고 부족 : totalStock &lt; safetyStock 이고 사용 중(active)인 품목만</li>
 *     <li>유통기한 : 30일 이내 만료 + 이미 만료 포함, <b>31일 남은 로트는 제외</b></li>
 *     <li>잔여 수량이 0인 로트는 경고 대상에서 제외</li>
 * </ul>
 */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.sql.init.mode=never",     // data.sql 을 실행하지 않는다
        "spring.jpa.show-sql=false"
})
@DisplayName("대시보드 경고 JPQL 쿼리 테스트")
class DashboardAlertRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductLotRepository productLotRepository;

    private final LocalDate today = LocalDate.now();

    /* ==================================================================
     * 재고 부족 경고
     * ================================================================== */

    @Test
    @DisplayName("[재고부족] totalStock < safetyStock 인 사용 중 품목만 반환하고, 부족량이 큰 순서로 정렬한다")
    void findSafetyStockAlerts_filtersAndSorts() {
        // given
        product("P-SHORT-10", "부족10", 40, 50, true);      // 10 부족  → 포함
        product("P-SHORT-100", "부족100", 0, 100, true);    // 100 부족 → 포함 (먼저 나와야 함)
        product("P-ENOUGH", "충분", 300, 100, true);        // 충분     → 제외
        product("P-EQUAL", "동일", 50, 50, true);           // 같음     → 제외 (미만 조건)
        product("P-INACTIVE", "단종", 10, 50, false);       // 미달이지만 사용중지 → 제외
        entityManager.flush();
        entityManager.clear();

        // when
        List<Product> alerts = productRepository.findSafetyStockAlerts();

        // then
        assertThat(alerts).hasSize(2);
        assertThat(alerts)
                .extracting(Product::getProductCode)
                .containsExactly("P-SHORT-100", "P-SHORT-10");   // 부족량 내림차순

        assertThat(productRepository.countSafetyStockAlerts()).isEqualTo(2L);
    }

    @Test
    @DisplayName("[재고부족] 재고가 안전재고와 같으면 경고 대상이 아니다 (경계값)")
    void findSafetyStockAlerts_equalStockIsNotAlert() {
        product("P-EQUAL", "동일", 50, 50, true);
        entityManager.flush();
        entityManager.clear();

        assertThat(productRepository.findSafetyStockAlerts()).isEmpty();
        assertThat(productRepository.countSafetyStockAlerts()).isZero();
    }

    /* ==================================================================
     * 유통기한 경고
     * ================================================================== */

    @Test
    @DisplayName("[유통기한] 30일 이내 + 이미 만료된 로트만 반환하고 31일 남은 로트는 제외한다")
    void findExpiringLots_excludesLotsBeyondThreshold() {
        // given
        Product product = product("P-LOT", "로트테스트", 500, 100, true);

        lot(product, "LOT-EXPIRED-10", today.minusDays(10), 20);   // 만료 10일 경과 → 포함
        lot(product, "LOT-EXPIRED-1", today.minusDays(1), 20);     // 만료 1일 경과  → 포함
        lot(product, "LOT-TODAY", today, 20);                      // 오늘 만료      → 포함
        lot(product, "LOT-D30", today.plusDays(30), 20);           // D-30 (경계)    → 포함
        lot(product, "LOT-D31", today.plusDays(31), 20);           // D-31           → 제외
        lot(product, "LOT-D100", today.plusDays(100), 20);         // D-100          → 제외
        lot(product, "LOT-EMPTY", today.plusDays(5), 0);           // 잔여 0         → 제외
        entityManager.flush();
        entityManager.clear();

        LocalDate limitDate = today.plusDays(30);

        // when
        List<ProductLot> lots = productLotRepository.findExpiringLots(limitDate);

        // then : 4건만, 유통기한이 이른 순서(가장 위험한 것 먼저)
        assertThat(lots).hasSize(4);
        assertThat(lots)
                .extracting(ProductLot::getLotNo)
                .containsExactly("LOT-EXPIRED-10", "LOT-EXPIRED-1", "LOT-TODAY", "LOT-D30");

        assertThat(lots)
                .extracting(ProductLot::getLotNo)
                .doesNotContain("LOT-D31", "LOT-D100", "LOT-EMPTY");

        assertThat(productLotRepository.countExpiringLots(limitDate)).isEqualTo(4L);
    }

    @Test
    @DisplayName("[유통기한] fetch join 으로 품목 정보가 함께 조회된다")
    void findExpiringLots_fetchesProduct() {
        Product product = product("P-FETCH", "패치조인", 100, 10, true);
        lot(product, "LOT-FETCH", today.plusDays(5), 30);
        entityManager.flush();
        entityManager.clear();

        List<ProductLot> lots = productLotRepository.findExpiringLots(today.plusDays(30));

        assertThat(lots).hasSize(1);
        assertThat(lots.get(0).getProduct().getProductCode()).isEqualTo("P-FETCH");
        assertThat(lots.get(0).getProduct().getName()).isEqualTo("패치조인");
    }

    @Test
    @DisplayName("[유통기한] 만료 건수는 오늘보다 이전 날짜의 로트만 센다 (오늘 만료는 제외)")
    void countExpiredLots_onlyPastExpiration() {
        Product product = product("P-EXPIRED", "만료테스트", 200, 10, true);

        lot(product, "LOT-PAST-3", today.minusDays(3), 10);   // 포함
        lot(product, "LOT-PAST-1", today.minusDays(1), 10);   // 포함
        lot(product, "LOT-TODAY", today, 10);                 // 제외 (당일은 아직 유효)
        lot(product, "LOT-FUTURE", today.plusDays(1), 10);    // 제외
        lot(product, "LOT-PAST-EMPTY", today.minusDays(5), 0); // 제외 (잔여 0)
        entityManager.flush();
        entityManager.clear();

        assertThat(productLotRepository.countExpiredLots(today)).isEqualTo(2L);
    }

    @Test
    @DisplayName("[유통기한] 경고 대상이 없으면 빈 목록과 0 을 반환한다")
    void findExpiringLots_empty() {
        Product product = product("P-SAFE", "여유", 100, 10, true);
        lot(product, "LOT-FAR", today.plusDays(180), 50);
        entityManager.flush();
        entityManager.clear();

        assertThat(productLotRepository.findExpiringLots(today.plusDays(30))).isEmpty();
        assertThat(productLotRepository.countExpiringLots(today.plusDays(30))).isZero();
        assertThat(productLotRepository.countExpiredLots(today)).isZero();
    }

    /* ==================================================================
     * 픽스처
     * ================================================================== */

    private Product product(String productCode, String name,
                            int totalStock, int safetyStock, boolean active) {
        Product product = Product.builder()
                .productCode(productCode)
                .name(name)
                .animalType(AnimalType.CATTLE)
                .weightKg(25)
                .price(32000L)
                .totalStock(totalStock)
                .safetyStock(safetyStock)
                .shelfLifeDays(180)
                .active(active)
                .build();

        return entityManager.persist(product);
    }

    private ProductLot lot(Product product, String lotNo, LocalDate expirationDate, int lotQuantity) {
        ProductLot lot = ProductLot.builder()
                .product(product)
                .lotNo(lotNo)
                .manufacturedDate(expirationDate.minusDays(180))
                .expirationDate(expirationDate)
                .lotQuantity(lotQuantity)
                .build();

        return entityManager.persist(lot);
    }
}
