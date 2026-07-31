package com.feedflow.admin.service;

import com.feedflow.admin.dto.DefectForm;
import com.feedflow.admin.dto.DefectRecordDto;
import com.feedflow.admin.dto.DefectResolveForm;
import com.feedflow.admin.dto.DefectSearchDto;
import com.feedflow.admin.dto.DefectStatRow;
import com.feedflow.common.exception.BusinessRuleException;
import com.feedflow.common.exception.ResourceNotFoundException;
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
import com.feedflow.repository.DefectRecordRepository;
import com.feedflow.repository.ProductLotRepository;
import com.feedflow.repository.WarehouseBinRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

/**
 * 불량 관리 서비스 단위 테스트.
 *
 * <h3>여기서 검증하는 것</h3>
 * <ul>
 *     <li><b>관리번호 발급</b> — 월별로 순번을 다시 시작하고, 형식이 어긋난 값이
 *         섞여 있어도 등록이 막히지 않아야 한다. 번호 하나 때문에 등록이 실패하면
 *         담당자는 기록을 아예 남기지 않는 쪽을 택한다.</li>
 *     <li><b>발견 단계 추정</b> — 구역만 고르면 단계가 정해진다. 담당자에게 같은 것을
 *         두 번 묻지 않기 위한 규칙이다.</li>
 *     <li><b>상태 전이</b> — 처리 완료는 되돌릴 수 없다.</li>
 *     <li><b>재고를 건드리지 않는다</b> — 이 서비스는 재고 리포지토리를 아예 주입받지
 *         않는다. 목으로 검증할 대상조차 없다는 것이 설계의 핵심이다.</li>
 *     <li><b>집계 라벨 변환</b> — 화면에 enum 이름(DAMAGE)이 아니라 한글이 나가야 한다.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("불량 관리 서비스 테스트")
class DefectServiceTest {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyMM");

    @Mock
    private DefectRecordRepository defectRecordRepository;

    @Mock
    private ProductLotRepository productLotRepository;

    @Mock
    private WarehouseBinRepository warehouseBinRepository;

    @InjectMocks
    private DefectService defectService;

    /* ==================================================================
     * 관리번호 발급
     * ================================================================== */

    @Test
    @DisplayName("그 달 첫 등록이면 순번 001 로 시작한다")
    void firstDefectOfMonthStartsAtOne() {
        givenLot(1L, lot(product("대한사료(주)")));
        givenBin(10L, bin(BinPurpose.RECEIVING));
        given(defectRecordRepository.findMaxDefectNo(anyString())).willReturn(Optional.empty());
        givenSaveReturnsArgument();

        DefectRecordDto saved = defectService.register(form(1L, 10L, 5), "이사원");

        assertThat(saved.getDefectNo()).isEqualTo(expectedNo(1));
    }

    @Test
    @DisplayName("같은 달에 이미 등록된 번호가 있으면 그 다음 순번을 쓴다")
    void continuesFromLastSequence() {
        givenLot(1L, lot(product("대한사료(주)")));
        givenBin(10L, bin(BinPurpose.RECEIVING));
        given(defectRecordRepository.findMaxDefectNo(anyString()))
                .willReturn(Optional.of(expectedNo(7)));
        givenSaveReturnsArgument();

        DefectRecordDto saved = defectService.register(form(1L, 10L, 5), "이사원");

        assertThat(saved.getDefectNo())
                .as("건수가 아니라 최댓값 +1 이어야 한다 (중간에 한 건 사라져도 재사용하지 않는다)")
                .isEqualTo(expectedNo(8));
    }

    @Test
    @DisplayName("형식이 어긋난 관리번호가 있어도 등록은 막히지 않는다")
    void malformedExistingNumberDoesNotBlockRegistration() {
        givenLot(1L, lot(product("대한사료(주)")));
        givenBin(10L, bin(BinPurpose.RECEIVING));
        given(defectRecordRepository.findMaxDefectNo(anyString()))
                .willReturn(Optional.of("DF-2607-XX"));
        givenSaveReturnsArgument();

        DefectRecordDto saved = defectService.register(form(1L, 10L, 5), "이사원");

        assertThat(saved.getDefectNo())
                .as("번호 하나 때문에 등록이 실패하면 담당자는 기록을 남기지 않게 된다")
                .isEqualTo(expectedNo(1));
    }

    /* ==================================================================
     * 발견 단계 추정
     * ================================================================== */

    @Test
    @DisplayName("단계를 비우면 구역 용도로 추정한다")
    void stageInferredFromBinPurpose() {
        givenLot(1L, lot(product("대한사료(주)")));
        givenBin(10L, bin(BinPurpose.STORAGE));
        given(defectRecordRepository.findMaxDefectNo(anyString())).willReturn(Optional.empty());
        givenSaveReturnsArgument();

        DefectForm form = form(1L, 10L, 5);
        form.setStage(null);

        assertThat(defectService.register(form, "이사원").getStage())
                .isEqualTo(DefectStage.STORAGE);
    }

    @Test
    @DisplayName("검수 구역에서 발견하면 입고 검사 단계로 본다")
    void inspectionBinMapsToReceivingStage() {
        givenLot(1L, lot(product("대한사료(주)")));
        givenBin(10L, bin(BinPurpose.INSPECTION));
        given(defectRecordRepository.findMaxDefectNo(anyString())).willReturn(Optional.empty());
        givenSaveReturnsArgument();

        DefectForm form = form(1L, 10L, 5);
        form.setStage(null);

        assertThat(defectService.register(form, "이사원").getStage())
                .as("검수 구역에 있다는 것은 아직 입고 절차 중이라는 뜻이다")
                .isEqualTo(DefectStage.RECEIVING);
    }

    @Test
    @DisplayName("구역도 단계도 없으면 등록을 거부한다")
    void rejectsWhenNeitherBinNorStageGiven() {
        givenLot(1L, lot(product("대한사료(주)")));

        DefectForm form = form(1L, null, 5);
        form.setStage(null);

        assertThatThrownBy(() -> defectService.register(form, "이사원"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("발견 단계");
    }

    @Test
    @DisplayName("구역이 없어도 단계를 직접 지정하면 등록된다 (이관 중 파손)")
    void allowsNoBinWhenStageGiven() {
        givenLot(1L, lot(product("대한사료(주)")));
        given(defectRecordRepository.findMaxDefectNo(anyString())).willReturn(Optional.empty());
        givenSaveReturnsArgument();

        DefectForm form = form(1L, null, 10);
        form.setStage(DefectStage.TRANSFER);

        DefectRecordDto saved = defectService.register(form, "이사원");

        assertThat(saved.getBinCode()).isNull();
        assertThat(saved.getStage()).isEqualTo(DefectStage.TRANSFER);
    }

    /* ==================================================================
     * 등록 검증
     * ================================================================== */

    @Test
    @DisplayName("수량이 0 이하면 거부한다")
    void rejectsNonPositiveQuantity() {
        givenLot(1L, lot(product("대한사료(주)")));
        givenBin(10L, bin(BinPurpose.RECEIVING));

        assertThatThrownBy(() -> defectService.register(form(1L, 10L, 0), "이사원"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("1 이상");
    }

    @Test
    @DisplayName("없는 로트를 지정하면 거부한다")
    void rejectsUnknownLot() {
        given(productLotRepository.findWithProductById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> defectService.register(form(99L, null, 5), "이사원"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("없는 구역을 지정하면 거부한다")
    void rejectsUnknownBin() {
        givenLot(1L, lot(product("대한사료(주)")));
        given(warehouseBinRepository.findWithCenterById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> defectService.register(form(1L, 99L, 5), "이사원"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    /* ==================================================================
     * 검사 착수 · 처리
     * ================================================================== */

    @Test
    @DisplayName("검사 착수는 격리를 검사 중으로 바꾼다")
    void startInspectionMovesToInspecting() {
        DefectRecord record = quarantined();
        given(defectRecordRepository.findWithDetailById(1L)).willReturn(Optional.of(record));

        DefectRecordDto updated = defectService.startInspection(1L, "개봉 검사 시작");

        assertThat(updated.getStatus()).isEqualTo(DefectStatus.INSPECTING);
        assertThat(updated.getMemo()).isEqualTo("개봉 검사 시작");
    }

    @Test
    @DisplayName("처리 방법을 고르면 처리 완료가 된다")
    void resolveCompletesRecord() {
        DefectRecord record = quarantined();
        given(defectRecordRepository.findWithDetailById(1L)).willReturn(Optional.of(record));

        DefectRecordDto updated = defectService.resolve(
                resolveForm(1L, DefectResolution.SUPPLIER_RETURN, "반품 접수 R-01"), "김책임");

        assertThat(updated.getStatus()).isEqualTo(DefectStatus.RESOLVED);
        assertThat(updated.getResolution()).isEqualTo(DefectResolution.SUPPLIER_RETURN);
        assertThat(updated.getResolvedByName()).isEqualTo("김책임");
        assertThat(updated.getResolvedAt()).isNotNull();
    }

    @Test
    @DisplayName("처리 방법을 비우면 검사 착수로만 처리한다")
    void resolveWithoutResolutionOnlyStartsInspection() {
        DefectRecord record = quarantined();
        given(defectRecordRepository.findWithDetailById(1L)).willReturn(Optional.of(record));

        DefectRecordDto updated = defectService.resolve(
                resolveForm(1L, null, "우선 검사만"), "이사원");

        assertThat(updated.getStatus())
                .as("한 폼이 두 가지 일을 한다 — 처리 방법의 유무로 갈린다")
                .isEqualTo(DefectStatus.INSPECTING);
        assertThat(updated.getResolution()).isNull();
    }

    @Test
    @DisplayName("이미 처리된 건은 되돌릴 수 없다")
    void resolvedRecordCannotBeReopened() {
        DefectRecord record = quarantined();
        record.resolve(DefectResolution.DISPOSAL, "폐기 완료", "김책임");
        given(defectRecordRepository.findWithDetailById(1L)).willReturn(Optional.of(record));

        assertThatThrownBy(() -> defectService.startInspection(1L, "다시 검사"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("되돌릴 수 없습니다");
    }

    @Test
    @DisplayName("처리 결과는 다음에 할 일을 함께 알려 준다")
    void resolutionCarriesFollowUp() {
        DefectRecord record = quarantined();
        given(defectRecordRepository.findWithDetailById(1L)).willReturn(Optional.of(record));

        DefectRecordDto updated = defectService.resolve(
                resolveForm(1L, DefectResolution.DISPOSAL, "폐기 결정"), "김책임");

        assertThat(updated.getFollowUp())
                .as("이 서비스는 재고를 줄이지 않는다. 폐기 화면으로 가야 한다는 것을 알려야 한다")
                .contains("재고 폐기 화면");
        assertThat(updated.isStockRemovalPending()).isTrue();
    }

    @Test
    @DisplayName("재작업 · 특채는 재고 차감이 남지 않는다")
    void reworkLeavesNoStockRemoval() {
        DefectRecord record = quarantined();
        given(defectRecordRepository.findWithDetailById(1L)).willReturn(Optional.of(record));

        DefectRecordDto updated = defectService.resolve(
                resolveForm(1L, DefectResolution.REWORK, "외포장 교체"), "김책임");

        assertThat(updated.isStockRemovalPending()).isFalse();
    }

    @Test
    @DisplayName("없는 불량 건을 처리하려 하면 거부한다")
    void rejectsUnknownDefect() {
        given(defectRecordRepository.findWithDetailById(99L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> defectService.startInspection(99L, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    /* ==================================================================
     * 조회 · 집계
     * ================================================================== */

    @Test
    @DisplayName("집계 라벨을 화면용 한글로 바꿔 내린다")
    void statsLabelsTranslatedToKorean() {
        given(defectRecordRepository.search(any(), any(), any(), any()))
                .willReturn(List.of());
        given(defectRecordRepository.findStatsByType())
                .willReturn(List.of(new DefectStatRow("DAMAGE", 3L, 30L)));
        given(defectRecordRepository.findStatsByStage())
                .willReturn(List.of(new DefectStatRow("RECEIVING", 3L, 30L)));
        given(defectRecordRepository.findStatsByManufacturer())
                .willReturn(List.of(new DefectStatRow("대한사료(주)", 3L, 30L)));

        DefectSearchDto search = defectService.search(null, null, null, null);

        assertThat(search.getTypeStats().get(0).label())
                .as("화면에 DAMAGE 가 아니라 한글이 나가야 한다")
                .isEqualTo(DefectType.DAMAGE.getDescription());
        assertThat(search.getStageStats().get(0).label())
                .isEqualTo(DefectStage.RECEIVING.getDescription());
        assertThat(search.getManufacturerStats().get(0).label())
                .as("제조사명은 이미 사람이 읽는 값이므로 그대로 둔다")
                .isEqualTo("대한사료(주)");
    }

    @Test
    @DisplayName("입고 검사 적발률은 전체 단계 집계에서 계산한다")
    void receivingCatchRateComputedFromStageStats() {
        given(defectRecordRepository.search(any(), any(), any(), any()))
                .willReturn(List.of());
        given(defectRecordRepository.findStatsByType()).willReturn(List.of());
        given(defectRecordRepository.findStatsByManufacturer()).willReturn(List.of());
        given(defectRecordRepository.findStatsByStage()).willReturn(List.of(
                new DefectStatRow("RECEIVING", 4L, 40L),
                new DefectStatRow("STORAGE", 1L, 10L),
                new DefectStatRow("SHIPPING", 1L, 10L),
                new DefectStatRow("TRANSFER", 1L, 10L)));

        assertThat(defectService.search(null, null, null, null).getReceivingCatchRate())
                .as("4 / 7 = 57%")
                .isEqualTo(57);
    }

    @Test
    @DisplayName("알 수 없는 라벨이 와도 집계가 깨지지 않는다")
    void unknownLabelFallsBack() {
        given(defectRecordRepository.search(any(), any(), any(), any()))
                .willReturn(List.of());
        given(defectRecordRepository.findStatsByStage()).willReturn(List.of());
        given(defectRecordRepository.findStatsByManufacturer()).willReturn(List.of());
        given(defectRecordRepository.findStatsByType())
                .willReturn(List.of(new DefectStatRow("SOMETHING_NEW", 1L, 1L)));

        assertThat(defectService.search(null, null, null, null).getTypeStats())
                .as("enum 에 값을 추가한 뒤 화면만 안 고친 상황에서 화면이 죽으면 안 된다")
                .hasSize(1);
    }

    @Test
    @DisplayName("요약은 조회된 목록을 기준으로 센다")
    void summaryCountsFilteredRows() {
        DefectRecord open = quarantined();
        DefectRecord inspecting = quarantined();
        inspecting.startInspection(null);
        DefectRecord resolved = quarantined();
        resolved.resolve(DefectResolution.SUPPLIER_RETURN, "반품", "김책임");

        given(defectRecordRepository.search(any(), any(), any(), any()))
                .willReturn(List.of(open, inspecting, resolved));
        given(defectRecordRepository.findStatsByType()).willReturn(List.of());
        given(defectRecordRepository.findStatsByStage()).willReturn(List.of());
        given(defectRecordRepository.findStatsByManufacturer()).willReturn(List.of());

        DefectSearchDto search = defectService.search(null, null, null, null);

        assertThat(search.getRowCount()).isEqualTo(3);
        assertThat(search.getOpenCount()).isEqualTo(2);
        assertThat(search.getQuarantinedCount()).isEqualTo(1);
        assertThat(search.getInspectingCount()).isEqualTo(1);
        assertThat(search.getResolvedCount()).isEqualTo(1);
        assertThat(search.getStockRemovalPendingCount())
                .as("반품으로 처리한 1건은 폐기 화면에서 차감해야 한다")
                .isEqualTo(1);
        assertThat(search.getTotalQuantity()).isEqualTo(30);
    }

    @Test
    @DisplayName("제조사가 없는 품목의 불량을 따로 센다")
    void countsDefectsWithUnknownManufacturer() {
        DefectRecord known = defect(lot(product("대한사료(주)")), bin(BinPurpose.RECEIVING));
        DefectRecord unknown = defect(lot(product(null)), bin(BinPurpose.RECEIVING));

        given(defectRecordRepository.search(any(), any(), any(), any()))
                .willReturn(List.of(known, unknown));
        given(defectRecordRepository.findStatsByType()).willReturn(List.of());
        given(defectRecordRepository.findStatsByStage()).willReturn(List.of());
        given(defectRecordRepository.findStatsByManufacturer()).willReturn(List.of());

        DefectSearchDto search = defectService.search(null, null, null, null);

        assertThat(search.getManufacturerUnknownCount())
                .as("반품을 검토해야 하는데 보낼 곳을 모르는 건이 몇 건인지 알려야 한다")
                .isEqualTo(1);
        assertThat(search.isHasManufacturerUnknown()).isTrue();
    }

    @Test
    @DisplayName("방치된 건은 기준 일수를 넘긴 미처리 건이다")
    void staleDefectsUseThreshold() {
        given(defectRecordRepository.findStale(any())).willReturn(List.of(quarantined()));

        assertThat(defectService.getStaleDefects()).hasSize(1);
    }

    @Test
    @DisplayName("미처리 건수는 리포지토리 집계를 그대로 쓴다")
    void openCountDelegatesToRepository() {
        given(defectRecordRepository.countOpen()).willReturn(5L);

        assertThat(defectService.getOpenCount()).isEqualTo(5L);
    }

    /* ==================================================================
     * 고정 데이터
     * ================================================================== */

    private void givenLot(Long lotId, ProductLot lot) {
        given(productLotRepository.findWithProductById(lotId)).willReturn(Optional.of(lot));
    }

    private void givenBin(Long binId, WarehouseBin bin) {
        given(warehouseBinRepository.findWithCenterById(binId)).willReturn(Optional.of(bin));
    }

    /** save 가 인자를 그대로 돌려주게 한다 (DB 없이 도메인 결과를 확인하기 위함) */
    private void givenSaveReturnsArgument() {
        given(defectRecordRepository.save(any(DefectRecord.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
    }

    private String expectedNo(int sequence) {
        return "DF-" + LocalDate.now().format(MONTH) + "-%03d".formatted(sequence);
    }

    private DefectForm form(Long lotId, Long binId, int quantity) {
        DefectForm form = new DefectForm();
        form.setLotId(lotId);
        form.setBinId(binId);
        form.setQuantity(quantity);
        form.setDefectType(DefectType.DAMAGE);
        form.setStage(DefectStage.RECEIVING);
        form.setMemo("하차 중 파손 확인");
        return form;
    }

    private DefectResolveForm resolveForm(Long defectId, DefectResolution resolution, String memo) {
        DefectResolveForm form = new DefectResolveForm();
        form.setDefectId(defectId);
        form.setResolution(resolution);
        form.setResolutionMemo(memo);
        return form;
    }

    private Product product(String manufacturerName) {
        Manufacturer manufacturer = manufacturerName == null ? null
                                    : Manufacturer.builder()
                                            .name(manufacturerName)
                                            .phone("041-330-7001")
                                            .active(true)
                                            .createdAt(LocalDateTime.now())
                                            .build();
        return Product.builder()
                .productCode("FD-CT-001").name("육성우 사료")
                .manufacturer(manufacturer)
                .animalType(AnimalType.CATTLE).productType(ProductType.FEED)
                .weightKg(20).price(30000L)
                .totalStock(100).safetyStock(10).shelfLifeDays(180)
                .active(true)
                .build();
    }

    private ProductLot lot(Product product) {
        return ProductLot.builder()
                .product(product)
                .lotNo("LOT-CT-2601")
                .manufacturedDate(LocalDate.now().minusDays(30))
                .expirationDate(LocalDate.now().plusDays(150))
                .lotQuantity(100)
                .build();
    }

    private WarehouseBin bin(BinPurpose purpose) {
        return WarehouseBin.builder()
                .binCode("YS-R-01")
                .center(Center.builder()
                        .centerCode("C1-YS").name("충남 예산 센터").region("충남 서북부")
                        .active(true).createdAt(LocalDateTime.now())
                        .build())
                .zone("R")
                .binPurpose(purpose)
                .posX(1).posY(1).posWidth(1).posHeight(1)
                .maxCapacity(300)
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private DefectRecord quarantined() {
        return defect(lot(product("대한사료(주)")), bin(BinPurpose.RECEIVING));
    }

    private DefectRecord defect(ProductLot lot, WarehouseBin bin) {
        return DefectRecord.builder()
                .defectNo(expectedNo(1))
                .lot(lot)
                .bin(bin)
                .quantity(10)
                .defectType(DefectType.DAMAGE)
                .stage(DefectStage.RECEIVING)
                .status(DefectStatus.QUARANTINED)
                .reportedByName("이사원")
                .createdAt(LocalDateTime.now())
                .build();
    }
}
