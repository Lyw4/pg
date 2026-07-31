package com.feedflow.repository;

import com.feedflow.admin.dto.WarehouseMapRow;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * 창고 2D 맵 집계 JPQL 검증 테스트.
 * <p>
 * H2 인메모리 DB 에 직접 데이터를 넣고 집계 쿼리가 정확한지 확인한다.
 * 초기 데이터(data.sql)가 섞이면 검증이 불가능하므로 sql.init 을 비활성화한다.
 *
 * <h3>검증 포인트</h3>
 * <ul>
 *     <li><b>재고가 전혀 없는 구역도 결과에 포함되는지</b> (outer join 유지 여부) — 가장 중요</li>
 *     <li>수량이 0 인 재고 행은 집계에서 제외하되 구역 자체는 남는지</li>
 *     <li>여러 로트가 섞인 구역의 수량 합계 / 로트 수 / 품목 수</li>
 *     <li>같은 품목의 로트가 2개면 로트 수는 2, 품목 수는 1인지 (distinct 동작)</li>
 *     <li>가장 먼저 만료되는 유통기한(min)을 정확히 뽑는지</li>
 *     <li>zone 필터 동작 / 사용 중지 구역도 도면에 포함되는지</li>
 * </ul>
 */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.sql.init.mode=never",
        "spring.jpa.show-sql=false"
})
@DisplayName("창고 2D 맵 집계 JPQL 테스트")
class WarehouseMapRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private WarehouseBinRepository warehouseBinRepository;

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 28);

    /** 좌표 자동 배정용 순번 */
    private int binSequence = 0;

    private Product feed;
    private Product supplement;

    /** 구역은 센터 없이 존재할 수 없으므로(optional = false) 먼저 만들어 둔다 */
    private Center center1;
    private Center center2;

    @BeforeEach
    void setUp() {
        center1 = persistCenter("WH1", "제1창고");
        center2 = persistCenter("WH2", "제2창고");

        feed = persistProduct("FD-CT-001", "육성우 사료", AnimalType.CATTLE, ProductType.FEED);
        supplement = persistProduct("SP-CT-001", "한우 영양제", AnimalType.CATTLE, ProductType.SUPPLEMENT);
    }

    @Test
    @DisplayName("재고가 전혀 없는 구역도 결과에 포함되고 적재량은 0 이다")
    void includesEmptyBin() {
        // given : 재고를 한 건도 넣지 않은 구역
        persistBin("A-01-01", "A", 500, true);

        // when
        List<WarehouseMapRow> rows = warehouseBinRepository.findWarehouseMapRows(null);

        // then
        assertThat(rows)
                .as("빈 구역이 결과에서 사라지면 도면에 구역이 아예 그려지지 않는다")
                .hasSize(1);

        WarehouseMapRow row = rows.get(0);
        assertThat(row.binCode()).isEqualTo("A-01-01");
        assertThat(row.loaded()).isZero();
        assertThat(row.lots()).isZero();
        assertThat(row.products()).isZero();
        assertThat(row.earliestExpiration()).isNull();
    }

    @Test
    @DisplayName("수량이 0 인 재고 행은 집계에서 제외하지만 구역은 결과에 남는다")
    void excludesZeroQuantityButKeepsBin() {
        // given
        WarehouseBin bin = persistBin("A-01-01", "A", 500, true);
        ProductLot lot = persistLot(feed, "LOT-A", TODAY.plusDays(100), 0);
        persistInventory(lot, bin, 0);      // 출고로 소진되어 0 이 된 행

        // when
        List<WarehouseMapRow> rows = warehouseBinRepository.findWarehouseMapRows(null);

        // then
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).loaded()).isZero();
        assertThat(rows.get(0).lots())
                .as("수량 0 인 로트는 보관 중으로 세지 않는다")
                .isZero();
    }

    @Test
    @DisplayName("여러 로트가 섞인 구역의 수량 합계 / 로트 수 / 품목 수를 정확히 집계한다")
    void aggregatesMultipleLots() {
        // given : 같은 구역에 사료 로트 2개 + 영양제 로트 1개
        WarehouseBin bin = persistBin("COLD-01", "COLD", 200, true);

        ProductLot feedLot1 = persistLot(feed, "LOT-F1", TODAY.plusDays(30), 90);
        ProductLot feedLot2 = persistLot(feed, "LOT-F2", TODAY.plusDays(60), 30);
        ProductLot supplementLot = persistLot(supplement, "LOT-S1", TODAY.plusDays(300), 70);

        persistInventory(feedLot1, bin, 90);
        persistInventory(feedLot2, bin, 30);
        persistInventory(supplementLot, bin, 70);

        // when
        List<WarehouseMapRow> rows = warehouseBinRepository.findWarehouseMapRows(null);

        // then
        assertThat(rows).hasSize(1);
        WarehouseMapRow row = rows.get(0);

        assertThat(row.loaded()).isEqualTo(190);
        assertThat(row.lots())
                .as("서로 다른 로트 3건")
                .isEqualTo(3);
        assertThat(row.products())
                .as("사료 로트 2개는 같은 품목이므로 품목 수는 2 (사료 + 영양제)")
                .isEqualTo(2);
        assertThat(row.earliestExpiration())
                .as("가장 먼저 만료되는 로트의 유통기한")
                .isEqualTo(TODAY.plusDays(30));
    }

    @Test
    @DisplayName("같은 품목의 로트가 여러 개면 로트 수만 늘고 품목 수는 1이다")
    void distinctCountsProductOnce() {
        WarehouseBin bin = persistBin("A-01-01", "A", 500, true);
        persistInventory(persistLot(feed, "LOT-1", TODAY.plusDays(10), 20), bin, 20);
        persistInventory(persistLot(feed, "LOT-2", TODAY.plusDays(20), 30), bin, 30);

        WarehouseMapRow row = warehouseBinRepository.findWarehouseMapRows(null).get(0);

        assertThat(row.lots()).isEqualTo(2);
        assertThat(row.products()).isEqualTo(1);
        assertThat(row.loaded()).isEqualTo(50);
    }

    @Test
    @DisplayName("같은 로트가 여러 구역에 나뉘어 있으면 각 구역에 자기 몫만 집계된다")
    void splitsLotAcrossBins() {
        WarehouseBin binA = persistBin("A-01-01", "A", 500, true);
        WarehouseBin binB = persistBin("B-01-01", "B", 600, true);

        ProductLot lot = persistLot(feed, "LOT-SPLIT", TODAY.plusDays(50), 150);
        persistInventory(lot, binA, 100);
        persistInventory(lot, binB, 50);

        List<WarehouseMapRow> rows = warehouseBinRepository.findWarehouseMapRows(null);

        assertThat(rows).hasSize(2);
        assertThat(rows)
                .extracting(WarehouseMapRow::binCode, WarehouseMapRow::loaded)
                .containsExactly(
                        tuple("A-01-01", 100),
                        tuple("B-01-01", 50));
    }

    @Test
    @DisplayName("센터를 지정하면 해당 센터의 구역만 조회한다")
    void filtersByCenter() {
        persistBin("A-01", center1, "A", BinPurpose.STORAGE, 500, true);
        persistBin("B-01", center1, "B", BinPurpose.STORAGE, 600, true);
        persistBin("COLD-01", center2, "COLD", BinPurpose.STORAGE, 200, true);

        assertThat(warehouseBinRepository.findWarehouseMapRows(center1.getCenterId()))
                .as("서로 떨어진 센터의 구역이 한 도면에 섞이면 실제 위치를 오해한다")
                .extracting(WarehouseMapRow::binCode)
                .containsExactly("A-01", "B-01");

        assertThat(warehouseBinRepository.findWarehouseMapRows(center2.getCenterId()))
                .extracting(WarehouseMapRow::binCode)
                .containsExactly("COLD-01");

        assertThat(warehouseBinRepository.findWarehouseMapRows(null))
                .as("센터가 null 이면 전체 조회")
                .hasSize(3);
    }

    @Test
    @DisplayName("입고 대기 등 보관 외 용도 구역도 도면에 그려지도록 조회에 포함한다")
    void includesNonStorageBin() {
        persistBin("R-01", center1, "R", BinPurpose.RECEIVING, 300, true);
        persistBin("S-01", center1, "S", BinPurpose.SHIPPING, 400, true);

        assertThat(warehouseBinRepository.findWarehouseMapRows(center1.getCenterId()))
                .extracting(WarehouseMapRow::purpose)
                .containsExactly(BinPurpose.RECEIVING, BinPurpose.SHIPPING);
    }

    @Test
    @DisplayName("사용 중지된 구역도 도면에 표시하기 위해 결과에 포함한다")
    void includesInactiveBin() {
        persistBin("A-03-01", "A", 400, false);

        List<WarehouseMapRow> rows = warehouseBinRepository.findWarehouseMapRows(null);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).isActive()).isFalse();
    }

    @Test
    @DisplayName("결과는 도면 좌표(위 → 아래, 왼쪽 → 오른쪽) 순으로 정렬된다")
    void ordersByPosition() {
        // 일부러 뒤섞어 저장한다
        persistBinAt("C-01", 9, 13, 7, 2);
        persistBinAt("A-01", 6, 1, 2, 6);
        persistBinAt("D-01", 17, 1, 7, 2);

        assertThat(warehouseBinRepository.findWarehouseMapRows(center1.getCenterId()))
                .as("도면을 위에서 아래로 읽는 순서와 같아야 구역 라벨 배치가 자연스럽다")
                .extracting(WarehouseMapRow::binCode)
                .containsExactly("A-01", "D-01", "C-01");
    }

    @Test
    @DisplayName("구역 1건 조회도 동일하게 집계되며 없는 구역은 빈 Optional 을 반환한다")
    void findsSingleBin() {
        WarehouseBin bin = persistBin("COLD-01", "COLD", 200, true);
        persistInventory(persistLot(supplement, "LOT-S1", TODAY.plusDays(300), 70), bin, 70);

        Optional<WarehouseMapRow> found =
                warehouseBinRepository.findWarehouseMapRowByBinId(bin.getBinId());

        assertThat(found).isPresent();
        assertThat(found.get().loaded()).isEqualTo(70);
        assertThat(found.get().lots()).isEqualTo(1);

        assertThat(warehouseBinRepository.findWarehouseMapRowByBinId(999L)).isEmpty();
    }

    /* ------------------------------------------------------------------
     * 픽스처
     * ------------------------------------------------------------------ */

    private Center persistCenter(String centerCode, String name) {
        return entityManager.persist(Center.builder()
                .centerCode(centerCode)
                .name(name)
                .region("수도권")
                .active(true)
                .build());
    }

    private Product persistProduct(String code, String name, AnimalType animalType, ProductType productType) {
        return entityManager.persist(Product.builder()
                .productCode(code)
                .name(name)
                .animalType(animalType)
                .productType(productType)
                .weightKg(25)
                .price(30000L)
                .totalStock(0)
                .safetyStock(10)
                .shelfLifeDays(180)
                .active(true)
                .build());
    }

    /** 보관 구역 (제1창고, 좌표는 순서대로 자동 배정) */
    private WarehouseBin persistBin(String binCode, String zone, int maxCapacity, boolean active) {
        return persistBin(binCode, center1, zone, BinPurpose.STORAGE, maxCapacity, active);
    }

    /** 좌표를 직접 지정하는 보관 구역 (정렬 검증용) */
    private WarehouseBin persistBinAt(String binCode, int posX, int posY, int posWidth, int posHeight) {
        return entityManager.persist(WarehouseBin.builder()
                .binCode(binCode)
                .center(center1)
                .zone(binCode.substring(0, 1))
                .binPurpose(BinPurpose.STORAGE)
                .rack("01")
                .binLevel(1)
                .maxCapacity(400)
                .posX(posX)
                .posY(posY)
                .posWidth(posWidth)
                .posHeight(posHeight)
                .active(true)
                .build());
    }

    private WarehouseBin persistBin(String binCode, Center center, String zone,
                                    BinPurpose binPurpose, int maxCapacity, boolean active) {
        // 좌표는 겹치지만 집계 쿼리 검증에는 영향이 없으므로 순번으로 단순 배정한다
        int seq = ++binSequence;
        return entityManager.persist(WarehouseBin.builder()
                .binCode(binCode)
                .center(center)
                .zone(zone)
                .binPurpose(binPurpose)
                .rack("01")
                .binLevel(1)
                .maxCapacity(maxCapacity)
                .posX(seq)
                .posY(seq)
                .posWidth(1)
                .posHeight(1)
                .active(active)
                .build());
    }

    private ProductLot persistLot(Product product, String lotNo, LocalDate expiration, int quantity) {
        return entityManager.persist(ProductLot.builder()
                .product(product)
                .lotNo(lotNo)
                .manufacturedDate(expiration.minusDays(180))
                .expirationDate(expiration)
                .lotQuantity(quantity)
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
