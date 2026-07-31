package com.feedflow.repository;

import com.feedflow.domain.AnimalType;
import com.feedflow.domain.BinPurpose;
import com.feedflow.domain.Center;
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

/**
 * FEFO 출고 후보 조회 JPQL 검증.
 * <p>
 * 이 조건들은 <b>JPQL 안에만</b> 있어서 서비스 단위 테스트(리포지토리를 목으로 대체)로는
 * 확인할 수 없다. 실제 H2 에 데이터를 넣고 쿼리를 돌려야 한다.
 *
 * <h3>왜 이 테스트가 필요했나</h3>
 * 원래 이 쿼리는 {@code binPurpose} 를 전혀 걸러내지 않았다. 그래서
 * <b>입고 검수 전 재고와 운송 중(트럭 위) 재고까지 출고 후보에 들어갔다.</b>
 * 시드에 대기 구역 재고가 0이라 아무도 눈치채지 못했지만, 입고 등록으로 입고 대기
 * 구역에 물건을 넣는 순간 검수도 하지 않은 물건이 고객에게 나갈 수 있었다.
 */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.sql.init.mode=never",
        "spring.jpa.show-sql=false"
})
@DisplayName("FEFO 출고 후보 조회 JPQL 테스트")
class AllocatableStockRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private InventoryRepository inventoryRepository;

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 28);

    private int binSequence = 0;
    private Center center;
    private Product feed;

    @BeforeEach
    void setUp() {
        center = entityManager.persist(Center.builder()
                .centerCode("C1-YS").name("충남 예산 센터").region("충남 서북부")
                .active(true).createdAt(LocalDateTime.now())
                .build());
        feed = entityManager.persist(Product.builder()
                .productCode("FD-PL-003").name("육계 후기 사료")
                .animalType(AnimalType.POULTRY).productType(ProductType.FEED)
                .weightKg(20).price(23000L)
                .totalStock(0).safetyStock(10).shelfLifeDays(90)
                .active(true)
                .build());
    }

    /**
     * 핵심 검증. 재고가 있다고 다 내보낼 수 있는 것이 아니다.
     * <p>
     * 보관 · 출고 대기 구역의 재고만 후보가 되어야 한다. 입고 대기와 검수 구역은
     * 아직 검수를 통과하지 않았고, 운송 중은 트럭 위라 집어올 수 없다.
     */
    @Test
    @DisplayName("보관 · 출고 대기 구역만 출고 후보가 되고 입고 대기 · 검수 · 운송 중은 제외된다")
    void onlyStorageAndShippingAreAllocatable() {
        ProductLot lot = persistLot("LOT-PL-2623", TODAY.plusDays(70));

        persistInventory(lot, persistBin("YS-PL-01", BinPurpose.STORAGE), 95);
        persistInventory(lot, persistBin("YS-S-01", BinPurpose.SHIPPING), 60);
        persistInventory(lot, persistBin("YS-R-01", BinPurpose.RECEIVING), 120);
        persistInventory(lot, persistBin("YS-I-01", BinPurpose.INSPECTION), 40);
        persistInventory(lot, persistBin("TRANSIT-C1-YS", BinPurpose.IN_TRANSIT), 30);

        List<Inventory> rows = inventoryRepository
                .findAllocatableByProductId(feed.getProductId(), TODAY);

        assertThat(rows)
                .extracting(i -> i.getBin().getBinCode())
                .as("검수 전 재고나 트럭 위 재고가 출고 후보에 들어가면 안 된다")
                .containsExactlyInAnyOrder("YS-PL-01", "YS-S-01");

        assertThat(rows.stream().mapToInt(Inventory::getQuantity).sum())
                .as("전체 재고 345 중 출고 가능한 것은 155 뿐이다")
                .isEqualTo(155);
    }

    /**
     * {@code Product.totalStock} 은 전국 전체이고 출고 가능 수량은 그보다 적을 수 있다.
     * "전체 재고는 있는데 출고 가능 재고가 부족" 한 상태는 오류가 아니라 정상이다.
     */
    @Test
    @DisplayName("입고 대기 구역에만 재고가 있으면 출고 후보가 하나도 없다")
    void receivingOnlyMeansNothingAllocatable() {
        ProductLot lot = persistLot("LOT-PL-2624", TODAY.plusDays(70));
        persistInventory(lot, persistBin("YS-R-01", BinPurpose.RECEIVING), 200);

        assertThat(inventoryRepository.findAllocatableByProductId(feed.getProductId(), TODAY))
                .as("검수 전 물건 200포대가 있어도 출고할 수는 없다. "
                    + "'구역 간 이동' 으로 보관 구역에 넣어야 가용 재고가 된다")
                .isEmpty();
    }

    /**
     * 미리보기(여러 품목 일괄 조회)와 실제 출고(단건 조회)의 조건이 어긋나면
     * 미리보기에서는 보이던 재고가 출고 때 사라진다(또는 그 반대).
     */
    @Test
    @DisplayName("여러 품목 일괄 조회도 단건 조회와 같은 구역 조건을 쓴다")
    void batchQueryUsesSameBinCondition() {
        ProductLot lot = persistLot("LOT-PL-2625", TODAY.plusDays(70));
        persistInventory(lot, persistBin("YS-PL-01", BinPurpose.STORAGE), 50);
        persistInventory(lot, persistBin("YS-R-01", BinPurpose.RECEIVING), 70);

        List<Inventory> single = inventoryRepository
                .findAllocatableByProductId(feed.getProductId(), TODAY);
        List<Inventory> batch = inventoryRepository
                .findAllocatableByProductIds(List.of(feed.getProductId()), TODAY);

        assertThat(batch)
                .extracting(i -> i.getBin().getBinCode())
                .isEqualTo(single.stream().map(i -> i.getBin().getBinCode()).toList());
        assertThat(batch).extracting(i -> i.getBin().getBinCode()).containsExactly("YS-PL-01");
    }

    @Test
    @DisplayName("사용 중지된 구역과 유통기한이 지난 로트는 여전히 제외된다")
    void keepsExistingExclusions() {
        ProductLot fresh = persistLot("LOT-FRESH", TODAY.plusDays(70));
        ProductLot expired = persistLot("LOT-EXPIRED", TODAY.minusDays(1));

        persistInventory(fresh, persistBin("YS-PL-01", BinPurpose.STORAGE), 50);
        persistInventory(fresh, persistBin("YS-PL-02", BinPurpose.STORAGE, false), 50);
        persistInventory(expired, persistBin("YS-PL-03", BinPurpose.STORAGE), 50);

        assertThat(inventoryRepository.findAllocatableByProductId(feed.getProductId(), TODAY))
                .extracting(i -> i.getBin().getBinCode())
                .containsExactly("YS-PL-01");
    }

    /* ------------------------------------------------------------------
     * 픽스처
     * ------------------------------------------------------------------ */

    private WarehouseBin persistBin(String binCode, BinPurpose purpose) {
        return persistBin(binCode, purpose, true);
    }

    private WarehouseBin persistBin(String binCode, BinPurpose purpose, boolean active) {
        int seq = ++binSequence;
        return entityManager.persist(WarehouseBin.builder()
                .binCode(binCode)
                .center(center)
                .zone("PL")
                .binPurpose(purpose)
                .rack("01")
                .binLevel(1)
                .maxCapacity(500)
                .posX(seq).posY(seq).posWidth(1).posHeight(1)
                .active(active)
                .build());
    }

    private ProductLot persistLot(String lotNo, LocalDate expiration) {
        return entityManager.persist(ProductLot.builder()
                .product(feed)
                .lotNo(lotNo)
                .manufacturedDate(expiration.minusDays(90))
                .expirationDate(expiration)
                .lotQuantity(0)
                .build());
    }

    private void persistInventory(ProductLot lot, WarehouseBin bin, int quantity) {
        entityManager.persist(Inventory.builder()
                .lot(lot)
                .bin(bin)
                .quantity(quantity)
                .updatedAt(LocalDateTime.now())
                .build());
    }
}
