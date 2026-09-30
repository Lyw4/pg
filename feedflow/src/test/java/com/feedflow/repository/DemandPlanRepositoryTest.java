package com.feedflow.repository;

import com.feedflow.admin.dto.CenterAnimalQuantityRow;
import com.feedflow.admin.dto.DeliveryScheduleRow;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * 수요 계획 집계 JPQL 검증.
 *
 * <h3>왜 이 테스트가 필요한가</h3>
 * 세 쿼리의 규칙이 모두 <b>JPQL 문자열 안에만</b> 있다.
 * <ul>
 *     <li>공급 — 출고 가능한 구역(보관 · 출고 대기)만, 만료 로트 제외, 사용 중인 구역만.
 *         이 조건이 {@code findAllocatableByProductId} 와 <b>같아야</b> 한다.
 *         한쪽만 바뀌면 "출고는 되는데 계획 화면에서는 부족" 같은 모순이 생긴다.</li>
 *     <li>수요 — 거래 중({@code ACTIVE}) 농장만. 보류 농장의 물량을 수요로 잡으면
 *         나가지 않을 사료를 기준으로 부족하다고 판단하게 된다.</li>
 *     <li>배송 일정 — 거래 중 농장만, 배송일별 묶음.</li>
 * </ul>
 * 서비스 단위 테스트는 리포지토리를 목으로 대체하므로 이 조건들을 전혀 확인하지 못한다.
 */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.sql.init.mode=never",
        "spring.jpa.show-sql=false"
})
@DisplayName("수요 계획 집계 JPQL 테스트")
class DemandPlanRepositoryTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 31);

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private FarmCustomerRepository farmCustomerRepository;

    private Center center;
    private Product cattleFeed;
    private Product poultryFeed;

    private int binSequence = 0;
    private int lotSequence = 0;
    private int farmSequence = 0;

    @BeforeEach
    void setUp() {
        center = entityManager.persist(Center.builder()
                .centerCode("C1-YS").name("충남 예산 센터").region("충남 서북부")
                .active(true).createdAt(LocalDateTime.now())
                .build());

        cattleFeed = persistProduct("FD-CT-001", "육성우 사료", AnimalType.CATTLE);
        poultryFeed = persistProduct("FD-PL-001", "육계 전기 사료", AnimalType.POULTRY);
    }

    /* ==================================================================
     * 공급 집계 — 출고 가능한 재고만
     * ================================================================== */

    @Test
    @DisplayName("보관 · 출고 대기 구역의 재고만 공급으로 센다")
    void supplyCountsOnlyShippableBins() {
        ProductLot lot = persistLot(cattleFeed, TODAY.plusDays(60));

        persistInventory(lot, bin(BinPurpose.STORAGE, true), 100);
        persistInventory(lot, bin(BinPurpose.SHIPPING, true), 50);
        persistInventory(lot, bin(BinPurpose.RECEIVING, true), 200);   // 검수 전
        persistInventory(lot, bin(BinPurpose.INSPECTION, true), 300);  // 검수 중
        persistInventory(lot, bin(BinPurpose.IN_TRANSIT, true), 400);  // 트럭 위
        flush();

        List<CenterAnimalQuantityRow> supply =
                inventoryRepository.findAllocatableStockByCenterAndAnimalType(TODAY);

        assertThat(supply).hasSize(1);
        assertThat(supply.get(0).amount())
                .as("100 + 50 만 센다. 전체라면 1,050 이 된다")
                .isEqualTo(150);
    }

    @Test
    @DisplayName("사용 중지된 구역과 만료된 로트는 공급에서 빠진다")
    void supplyExcludesInactiveBinAndExpiredLot() {
        ProductLot fresh = persistLot(cattleFeed, TODAY.plusDays(30));
        ProductLot expired = persistLot(cattleFeed, TODAY.minusDays(1));

        persistInventory(fresh, bin(BinPurpose.STORAGE, true), 100);
        persistInventory(fresh, bin(BinPurpose.STORAGE, false), 500);   // 사용 중지 구역
        persistInventory(expired, bin(BinPurpose.STORAGE, true), 700);  // 만료 로트
        flush();

        List<CenterAnimalQuantityRow> supply =
                inventoryRepository.findAllocatableStockByCenterAndAnimalType(TODAY);

        assertThat(supply.get(0).amount())
                .as("조건은 findAllocatableByProductId 와 같아야 한다")
                .isEqualTo(100);
    }

    @Test
    @DisplayName("축종이 다른 품목은 다른 행으로 집계된다")
    void supplyIsGroupedByAnimalType() {
        persistInventory(persistLot(cattleFeed, TODAY.plusDays(60)),
                bin(BinPurpose.STORAGE, true), 100);
        persistInventory(persistLot(poultryFeed, TODAY.plusDays(60)),
                bin(BinPurpose.STORAGE, true), 250);
        flush();

        List<CenterAnimalQuantityRow> supply =
                inventoryRepository.findAllocatableStockByCenterAndAnimalType(TODAY);

        assertThat(supply)
                .hasSize(2)
                .extracting(CenterAnimalQuantityRow::animalType, CenterAnimalQuantityRow::amount)
                .containsExactlyInAnyOrder(
                        tuple(AnimalType.CATTLE, 100),
                        tuple(AnimalType.POULTRY, 250));
    }

    /* ==================================================================
     * 수요 집계 — 거래 중인 농장만
     * ================================================================== */

    @Test
    @DisplayName("거래 보류 농장의 물량은 수요에서 빠진다")
    void demandExcludesPausedFarms() {
        persistFarm(AnimalType.CATTLE, 720, CustomerStatus.ACTIVE, 1);
        persistFarm(AnimalType.CATTLE, 850, CustomerStatus.ACTIVE, 15);
        persistFarm(AnimalType.CATTLE, 2380, CustomerStatus.PAUSED, 1);
        flush();

        List<CenterAnimalQuantityRow> demand = farmCustomerRepository.findDemandByCenterAndAnimalType();

        assertThat(demand).hasSize(1);
        assertThat(demand.get(0).amount())
                .as("720 + 850 만 더한다. 보류 2,380 을 넣으면 3,950 이 된다")
                .isEqualTo(1570);
    }

    @Test
    @DisplayName("수요는 센터 × 축종으로 묶인다 (공급과 같은 축)")
    void demandIsGroupedByCenterAndAnimalType() {
        persistFarm(AnimalType.CATTLE, 720, CustomerStatus.ACTIVE, 1);
        persistFarm(AnimalType.POULTRY, 3100, CustomerStatus.ACTIVE, 3);
        persistFarm(AnimalType.POULTRY, 1980, CustomerStatus.ACTIVE, 5);
        flush();

        List<CenterAnimalQuantityRow> demand = farmCustomerRepository.findDemandByCenterAndAnimalType();

        assertThat(demand)
                .hasSize(2)
                .extracting(CenterAnimalQuantityRow::animalType, CenterAnimalQuantityRow::amount)
                .containsExactlyInAnyOrder(
                        tuple(AnimalType.CATTLE, 720),
                        tuple(AnimalType.POULTRY, 5080));
        assertThat(demand.get(0).centerId())
                .as("공급 결과와 이 키로 맞물린다")
                .isEqualTo(center.getCenterId());
    }

    @Test
    @DisplayName("거래 중인 농장이 없으면 수요는 빈 결과다 (0 행이 아니라 행 자체가 없다)")
    void demandIsEmptyWhenNoActiveFarm() {
        persistFarm(AnimalType.CATTLE, 720, CustomerStatus.PAUSED, 1);
        flush();

        assertThat(farmCustomerRepository.findDemandByCenterAndAnimalType()).isEmpty();
    }

    /* ==================================================================
     * 정기 배송 일정
     * ================================================================== */

    @Test
    @DisplayName("배송일별로 농장 수와 물량을 묶는다")
    void deliveryScheduleGroupsByDay() {
        persistFarm(AnimalType.CATTLE, 720, CustomerStatus.ACTIVE, 1);
        persistFarm(AnimalType.POULTRY, 2380, CustomerStatus.ACTIVE, 1);
        persistFarm(AnimalType.PIG, 1850, CustomerStatus.ACTIVE, 15);
        persistFarm(AnimalType.CATTLE, 900, CustomerStatus.PAUSED, 15);  // 보류
        flush();

        List<DeliveryScheduleRow> schedule = farmCustomerRepository.findDeliverySchedule();

        assertThat(schedule).hasSize(2);

        DeliveryScheduleRow first = schedule.get(0);
        assertThat(first.day()).isEqualTo(1);
        assertThat(first.farms()).isEqualTo(2);
        assertThat(first.amount()).isEqualTo(3100);
        assertThat(first.label()).isEqualTo("매월 1일");

        DeliveryScheduleRow fifteenth = schedule.get(1);
        assertThat(fifteenth.day()).isEqualTo(15);
        assertThat(fifteenth.farms())
                .as("보류 농장은 그날 나갈 물량이 없으므로 세지 않는다")
                .isEqualTo(1);
        assertThat(fifteenth.amount()).isEqualTo(1850);
    }

    @Test
    @DisplayName("배송일 오름차순으로 정렬된다")
    void deliveryScheduleIsOrderedByDay() {
        persistFarm(AnimalType.CATTLE, 100, CustomerStatus.ACTIVE, 28);
        persistFarm(AnimalType.CATTLE, 100, CustomerStatus.ACTIVE, 5);
        persistFarm(AnimalType.CATTLE, 100, CustomerStatus.ACTIVE, 15);
        flush();

        assertThat(farmCustomerRepository.findDeliverySchedule())
                .extracting(DeliveryScheduleRow::day)
                .containsExactly(5, 15, 28);
    }

    /* ==================================================================
     * 픽스처
     * ================================================================== */

    private void flush() {
        entityManager.flush();
        entityManager.clear();
    }

    private Product persistProduct(String code, String name, AnimalType animalType) {
        return entityManager.persist(Product.builder()
                .productCode(code).name(name)
                .animalType(animalType).productType(ProductType.FEED)
                .weightKg(20).price(30000L)
                .totalStock(0).safetyStock(10).shelfLifeDays(180)
                .active(true)
                .build());
    }

    private ProductLot persistLot(Product product, LocalDate expiration) {
        lotSequence++;
        return entityManager.persist(ProductLot.builder()
                .product(product)
                .lotNo("LOT-%03d".formatted(lotSequence))
                .manufacturedDate(expiration.minusDays(180))
                .expirationDate(expiration)
                .lotQuantity(0)
                .build());
    }

    private WarehouseBin bin(BinPurpose purpose, boolean active) {
        binSequence++;
        return entityManager.persist(WarehouseBin.builder()
                .binCode("YS-%s-%02d".formatted(purpose.name().charAt(0), binSequence))
                .center(center)
                .zone("Z")
                .binPurpose(purpose)
                .posX(binSequence).posY(1).posWidth(1).posHeight(1)
                .maxCapacity(1000)
                .active(active)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private void persistInventory(ProductLot lot, WarehouseBin bin, int quantity) {
        entityManager.persist(Inventory.createForInbound(lot, bin, quantity));
    }

    private void persistFarm(AnimalType animalType,
                             int monthlyFeedQuantity,
                             CustomerStatus status,
                             int deliveryDay) {
        farmSequence++;
        entityManager.persist(FarmCustomer.builder()
                .farmCode("F-TEST-%02d".formatted(farmSequence))
                .farmName("테스트 농장 " + farmSequence)
                .representativeName("대표" + farmSequence)
                .phone("010-0000-00%02d".formatted(farmSequence))
                .postalCode("32400")
                .address("테스트 주소 " + farmSequence)
                .latitude(36.7)
                .longitude(126.7)
                .animalType(animalType)
                .livestockCount(100)
                .monthlyFeedQuantity(monthlyFeedQuantity)
                .preferredFeed("테스트 사료")
                .recurringDeliveryDay(deliveryDay)
                .center(center)
                .distanceKm(5.0)
                .status(status)
                .createdAt(LocalDateTime.now())
                .build());
    }
}
