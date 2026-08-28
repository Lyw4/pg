package com.feedflow.repository;

import com.feedflow.admin.dto.CenterFarmRow;
import com.feedflow.domain.AnimalType;
import com.feedflow.domain.BinPurpose;
import com.feedflow.domain.Center;
import com.feedflow.domain.CustomerStatus;
import com.feedflow.domain.FarmCustomer;
import com.feedflow.domain.Inventory;
import com.feedflow.domain.Product;
import com.feedflow.domain.ProductLot;
import com.feedflow.domain.ProductType;
import com.feedflow.domain.WarehouseBin;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 농장 고객사 조회 · 집계 JPQL 검증.
 * <p>
 * 이 쿼리들의 핵심 규칙은 <b>JPQL 문자열 안에만</b> 있어서 서비스 단위 테스트로는
 * 확인할 수 없다. 특히 다음 두 가지다.
 * <ul>
 *     <li>{@code sum(case when status = ACTIVE then ... else 0 end)} —
 *         농장 수는 전체를 세지만 월 사료량은 거래 중만 더한다</li>
 *     <li>{@code not exists} 서브쿼리 — 담당 센터에 그 축종 재고가 한 번도 없는 농장</li>
 * </ul>
 *
 * <h3>왜 이 테스트가 필요했나</h3>
 * 원본(팀원 모듈)은 이 집계를 자바 스트림으로 했다. 전체 농장을 네 번 로드하고
 * 센터마다 {@code filter} 를 돌렸다. 집계를 DB 로 내리면서 <b>세는 기준이 컬럼마다
 * 다르다</b>는 규칙이 SQL 안으로 들어갔으므로, 실제 H2 에 넣고 확인해야 한다.
 */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.sql.init.mode=never",
        "spring.jpa.show-sql=false"
})
@DisplayName("농장 고객사 조회 · 집계 JPQL 테스트")
class FarmCustomerRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private FarmCustomerRepository farmCustomerRepository;

    private Center yesan;
    private Center naju;
    private Center empty;

    private int farmSequence = 0;

    @BeforeEach
    void setUp() {
        yesan = persistCenter("C1-YS", "충남 예산 센터");
        naju = persistCenter("C5-NJ", "전남 나주 센터");
        // 담당 농장이 한 곳도 없는 센터 — group by 결과에서 빠지는지 확인용
        empty = persistCenter("C9-ZZ", "미배정 센터");

        // 예산 : 거래 중 2곳(720 + 1850) + 거래 보류 1곳(2380)
        persistFarm(yesan, "예산 고덕 한우농장", AnimalType.CATTLE,
                180, 720, CustomerStatus.ACTIVE, 6.8);
        persistFarm(yesan, "당진 합덕 양돈농장", AnimalType.PIG,
                2400, 1850, CustomerStatus.ACTIVE, 2.2);
        persistFarm(yesan, "홍성 광천 산란계농장", AnimalType.POULTRY,
                60000, 2380, CustomerStatus.PAUSED, 33.1);

        // 나주 : 거래 중 1곳(3200)
        persistFarm(naju, "나주 문평 육계농장", AnimalType.POULTRY,
                72000, 3200, CustomerStatus.ACTIVE, 17.9);
    }

    /* ==================================================================
     * 센터별 집계 — 세는 기준이 컬럼마다 다르다
     * ================================================================== */

    @Test
    @DisplayName("농장 수와 사육 규모는 거래 보류를 포함해 센다")
    void countsIncludePausedFarms() {
        Map<Long, CenterFarmRow> byCenter = summaryByCenter();

        CenterFarmRow yesanRow = byCenter.get(yesan.getCenterId());

        assertThat(yesanRow.farms())
                .as("거래를 보류했어도 담당 농장이 아니게 되는 것은 아니다")
                .isEqualTo(3);
        assertThat(yesanRow.livestock())
                .as("사육 규모도 전체 기준 (180 + 2400 + 60000)")
                .isEqualTo(62580);
    }

    @Test
    @DisplayName("월 예상 사료량은 거래 중인 농장만 합산한다")
    void feedQuantitySumsOnlyActiveFarms() {
        Map<Long, CenterFarmRow> byCenter = summaryByCenter();

        CenterFarmRow yesanRow = byCenter.get(yesan.getCenterId());

        assertThat(yesanRow.activeFarms())
                .as("거래 중인 농장 수")
                .isEqualTo(2);
        assertThat(yesanRow.activeFeed())
                .as("720 + 1850 만 더한다. 보류 중인 2380 은 제외 (전체라면 4950)")
                .isEqualTo(2570);
        assertThat(yesanRow.pausedFarms())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("담당 농장이 없는 센터는 집계 결과에 나오지 않는다 (호출부가 0 으로 채워야 한다)")
    void centerWithoutFarmIsAbsentFromSummary() {
        Map<Long, CenterFarmRow> byCenter = summaryByCenter();

        assertThat(byCenter).containsKeys(yesan.getCenterId(), naju.getCenterId());
        assertThat(byCenter)
                .as("group by 결과이므로 행 자체가 없다")
                .doesNotContainKey(empty.getCenterId());
    }

    @Test
    @DisplayName("집계는 센터 코드 순으로 정렬된다")
    void summaryIsOrderedByCenterCode() {
        List<CenterFarmRow> rows = farmCustomerRepository.findFarmSummaryByCenter();

        assertThat(rows).extracting(CenterFarmRow::centerName)
                .containsExactly("충남 예산 센터", "전남 나주 센터");
    }

    /* ==================================================================
     * 목록 검색 — 필터와 정렬
     * ================================================================== */

    @Test
    @DisplayName("필터가 모두 null 이면 전체를 조회한다")
    void searchWithoutFilterReturnsAll() {
        List<FarmCustomer> found = farmCustomerRepository.search(null, null, null, null);

        assertThat(found).hasSize(4);
    }

    @Test
    @DisplayName("센터 · 축종 · 거래 상태 필터가 함께 걸린다")
    void searchAppliesEachFilter() {
        assertThat(farmCustomerRepository.search(yesan.getCenterId(), null, null, null))
                .as("센터 필터")
                .hasSize(3);

        assertThat(farmCustomerRepository.search(null, AnimalType.POULTRY, null, null))
                .as("축종 필터 — 예산 산란계 + 나주 육계")
                .hasSize(2);

        assertThat(farmCustomerRepository.search(null, null, CustomerStatus.PAUSED, null))
                .as("거래 상태 필터")
                .hasSize(1)
                .extracting(FarmCustomer::getFarmName)
                .containsExactly("홍성 광천 산란계농장");

        assertThat(farmCustomerRepository.search(
                yesan.getCenterId(), AnimalType.POULTRY, CustomerStatus.ACTIVE, null))
                .as("세 조건을 모두 만족하는 농장은 없다")
                .isEmpty();
    }

    @Test
    @DisplayName("키워드는 농장명 · 대표자 · 주소 · 농장코드를 함께 훑는다")
    void searchMatchesKeywordAcrossColumns() {
        assertThat(farmCustomerRepository.search(null, null, null, "%양돈%"))
                .as("농장명 부분 일치")
                .hasSize(1);

        assertThat(farmCustomerRepository.search(null, null, null, "%대표%"))
                .as("대표자명 부분 일치 (픽스처의 대표자는 모두 '대표N')")
                .hasSize(4);

        assertThat(farmCustomerRepository.search(null, null, null, "%없는농장%"))
                .isEmpty();
    }

    @Test
    @DisplayName("정렬은 센터 코드 순 → 담당 센터에서 가까운 순")
    void searchIsOrderedByCenterThenDistance() {
        List<FarmCustomer> found = farmCustomerRepository.search(null, null, null, null);

        assertThat(found).extracting(FarmCustomer::getFarmName)
                .as("예산(C1) 3곳이 거리순으로 먼저, 그 다음 나주(C5)")
                .containsExactly(
                        "당진 합덕 양돈농장",      // C1 · 2.2km
                        "예산 고덕 한우농장",      // C1 · 6.8km
                        "홍성 광천 산란계농장",    // C1 · 33.1km
                        "나주 문평 육계농장");     // C5 · 17.9km
    }

    /* ==================================================================
     * 배정 검토 — 담당 센터가 그 축종을 취급하지 않는 농장
     * ================================================================== */

    @Test
    @DisplayName("담당 센터에 그 축종 재고가 하나도 없으면 배정 검토 대상이 된다")
    void detectsFarmWhoseCenterHasNoStockOfItsAnimalType() {
        // 나주 센터에 가금(POULTRY) 사료 재고만 넣는다.
        // → 나주 담당 육계농장은 정상, 예산 담당 3곳은 모두 검토 대상이 된다
        //   (예산 센터에는 어떤 재고도 넣지 않았다)
        Product poultryFeed = entityManager.persist(Product.builder()
                .productCode("FD-PL-001").name("육계 전기 사료")
                .animalType(AnimalType.POULTRY).productType(ProductType.FEED)
                .weightKg(20).price(23000L)
                .totalStock(100).safetyStock(10).shelfLifeDays(90)
                .active(true)
                .build());

        ProductLot lot = entityManager.persist(ProductLot.builder()
                .product(poultryFeed).lotNo("PL-2607-01")
                .manufacturedDate(LocalDate.of(2026, 7, 1))
                .expirationDate(LocalDate.of(2026, 9, 29))
                .lotQuantity(100)
                .build());

        WarehouseBin najuBin = entityManager.persist(WarehouseBin.builder()
                .binCode("NJ-PL-01").center(naju).zone("PL")
                .binPurpose(BinPurpose.STORAGE)
                .posX(1).posY(1).posWidth(1).posHeight(1)
                .maxCapacity(250).active(true)
                .createdAt(LocalDateTime.now())
                .build());

        entityManager.persist(Inventory.createForInbound(lot, najuBin, 100));
        entityManager.flush();
        entityManager.clear();

        List<FarmCustomer> mismatched = farmCustomerRepository.findWithUnsupportedAnimalType();

        assertThat(mismatched).extracting(FarmCustomer::getFarmName)
                .as("나주 육계농장은 담당 센터에 가금 사료가 있으므로 제외된다")
                .doesNotContain("나주 문평 육계농장");
        assertThat(mismatched)
                .as("예산 센터는 재고가 전혀 없으므로 담당 3곳 모두 검토 대상")
                .hasSize(3);
    }

    /* ==================================================================
     * 픽스처
     * ================================================================== */

    private Map<Long, CenterFarmRow> summaryByCenter() {
        return farmCustomerRepository.findFarmSummaryByCenter().stream()
                .collect(Collectors.toMap(CenterFarmRow::centerId, Function.identity()));
    }

    private Center persistCenter(String code, String name) {
        return entityManager.persist(Center.builder()
                .centerCode(code).name(name).region("테스트 권역")
                .active(true).createdAt(LocalDateTime.now())
                .build());
    }

    private void persistFarm(Center center,
                             String farmName,
                             AnimalType animalType,
                             int livestockCount,
                             int monthlyFeedQuantity,
                             CustomerStatus status,
                             double distanceKm) {
        farmSequence++;
        entityManager.persist(FarmCustomer.builder()
                .farmCode("F-TEST-%02d".formatted(farmSequence))
                .farmName(farmName)
                .representativeName("대표" + farmSequence)
                .phone("010-0000-00%02d".formatted(farmSequence))
                .postalCode("3200%d".formatted(farmSequence % 10))
                .address("테스트 주소 " + farmSequence)
                .latitude(36.0 + farmSequence * 0.01)
                .longitude(126.0 + farmSequence * 0.01)
                .animalType(animalType)
                .livestockCount(livestockCount)
                .monthlyFeedQuantity(monthlyFeedQuantity)
                .preferredFeed("테스트 사료")
                .recurringDeliveryDay(1 + (farmSequence % 28))
                .center(center)
                .distanceKm(distanceKm)
                .status(status)
                .createdAt(LocalDateTime.now())
                .build());
    }
}
