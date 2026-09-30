package com.feedflow.repository;

import com.feedflow.admin.dto.DefectStatRow;
import com.feedflow.domain.AnimalType;
import com.feedflow.domain.BinPurpose;
import com.feedflow.domain.Center;
import com.feedflow.domain.DefectRecord;
import com.feedflow.domain.DefectResolution;
import com.feedflow.domain.DefectStage;
import com.feedflow.domain.DefectStatus;
import com.feedflow.domain.DefectType;
import com.feedflow.domain.Manufacturer;
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
 * 불량 기록 조회 · 집계 JPQL 검증.
 *
 * <h3>왜 이 테스트가 필요한가</h3>
 * 이 리포지토리의 규칙은 전부 <b>JPQL 문자열 안에만</b> 있다. 서비스 단위 테스트는
 * 리포지토리를 목으로 대체하므로 아래 것들을 하나도 확인하지 못한다.
 * <ul>
 *     <li><b>left join</b> — 제조사가 등록되지 않은 품목의 불량이 목록과 집계에서
 *         사라지지 않아야 한다. {@code join} 하나만 잘못 써도 그 행이 통째로 빠지는데,
 *         에러가 아니라 "결과가 조용히 줄어드는" 방식으로 틀린다.</li>
 *     <li><b>정렬</b> — 미처리 우선 + 오래된 것부터. 최신순으로 두면 오래 방치된 건이
 *         목록 끝으로 밀려 영원히 보이지 않는다.</li>
 *     <li><b>coalesce</b> — 제조사 미등록을 '미등록' 으로 묶어 합계를 전체와 맞춘다.</li>
 *     <li><b>관리번호 최댓값</b> — 접두어가 같은 구간에서만 최댓값을 찾아야 한다.</li>
 * </ul>
 */
@DataJpaTest
@TestPropertySource(properties = {
        "spring.sql.init.mode=never",
        "spring.jpa.show-sql=false"
})
@DisplayName("불량 기록 리포지토리 테스트")
class DefectRecordRepositoryTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 31);

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private DefectRecordRepository defectRecordRepository;

    @Autowired
    private ManufacturerRepository manufacturerRepository;

    private Center yesan;
    private Center gimje;

    private Manufacturer daehan;

    /** 제조사가 등록된 품목 */
    private Product withManufacturer;

    /** 제조사가 등록되지 않은 품목 — left join 이 아니면 사라진다 */
    private Product withoutManufacturer;

    private int lotSequence = 0;
    private int binSequence = 0;
    private int defectSequence = 0;

    @BeforeEach
    void setUp() {
        yesan = persistCenter("C1-YS", "충남 예산 센터");
        gimje = persistCenter("C2-GJ", "전북 김제 센터");

        daehan = entityManager.persist(Manufacturer.builder()
                .name("대한사료(주)")
                .businessNumber("312-81-40021")
                .phone("041-330-7001")
                .contactName("박영수")
                .active(true)
                .createdAt(LocalDateTime.now())
                .build());

        withManufacturer = persistProduct("FD-CT-001", "육성우 사료", daehan);
        withoutManufacturer = persistProduct("FD-PL-004", "산란오리 사료", null);
    }

    /* ==================================================================
     * left join — 제조사 없는 품목이 사라지지 않아야 한다
     * ================================================================== */

    @Test
    @DisplayName("제조사가 등록되지 않은 품목의 불량도 목록에 나온다")
    void searchIncludesDefectsOfProductWithoutManufacturer() {
        persistDefect(lotOf(withManufacturer), bin(yesan, BinPurpose.RECEIVING),
                DefectType.DAMAGE, DefectStage.RECEIVING, 10);
        persistDefect(lotOf(withoutManufacturer), bin(yesan, BinPurpose.RECEIVING),
                DefectType.DAMAGE, DefectStage.RECEIVING, 6);

        List<DefectRecord> found = defectRecordRepository.search(null, null, null, null);

        assertThat(found)
                .as("inner join 으로 쓰면 제조사가 없는 품목의 불량이 통째로 빠진다")
                .hasSize(2);
    }

    @Test
    @DisplayName("구역이 지정되지 않은 불량(이관 중 파손)도 목록에 나온다")
    void searchIncludesDefectsWithoutBin() {
        persistDefect(lotOf(withManufacturer), null,
                DefectType.DAMAGE, DefectStage.TRANSFER, 10);

        List<DefectRecord> found = defectRecordRepository.search(null, null, null, null);

        assertThat(found)
                .as("센터 간 이관 중에는 어느 구역이라고 말할 수 없다. 그래도 기록은 남아야 한다")
                .hasSize(1);
        assertThat(found.get(0).getBin()).isNull();
    }

    /* ==================================================================
     * 정렬 — 미처리 우선, 그 안에서 오래된 것부터
     * ================================================================== */

    @Test
    @DisplayName("미처리 건이 먼저 나오고, 같은 상태 안에서는 오래된 것이 먼저다")
    void searchOrdersOpenFirstThenOldest() {
        // 일부러 뒤섞어 넣는다
        DefectRecord resolvedOld = persistDefect(
                lotOf(withManufacturer), bin(yesan, BinPurpose.RECEIVING),
                DefectType.DAMAGE, DefectStage.RECEIVING, 5, 30);
        resolvedOld.resolve(DefectResolution.DISPOSAL, "폐기 완료", "김책임");

        DefectRecord inspectingRecent = persistDefect(
                lotOf(withManufacturer), bin(yesan, BinPurpose.RECEIVING),
                DefectType.WET, DefectStage.RECEIVING, 5, 1);
        inspectingRecent.startInspection("검사 중");

        DefectRecord quarantinedOld = persistDefect(
                lotOf(withManufacturer), bin(yesan, BinPurpose.RECEIVING),
                DefectType.CONTAMINATION, DefectStage.RECEIVING, 5, 20);

        DefectRecord quarantinedRecent = persistDefect(
                lotOf(withManufacturer), bin(yesan, BinPurpose.RECEIVING),
                DefectType.EXPIRED, DefectStage.RECEIVING, 5, 2);

        entityManager.flush();
        entityManager.clear();

        List<DefectRecord> found = defectRecordRepository.search(null, null, null, null);

        assertThat(found)
                .as("격리(오래된 것 먼저) → 검사 중 → 처리 완료 순")
                .extracting(DefectRecord::getDefectNo)
                .containsExactly(
                        quarantinedOld.getDefectNo(),
                        quarantinedRecent.getDefectNo(),
                        inspectingRecent.getDefectNo(),
                        resolvedOld.getDefectNo());
    }

    /* ==================================================================
     * 필터
     * ================================================================== */

    @Test
    @DisplayName("상태 · 유형 · 단계 · 센터로 걸러낸다")
    void searchFiltersByEachCondition() {
        persistDefect(lotOf(withManufacturer), bin(yesan, BinPurpose.RECEIVING),
                DefectType.DAMAGE, DefectStage.RECEIVING, 10);
        persistDefect(lotOf(withManufacturer), bin(gimje, BinPurpose.STORAGE),
                DefectType.WET, DefectStage.STORAGE, 20);
        DefectRecord resolved = persistDefect(
                lotOf(withManufacturer), bin(yesan, BinPurpose.SHIPPING),
                DefectType.DAMAGE, DefectStage.SHIPPING, 4);
        resolved.resolve(DefectResolution.REWORK, "재작업", "김책임");
        entityManager.flush();

        assertThat(defectRecordRepository.search(DefectStatus.QUARANTINED, null, null, null))
                .as("격리 상태만").hasSize(2);
        assertThat(defectRecordRepository.search(null, DefectType.DAMAGE, null, null))
                .as("파손만").hasSize(2);
        assertThat(defectRecordRepository.search(null, null, DefectStage.STORAGE, null))
                .as("보관 중 발견만").hasSize(1);
        assertThat(defectRecordRepository.search(null, null, null, gimje.getCenterId()))
                .as("김제 센터만").hasSize(1);
        assertThat(defectRecordRepository.search(
                DefectStatus.QUARANTINED, DefectType.DAMAGE, DefectStage.RECEIVING,
                yesan.getCenterId()))
                .as("네 조건을 함께 걸었을 때").hasSize(1);
    }

    @Test
    @DisplayName("센터로 거를 때 구역이 없는 불량은 빠진다")
    void searchByCenterExcludesDefectsWithoutBin() {
        persistDefect(lotOf(withManufacturer), null,
                DefectType.DAMAGE, DefectStage.TRANSFER, 10);

        assertThat(defectRecordRepository.search(null, null, null, yesan.getCenterId()))
                .as("구역이 없으면 어느 센터인지 알 수 없으므로 센터 필터에 걸리지 않는다")
                .isEmpty();
        assertThat(defectRecordRepository.search(null, null, null, null))
                .as("전국 조회에서는 보여야 한다")
                .hasSize(1);
    }

    /* ==================================================================
     * 집계
     * ================================================================== */

    @Test
    @DisplayName("제조사별 집계는 미등록 품목을 '미등록' 으로 묶어 함께 센다")
    void manufacturerStatsGroupUnknownAsLabel() {
        persistDefect(lotOf(withManufacturer), bin(yesan, BinPurpose.RECEIVING),
                DefectType.DAMAGE, DefectStage.RECEIVING, 10);
        persistDefect(lotOf(withManufacturer), bin(yesan, BinPurpose.RECEIVING),
                DefectType.WET, DefectStage.RECEIVING, 5);
        persistDefect(lotOf(withoutManufacturer), bin(yesan, BinPurpose.RECEIVING),
                DefectType.DAMAGE, DefectStage.RECEIVING, 6);
        entityManager.flush();

        List<DefectStatRow> stats = defectRecordRepository.findStatsByManufacturer();

        assertThat(stats)
                .as("제외하면 합계가 전체(3건)와 맞지 않아 설명할 수 없다")
                .extracting(DefectStatRow::label, DefectStatRow::defectCount, DefectStatRow::quantity)
                .containsExactlyInAnyOrder(
                        tuple("대한사료(주)", 2L, 15L),
                        tuple("미등록", 1L, 6L));

        assertThat(stats.stream().mapToInt(DefectStatRow::count).sum())
                .as("집계 합계가 전체 건수와 같아야 한다")
                .isEqualTo(3);
    }

    @Test
    @DisplayName("유형별 · 단계별 집계는 건수와 수량을 함께 낸다")
    void typeAndStageStatsCountBothCasesAndQuantity() {
        // 낱개 파손이 여러 번
        persistDefect(lotOf(withManufacturer), bin(yesan, BinPurpose.RECEIVING),
                DefectType.DAMAGE, DefectStage.RECEIVING, 1);
        persistDefect(lotOf(withManufacturer), bin(yesan, BinPurpose.RECEIVING),
                DefectType.DAMAGE, DefectStage.RECEIVING, 2);
        // 대량 침수가 한 번
        persistDefect(lotOf(withManufacturer), bin(gimje, BinPurpose.STORAGE),
                DefectType.WET, DefectStage.STORAGE, 100);
        entityManager.flush();

        assertThat(defectRecordRepository.findStatsByType())
                .extracting(DefectStatRow::label, DefectStatRow::defectCount, DefectStatRow::quantity)
                .containsExactlyInAnyOrder(
                        tuple("DAMAGE", 2L, 3L),
                        tuple("WET", 1L, 100L));

        assertThat(defectRecordRepository.findStatsByStage())
                .extracting(DefectStatRow::label, DefectStatRow::defectCount)
                .containsExactlyInAnyOrder(
                        tuple("RECEIVING", 2L),
                        tuple("STORAGE", 1L));
    }

    @Test
    @DisplayName("미처리 건수는 처리 완료를 세지 않는다")
    void countOpenExcludesResolved() {
        persistDefect(lotOf(withManufacturer), bin(yesan, BinPurpose.RECEIVING),
                DefectType.DAMAGE, DefectStage.RECEIVING, 10);
        DefectRecord inspecting = persistDefect(
                lotOf(withManufacturer), bin(yesan, BinPurpose.RECEIVING),
                DefectType.WET, DefectStage.RECEIVING, 5);
        inspecting.startInspection(null);
        DefectRecord resolved = persistDefect(
                lotOf(withManufacturer), bin(yesan, BinPurpose.RECEIVING),
                DefectType.EXPIRED, DefectStage.RECEIVING, 3);
        resolved.resolve(DefectResolution.DISPOSAL, "폐기", "김책임");
        entityManager.flush();

        assertThat(defectRecordRepository.countOpen())
                .as("격리 + 검사 중 = 2")
                .isEqualTo(2);
    }

    /* ==================================================================
     * 방치된 건
     * ================================================================== */

    @Test
    @DisplayName("기준일보다 오래된 미처리 건만 방치로 잡는다")
    void findStaleReturnsOnlyOldOpenDefects() {
        DefectRecord old = persistDefect(
                lotOf(withManufacturer), bin(yesan, BinPurpose.RECEIVING),
                DefectType.DAMAGE, DefectStage.RECEIVING, 10, 12);
        persistDefect(lotOf(withManufacturer), bin(yesan, BinPurpose.RECEIVING),
                DefectType.WET, DefectStage.RECEIVING, 5, 2);
        DefectRecord oldButResolved = persistDefect(
                lotOf(withManufacturer), bin(yesan, BinPurpose.RECEIVING),
                DefectType.EXPIRED, DefectStage.RECEIVING, 3, 30);
        oldButResolved.resolve(DefectResolution.DISPOSAL, "폐기", "김책임");
        entityManager.flush();
        entityManager.clear();

        List<DefectRecord> stale = defectRecordRepository
                .findStale(LocalDateTime.now().minusDays(7));

        assertThat(stale)
                .as("처리가 끝난 건은 오래됐어도 방치가 아니다")
                .extracting(DefectRecord::getDefectNo)
                .containsExactly(old.getDefectNo());
    }

    /* ==================================================================
     * 관리번호 발급
     * ================================================================== */

    @Test
    @DisplayName("관리번호 최댓값은 같은 월 접두어 안에서만 찾는다")
    void findMaxDefectNoScopedToPrefix() {
        persistDefectWithNo("DF-2606-009");
        persistDefectWithNo("DF-2607-001");
        persistDefectWithNo("DF-2607-003");
        entityManager.flush();

        assertThat(defectRecordRepository.findMaxDefectNo("DF-2607-"))
                .as("다른 달의 더 큰 순번(009)에 끌려가면 안 된다")
                .contains("DF-2607-003");

        assertThat(defectRecordRepository.findMaxDefectNo("DF-2608-"))
                .as("그 달 첫 등록이면 비어 있어야 한다 (순번 001 부터 시작)")
                .isEmpty();
    }

    @Test
    @DisplayName("관리번호 중복 여부를 확인할 수 있다")
    void existsByDefectNo() {
        persistDefectWithNo("DF-2607-001");
        entityManager.flush();

        assertThat(defectRecordRepository.existsByDefectNo("DF-2607-001")).isTrue();
        assertThat(defectRecordRepository.existsByDefectNo("DF-2607-002")).isFalse();
    }

    /* ==================================================================
     * 제조사
     * ================================================================== */

    @Test
    @DisplayName("거래 중지된 제조사는 선택 목록에서 빠진다")
    void activeManufacturersOnly() {
        entityManager.persist(Manufacturer.builder()
                .name("세종사료공업").active(false)
                .createdAt(LocalDateTime.now()).build());
        entityManager.flush();

        assertThat(manufacturerRepository.findAllByOrderByNameAsc())
                .as("전체 목록에는 있어야 한다")
                .hasSize(2);
        assertThat(manufacturerRepository.findByActiveTrueOrderByNameAsc())
                .as("거래 중인 곳만 골라야 한다")
                .extracting(Manufacturer::getName)
                .containsExactly("대한사료(주)");
        assertThat(manufacturerRepository.existsByName("대한사료(주)")).isTrue();
    }

    /* ==================================================================
     * 헬퍼
     * ================================================================== */

    private Center persistCenter(String code, String name) {
        return entityManager.persist(Center.builder()
                .centerCode(code).name(name).region("테스트 권역")
                .active(true).createdAt(LocalDateTime.now())
                .build());
    }

    private Product persistProduct(String code, String name, Manufacturer manufacturer) {
        return entityManager.persist(Product.builder()
                .productCode(code).name(name)
                .manufacturer(manufacturer)
                .animalType(AnimalType.CATTLE).productType(ProductType.FEED)
                .weightKg(20).price(30000L)
                .totalStock(0).safetyStock(10).shelfLifeDays(180)
                .active(true)
                .build());
    }

    private ProductLot lotOf(Product product) {
        lotSequence++;
        return entityManager.persist(ProductLot.builder()
                .product(product)
                .lotNo("LOT-%03d".formatted(lotSequence))
                .manufacturedDate(TODAY.minusDays(30))
                .expirationDate(TODAY.plusDays(150))
                .lotQuantity(0)
                .build());
    }

    private WarehouseBin bin(Center center, BinPurpose purpose) {
        binSequence++;
        return entityManager.persist(WarehouseBin.builder()
                .binCode("B-%s-%02d".formatted(purpose.name().charAt(0), binSequence))
                .center(center)
                .zone("Z")
                .binPurpose(purpose)
                .posX(binSequence).posY(1).posWidth(1).posHeight(1)
                .maxCapacity(1000)
                .active(true)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private DefectRecord persistDefect(ProductLot lot, WarehouseBin bin,
                                       DefectType type, DefectStage stage, int quantity) {
        return persistDefect(lot, bin, type, stage, quantity, 0);
    }

    /**
     * @param daysAgo 며칠 전에 등록된 것으로 둘지 (정렬 · 방치 판정 검증용)
     */
    private DefectRecord persistDefect(ProductLot lot, WarehouseBin bin,
                                       DefectType type, DefectStage stage,
                                       int quantity, int daysAgo) {
        defectSequence++;
        DefectRecord record = DefectRecord.builder()
                .defectNo("DF-2607-%03d".formatted(defectSequence))
                .lot(lot)
                .bin(bin)
                .quantity(quantity)
                .defectType(type)
                .stage(stage)
                .status(DefectStatus.QUARANTINED)
                .reportedByName("이사원")
                .createdAt(LocalDateTime.now().minusDays(daysAgo))
                .build();
        return entityManager.persist(record);
    }

    private void persistDefectWithNo(String defectNo) {
        entityManager.persist(DefectRecord.builder()
                .defectNo(defectNo)
                .lot(lotOf(withManufacturer))
                .quantity(1)
                .defectType(DefectType.DAMAGE)
                .stage(DefectStage.RECEIVING)
                .status(DefectStatus.QUARANTINED)
                .reportedByName("이사원")
                .createdAt(LocalDateTime.now())
                .build());
    }
}
