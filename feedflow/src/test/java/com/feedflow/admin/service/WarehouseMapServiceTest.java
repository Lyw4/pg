package com.feedflow.admin.service;

import com.feedflow.admin.dto.BinDetailDto;
import com.feedflow.admin.dto.WarehouseBinMapDto;
import com.feedflow.admin.dto.WarehouseFacilityDto;
import com.feedflow.admin.dto.WarehouseFloorPlanDto;
import com.feedflow.admin.dto.WarehouseMapRow;
import com.feedflow.admin.dto.WarehouseMapSummaryDto;
import com.feedflow.admin.dto.WarehouseMapZoneDto;
import com.feedflow.common.exception.ResourceNotFoundException;
import com.feedflow.domain.AnimalType;
import com.feedflow.domain.BinLoadStatus;
import com.feedflow.domain.BinPurpose;
import com.feedflow.domain.Inventory;
import com.feedflow.domain.Product;
import com.feedflow.domain.ProductLot;
import com.feedflow.domain.Center;
import com.feedflow.domain.ProductType;
import com.feedflow.domain.WarehouseBin;
import com.feedflow.repository.InventoryRepository;
import com.feedflow.repository.WarehouseBinRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * 창고 2D 평면도 서비스 단위 테스트.
 *
 * <h3>검증 포인트</h3>
 * <ul>
 *     <li>수용량 대비 적재율 계산 (반올림 / 0 나눗셈 / 초과 적재)</li>
 *     <li>적재율에 따른 상태(색상) 분류 경계값</li>
 *     <li>좌표 기반 자유 배치 (grid-area 변환)</li>
 *     <li>구역(Zone) 라벨의 경계 상자 계산</li>
 *     <li>입고/출고 대기 구역을 적재율 통계에서 제외하는지</li>
 *     <li>구역 상세 조회 (보관 재고 목록 / 존재하지 않는 구역)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WarehouseMapService 단위 테스트")
class WarehouseMapServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 28);

    /** 제1창고 (data.sql 시드의 centerId) */
    private static final Long CENTER_1_ID = 1L;

    /** 제2창고 — COLD 구역이 배치된 센터 */
    private static final Long CENTER_2_ID = 2L;

    @Mock
    private WarehouseBinRepository warehouseBinRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    @InjectMocks
    private WarehouseMapService warehouseMapService;

    /* ==================================================================
     * 적재율 계산
     * ================================================================== */

    @Nested
    @DisplayName("적재율 계산")
    class UsageRate {

        @ParameterizedTest(name = "적재 {0} / 수용 {1} → {2}%")
        @CsvSource({
                "0,   500, 0",
                "240, 500, 48",
                "190, 200, 95",
                "360, 600, 60",
                "210, 400, 53",     // 52.5 → 반올림 53
                "1,   400, 0",      // 0.25 → 반올림 0 (하지만 '비어있음'은 아니다)
                "500, 500, 100",
                "600, 500, 120"     // 초과 적재도 그대로 노출
        })
        @DisplayName("수용량 대비 적재율을 반올림해서 계산한다")
        void calculatesUsageRate(int loaded, int capacity, int expected) {
            assertThat(WarehouseBinMapDto.calculateUsageRate(loaded, capacity)).isEqualTo(expected);
        }

        @Test
        @DisplayName("최대 수용량이 0 이거나 음수면 0으로 나누지 않고 0% 를 반환한다")
        void zeroCapacity_returnsZero() {
            assertThat(WarehouseBinMapDto.calculateUsageRate(100, 0)).isZero();
            assertThat(WarehouseBinMapDto.calculateUsageRate(100, -1)).isZero();
        }

        @Test
        @DisplayName("초과 적재된 구역의 진행바는 100% 에서 멈추지만 실제 적재율은 그대로 보여준다")
        void overloaded_gaugeCappedButRateKept() {
            WarehouseBinMapDto bin = WarehouseBinMapDto.of(
                    storageRow(1L, "A-01", "A", 500, true, 600L, 2L, 2L, null, 6, 1, 2, 6), TODAY);

            assertThat(bin.getUsageRate()).isEqualTo(120);
            assertThat(bin.getUsageRateCapped()).isEqualTo(100);
            assertThat(bin.getRemainingCapacity())
                    .as("초과 적재 시 남은 여유는 음수가 아니라 0 이어야 한다")
                    .isZero();
        }
    }

    /* ==================================================================
     * 상태(색상) 분류
     * ================================================================== */

    @Nested
    @DisplayName("적재 상태 분류")
    class Status {

        @ParameterizedTest(name = "적재 {0} / 수용 {1} → {2}")
        @CsvSource({
                "0,   500, EMPTY",
                "1,   500, SPARE",      // 0% 로 반올림되지만 비어있지 않다
                "295, 500, SPARE",      // 59%
                "299, 500, NORMAL",     // 59.8% → 반올림 60 → 보통
                "300, 500, NORMAL",     // 정확히 60%
                "445, 500, NORMAL",     // 89%
                "450, 500, FULL",       // 정확히 90%
                "190, 200, FULL",
                "600, 500, FULL"
        })
        @DisplayName("적재율 구간에 따라 상태가 결정된다")
        void classifiesStatus(int loaded, int capacity, BinLoadStatus expected) {
            int rate = WarehouseBinMapDto.calculateUsageRate(loaded, capacity);
            assertThat(BinLoadStatus.of(loaded, rate)).isEqualTo(expected);
        }

        @Test
        @DisplayName("경계값 60% 는 보통, 90% 는 포화로 분류한다")
        void boundaryValues() {
            assertThat(BinLoadStatus.of(100, 59)).isEqualTo(BinLoadStatus.SPARE);
            assertThat(BinLoadStatus.of(100, 60)).isEqualTo(BinLoadStatus.NORMAL);
            assertThat(BinLoadStatus.of(100, 89)).isEqualTo(BinLoadStatus.NORMAL);
            assertThat(BinLoadStatus.of(100, 90)).isEqualTo(BinLoadStatus.FULL);
        }

        @Test
        @DisplayName("적재량이 0 이면 비율과 무관하게 비어있음으로 분류한다")
        void emptyBasedOnQuantityNotRate() {
            assertThat(BinLoadStatus.of(0, 0)).isEqualTo(BinLoadStatus.EMPTY);
        }

        @Test
        @DisplayName("유통기한 30일 이내 재고가 있는 구역만 임박으로 표시한다")
        void marksExpiringSoonBins() {
            WarehouseBinMapDto urgent = bin(500, 100L, TODAY.plusDays(5));
            WarehouseBinMapDto boundary = bin(500, 100L, TODAY.plusDays(30));
            WarehouseBinMapDto safe = bin(500, 100L, TODAY.plusDays(31));
            WarehouseBinMapDto expired = bin(500, 100L, TODAY.minusDays(3));
            WarehouseBinMapDto emptyBin = bin(500, 0L, null);

            assertThat(urgent.isExpiringSoon()).isTrue();
            assertThat(urgent.getEarliestDDayLabel()).isEqualTo("D-5");
            assertThat(boundary.isExpiringSoon())
                    .as("정확히 30일도 임박으로 본다 (대시보드 경고와 동일 기준)")
                    .isTrue();
            assertThat(safe.isExpiringSoon())
                    .as("31일 남은 재고는 임박이 아니다")
                    .isFalse();
            assertThat(expired.isExpiringSoon()).isTrue();
            assertThat(expired.getEarliestDDayLabel()).isEqualTo("만료 3일 경과");
            assertThat(expired.getEarliestDDayShortLabel())
                    .as("도면 사각형 안에서는 글자가 잘리지 않게 압축해서 쓴다")
                    .isEqualTo("만료+3");
            assertThat(emptyBin.isExpiringSoon())
                    .as("재고가 없으면 임박 표시도 없다")
                    .isFalse();
        }

        @Test
        @DisplayName("도면용 짧은 D-Day 라벨은 길이가 제한된다")
        void shortDDayLabel_isCompact() {
            assertThat(bin(500, 100L, TODAY.plusDays(12)).getEarliestDDayShortLabel())
                    .isEqualTo("D-12");
            assertThat(bin(500, 100L, TODAY).getEarliestDDayShortLabel())
                    .isEqualTo("오늘");
            assertThat(bin(500, 100L, TODAY.minusDays(40)).getEarliestDDayShortLabel())
                    .isEqualTo("만료+40");
            assertThat(bin(500, 0L, null).getEarliestDDayShortLabel())
                    .isEqualTo("-");

            // 사각형 폭이 좁아도 넘치지 않도록 6자 이내로 유지한다
            assertThat(bin(500, 100L, TODAY.minusDays(365)).getEarliestDDayShortLabel())
                    .hasSizeLessThanOrEqualTo(7);
        }

        @Test
        @DisplayName("사용 중지된 구역은 적재 상태색이 아니라 사용 중지로 표시한다")
        void inactiveBin_showsInactiveStyle() {
            WarehouseBinMapDto target = WarehouseBinMapDto.of(
                    storageRow(9L, "A-03", "A", 400, false, 0L, 0L, 0L, null, 6, 13, 2, 6), TODAY);

            assertThat(target.getStatus())
                    .as("계산된 상태 자체는 EMPTY 이지만")
                    .isEqualTo(BinLoadStatus.EMPTY);
            assertThat(target.getTileClass()).isEqualTo("ff-bin-inactive");
            assertThat(target.getStatusLabel()).isEqualTo("사용 중지");
        }

        @Test
        @DisplayName("입고/출고 대기 구역은 적재율 색이 아니라 용도별 고정색을 쓴다")
        void nonStorageBin_usesPurposeColor() {
            WarehouseBinMapDto receiving = WarehouseBinMapDto.of(
                    row(10L, "R-01", "R", BinPurpose.RECEIVING, 300, true,
                            250L, 1L, 1L, null, 1, 4, 4, 3), TODAY);
            WarehouseBinMapDto shipping = WarehouseBinMapDto.of(
                    row(23L, "S-01", "S", BinPurpose.SHIPPING, 400, true,
                            0L, 0L, 0L, null, 1, 7, 4, 3), TODAY);

            assertThat(receiving.isStorage()).isFalse();
            assertThat(receiving.getTileClass()).isEqualTo("ff-bin-receiving");
            assertThat(receiving.getStatusLabel()).isEqualTo("입고 대기");

            assertThat(shipping.getTileClass()).isEqualTo("ff-bin-shipping");
            assertThat(shipping.getStatusLabel()).isEqualTo("출고 대기");
        }
    }

    /* ==================================================================
     * 좌표 기반 자유 배치
     * ================================================================== */

    @Nested
    @DisplayName("도면 배치 좌표")
    class Layout {

        @Test
        @DisplayName("좌표와 크기를 CSS grid-area 문자열로 변환한다")
        void convertsToGridArea() {
            WarehouseBinMapDto target = WarehouseBinMapDto.of(
                    storageRow(5L, "B-01", "B", 600, true, 360L, 2L, 1L, null, 9, 4, 7, 2), TODAY);

            assertThat(target.getGridArea())
                    .as("grid-area: row / column / span height / span width")
                    .isEqualTo("4 / 9 / span 2 / span 7");
        }

        @Test
        @DisplayName("좁거나 납작한 사각형은 글자가 겹치지 않도록 화면에서 구분한다")
        void detectsNarrowAndFlatTiles() {
            WarehouseBinMapDto vertical = WarehouseBinMapDto.of(
                    storageRow(1L, "A-01", "A", 500, true, 240L, 1L, 1L, null, 6, 1, 2, 6), TODAY);
            WarehouseBinMapDto wide = WarehouseBinMapDto.of(
                    storageRow(5L, "B-01", "B", 600, true, 360L, 1L, 1L, null, 9, 1, 7, 2), TODAY);
            WarehouseBinMapDto flat = WarehouseBinMapDto.of(
                    storageRow(6L, "B-02", "B", 600, true, 360L, 1L, 1L, null, 9, 4, 7, 1), TODAY);

            assertThat(vertical.isNarrow()).isTrue();
            assertThat(vertical.isFlat()).isFalse();
            assertThat(wide.isNarrow()).isFalse();
            assertThat(flat.isFlat()).isTrue();
        }

        @Test
        @DisplayName("좌표가 없는 옛 데이터도 1,1 기본값으로 도면에 그려진다")
        void missingCoordinates_fallbackToOrigin() {
            WarehouseMapRow noCoords = new WarehouseMapRow(
                    1L, "A-01", "A", BinPurpose.STORAGE, "01", 1, 500, true,
                    null, null, null, null, 0L, 0L, 0L, null);

            WarehouseBinMapDto target = WarehouseBinMapDto.of(noCoords, TODAY);

            assertThat(target.getGridArea()).isEqualTo("1 / 1 / span 1 / span 1");
        }
    }

    /* ==================================================================
     * 평면도 조회
     * ================================================================== */

    @Nested
    @DisplayName("평면도 조회")
    class FloorPlan {

        @Test
        @DisplayName("센터 한 곳의 사각형 · 부대시설 · 구역 요약 · 전체 요약을 함께 내려준다")
        void returnsFloorPlan() {
            given(warehouseBinRepository.findWarehouseMapRows(CENTER_1_ID)).willReturn(List.of(
                    row(10L, "R-01", "R", BinPurpose.RECEIVING, 300, true, 250L, 1L, 1L, null, 1, 4, 4, 3),
                    storageRow(1L, "A-01", "A", 500, true, 240L, 2L, 2L, null, 6, 1, 2, 6),
                    storageRow(2L, "A-02", "A", 500, true, 170L, 1L, 1L, null, 6, 7, 2, 6),
                    storageRow(5L, "B-01", "B", 600, true, 360L, 2L, 1L, null, 9, 1, 7, 2)));

            WarehouseFloorPlanDto plan = warehouseMapService.getFloorPlan(CENTER_1_ID, TODAY);

            assertThat(plan.getBins()).hasSize(4);
            assertThat(plan.isEmpty()).isFalse();

            assertThat(plan.getFacilities())
                    .as("출입구 · 벽 · 검수실은 DB 가 아니라 센터별 상수로 내려온다")
                    .isNotEmpty()
                    .extracting(WarehouseFacilityDto::getLabel)
                    .contains("입고 출입구", "검수실");

            assertThat(plan.getZones())
                    .extracting(WarehouseMapZoneDto::getZone)
                    .as("도면 조회 순서(위 → 아래)를 유지해야 한다")
                    .containsExactly("R", "A", "B");
        }

        @Test
        @DisplayName("구역이 아직 없는 센터는 빈 도면으로 표시한다")
        void emptyCenter() {
            given(warehouseBinRepository.findWarehouseMapRows(CENTER_2_ID)).willReturn(List.of());

            WarehouseFloorPlanDto plan = warehouseMapService.getFloorPlan(CENTER_2_ID, TODAY);

            assertThat(plan.isEmpty()).isTrue();
            assertThat(plan.getZones()).isEmpty();
            assertThat(plan.getSummary().getUsageRate()).isZero();
        }

        /**
         * 센터가 늘어나도 조회한 센터의 구역만 도면에 그려져야 한다.
         * Warehouse enum 시절에는 창고 목록이 상수 두 개로 고정이었지만,
         * 이제 센터는 DB 에서 관리되므로 조회 조건이 그대로 전달되는지가 중요하다.
         */
        @Test
        @DisplayName("요청한 센터의 구역만 조회한다")
        void queriesOnlyRequestedCenter() {
            given(warehouseBinRepository.findWarehouseMapRows(CENTER_2_ID)).willReturn(List.of(
                    storageRow(8L, "COLD-01", "COLD", 200, true, 90L, 1L, 1L, null, 9, 1, 7, 2)));

            WarehouseFloorPlanDto plan = warehouseMapService.getFloorPlan(CENTER_2_ID, TODAY);

            assertThat(plan.getBins())
                    .extracting(WarehouseBinMapDto::getBinCode)
                    .containsExactly("COLD-01");
            verify(warehouseBinRepository).findWarehouseMapRows(CENTER_2_ID);
        }
    }

    /* ==================================================================
     * 구역(Zone) 요약
     * ================================================================== */

    @Nested
    @DisplayName("구역 요약과 라벨 위치")
    class ZoneSummary {

        @Test
        @DisplayName("구역에 속한 사각형들의 경계 상자로 라벨 위치를 계산한다")
        void calculatesBoundingBox() {
            given(warehouseBinRepository.findWarehouseMapRows(CENTER_1_ID)).willReturn(List.of(
                    storageRow(5L, "B-01", "B", 600, true, 300L, 1L, 1L, null, 9, 1, 7, 2),
                    storageRow(6L, "B-02", "B", 600, true, 300L, 1L, 1L, null, 9, 4, 7, 2),
                    storageRow(7L, "B-03", "B", 300, true, 0L, 0L, 0L, null, 9, 7, 7, 2)));

            WarehouseMapZoneDto zoneB = warehouseMapService
                    .getFloorPlan(CENTER_1_ID, TODAY).getZones().get(0);

            assertThat(zoneB.getPosX()).isEqualTo(9);
            assertThat(zoneB.getPosY()).isEqualTo(1);
            assertThat(zoneB.getPosWidth())
                    .as("9열부터 15열까지 7칸")
                    .isEqualTo(7);
            assertThat(zoneB.getPosHeight())
                    .as("1행부터 8행까지 8칸 (사이 통로 포함)")
                    .isEqualTo(8);
            assertThat(zoneB.getGridArea()).isEqualTo("1 / 9 / span 8 / span 7");
        }

        @Test
        @DisplayName("저온 구역과 상온 구역의 경계선 색을 구분한다")
        void distinguishesColdZone() {
            WarehouseMapZoneDto cold = WarehouseMapZoneDto.of("COLD", List.of(
                    WarehouseBinMapDto.of(
                            storageRow(35L, "COLD-01", "COLD", 300, true, 160L, 1L, 1L, null,
                                    21, 1, 3, 3), TODAY)));
            WarehouseMapZoneDto normal = WarehouseMapZoneDto.of("N", List.of(
                    WarehouseBinMapDto.of(
                            storageRow(8L, "N-01", "N", 200, true, 90L, 1L, 1L, null,
                                    9, 1, 5, 3), TODAY)));

            assertThat(cold.isCold()).isTrue();
            assertThat(cold.getAreaClass()).isEqualTo("ff-zone-area-cold");
            assertThat(cold.getKindLabel()).isEqualTo("저온");

            assertThat(normal.isCold())
                    .as("저온 구역이 아니면 상온으로 표시해 적재 가능 여부를 구분한다")
                    .isFalse();
            assertThat(normal.getAreaClass()).isEqualTo("ff-zone-area-normal");
            assertThat(normal.getKindLabel()).isEqualTo("상온");
        }

        @Test
        @DisplayName("구역 적재율은 사용 중인 보관 구역만 집계한다")
        void zoneUsageRate_excludesInactiveAndNonStorage() {
            given(warehouseBinRepository.findWarehouseMapRows(CENTER_1_ID)).willReturn(List.of(
                    storageRow(1L, "A-01", "A", 500, true, 250L, 1L, 1L, null, 6, 1, 2, 6),
                    storageRow(9L, "A-03", "A", 500, false, 0L, 0L, 0L, null, 6, 13, 2, 6)));

            WarehouseMapZoneDto zoneA = warehouseMapService
                    .getFloorPlan(CENTER_1_ID, TODAY).getZones().get(0);

            assertThat(zoneA.getBinCount())
                    .as("도면에는 사용 중지 구역도 그려지므로 칸 수에는 포함된다")
                    .isEqualTo(2);
            assertThat(zoneA.getTotalCapacity())
                    .as("사용 중지 구역 500 은 수용량에서 제외한다")
                    .isEqualTo(500);
            assertThat(zoneA.getUsageRate()).isEqualTo(50);
            assertThat(zoneA.getBarClass()).isEqualTo("bg-success");
        }

        @Test
        @DisplayName("구역 요약 진행바 색은 적재율 구간을 따른다")
        void barClassByUsageRate() {
            given(warehouseBinRepository.findWarehouseMapRows(CENTER_2_ID)).willReturn(List.of(
                    storageRow(8L, "COLD-01", "COLD", 200, true, 190L, 1L, 1L, null, 9, 1, 7, 2)));

            WarehouseMapZoneDto cold = warehouseMapService
                    .getFloorPlan(CENTER_2_ID, TODAY).getZones().get(0);

            assertThat(cold.getUsageRate()).isEqualTo(95);
            assertThat(cold.getBarClass()).isEqualTo("bg-danger");
        }
    }

    /* ==================================================================
     * 창고 전체 요약
     * ================================================================== */

    @Nested
    @DisplayName("창고 전체 요약")
    class Summary {

        @Test
        @DisplayName("사용 중지 구역과 입고/출고 대기 구역은 수용량 통계에서 제외한다")
        void excludesInactiveAndNonStorage() {
            given(warehouseBinRepository.findWarehouseMapRows(CENTER_1_ID)).willReturn(List.of(
                    storageRow(1L, "A-01", "A", 500, true, 240L, 2L, 2L, null, 6, 1, 2, 6),
                    storageRow(8L, "C-01", "C", 200, true, 190L, 2L, 2L, null, 9, 13, 7, 2),
                    row(10L, "R-01", "R", BinPurpose.RECEIVING, 300, true, 250L, 1L, 1L, null, 1, 4, 4, 3),
                    storageRow(9L, "A-03", "A", 400, false, 0L, 0L, 0L, null, 6, 13, 2, 6)));

            WarehouseMapSummaryDto summary =
                    warehouseMapService.getFloorPlan(CENTER_1_ID, TODAY).getSummary();

            assertThat(summary.getTotalBins()).isEqualTo(4);
            assertThat(summary.getActiveBins()).isEqualTo(3);
            assertThat(summary.getInactiveBins()).isEqualTo(1);
            assertThat(summary.getStorageBins())
                    .as("입고 대기 구역은 보관 구역으로 세지 않는다")
                    .isEqualTo(2);
            assertThat(summary.getTotalCapacity())
                    .as("500 + 200 (사용 중지 400, 입고 대기 300 제외)")
                    .isEqualTo(700);
            assertThat(summary.getTotalLoaded()).isEqualTo(430);
            assertThat(summary.getUsageRate())
                    .as("430 / 700 = 61.4% → 61")
                    .isEqualTo(61);
            assertThat(summary.getRemainingCapacity()).isEqualTo(270);
        }

        @Test
        @DisplayName("대기 구역 재고는 적재율에서 빼지만 별도 항목으로 집계해 보여준다")
        void waitingStockIsExcludedButReported() {
            // given : 입고 대기 구역에 250 포대가 실제로 쌓여 있는 상태
            given(warehouseBinRepository.findWarehouseMapRows(CENTER_1_ID)).willReturn(List.of(
                    storageRow(1L, "A-01", "A", 500, true, 240L, 1L, 1L, null, 6, 1, 2, 4),
                    row(10L, "R-01", "R", BinPurpose.RECEIVING, 300, true,
                            250L, 1L, 1L, null, 1, 3, 4, 3),
                    row(23L, "S-01", "S", BinPurpose.SHIPPING, 400, true,
                            0L, 0L, 0L, null, 1, 6, 4, 2)));

            WarehouseMapSummaryDto summary =
                    warehouseMapService.getFloorPlan(CENTER_1_ID, TODAY).getSummary();

            // 적재율은 보관 구역만
            assertThat(summary.getTotalCapacity()).isEqualTo(500);
            assertThat(summary.getTotalLoaded()).isEqualTo(240);
            assertThat(summary.getUsageRate()).isEqualTo(48);

            // 대기 구역은 별도 집계
            assertThat(summary.getWaitingBins()).isEqualTo(2);
            assertThat(summary.getWaitingCapacity())
                    .as("입고 대기 300 + 출고 대기 400")
                    .isEqualTo(700);
            assertThat(summary.getWaitingLoaded())
                    .as("대기 구역 재고가 요약에서 사라지면 창고 실물을 알 수 없다")
                    .isEqualTo(250);
            assertThat(summary.isHasWaitingStock()).isTrue();

            // 창고 실물 총량은 보관 + 대기
            assertThat(summary.getTotalLoadedIncludingWaiting())
                    .as("240 + 250 = 490")
                    .isEqualTo(490);
        }

        @Test
        @DisplayName("사용 중지 구역의 수용량도 별도로 집계한다")
        void reportsInactiveCapacity() {
            given(warehouseBinRepository.findWarehouseMapRows(CENTER_1_ID)).willReturn(List.of(
                    storageRow(1L, "A-01", "A", 500, true, 240L, 1L, 1L, null, 6, 1, 2, 4),
                    storageRow(9L, "A-03", "A", 400, false, 0L, 0L, 0L, null, 6, 10, 2, 5)));

            WarehouseMapSummaryDto summary =
                    warehouseMapService.getFloorPlan(CENTER_1_ID, TODAY).getSummary();

            assertThat(summary.getInactiveCapacity())
                    .as("보수 중이라 쓸 수 없는 공간을 구분해 보여준다")
                    .isEqualTo(400);
            assertThat(summary.getTotalCapacity()).isEqualTo(500);
        }

        @Test
        @DisplayName("대기 구역이 비어 있으면 별도 표기를 하지 않는다")
        void noWaitingStock_notReported() {
            given(warehouseBinRepository.findWarehouseMapRows(CENTER_2_ID)).willReturn(List.of(
                    storageRow(8L, "N-01", "N", 200, true, 90L, 1L, 1L, null, 9, 1, 5, 3),
                    row(22L, "R-11", "R", BinPurpose.RECEIVING, 300, true,
                            0L, 0L, 0L, null, 1, 3, 4, 3)));

            WarehouseMapSummaryDto summary =
                    warehouseMapService.getFloorPlan(CENTER_2_ID, TODAY).getSummary();

            assertThat(summary.getWaitingLoaded()).isZero();
            assertThat(summary.isHasWaitingStock()).isFalse();
            assertThat(summary.getWaitingCapacity()).isEqualTo(300);
        }

        @Test
        @DisplayName("포화 / 빈 구역 / 유통기한 임박 구역 개수를 센다")
        void countsBinsByStatus() {
            given(warehouseBinRepository.findWarehouseMapRows(CENTER_1_ID)).willReturn(List.of(
                    storageRow(1L, "A-01", "A", 200, true, 190L, 1L, 1L, TODAY.plusDays(5), 6, 1, 2, 6),
                    storageRow(2L, "A-02", "A", 200, true, 180L, 1L, 1L, null, 6, 7, 2, 6),
                    storageRow(3L, "C-01", "C", 400, true, 0L, 0L, 0L, null, 9, 13, 7, 2),
                    storageRow(4L, "C-02", "C", 400, true, 100L, 1L, 1L, TODAY.plusDays(10), 9, 16, 7, 2),
                    storageRow(9L, "A-03", "A", 400, false, 0L, 0L, 0L, null, 6, 13, 2, 6)));

            WarehouseMapSummaryDto summary =
                    warehouseMapService.getFloorPlan(CENTER_1_ID, TODAY).getSummary();

            assertThat(summary.getFullBins()).isEqualTo(2);
            assertThat(summary.getEmptyBins())
                    .as("사용 중지 구역은 빈 구역으로 세지 않는다")
                    .isEqualTo(1);
            assertThat(summary.getExpiringBins()).isEqualTo(2);
        }

        @Test
        @DisplayName("구역이 하나도 없으면 0으로 나누지 않고 사용률 0% 를 반환한다")
        void noBins_zeroUsageRate() {
            WarehouseMapSummaryDto summary = WarehouseMapSummaryDto.of(List.of());

            assertThat(summary.getTotalBins()).isZero();
            assertThat(summary.getTotalCapacity()).isZero();
            assertThat(summary.getUsageRate()).isZero();
        }
    }

    /* ==================================================================
     * 구역 상세
     * ================================================================== */

    @Nested
    @DisplayName("구역 상세 조회")
    class BinDetail {

        @Test
        @DisplayName("구역 요약과 보관 중인 재고 목록을 함께 반환한다")
        void returnsBinSummaryWithInventories() {
            given(warehouseBinRepository.findWarehouseMapRowByBinId(8L)).willReturn(Optional.of(
                    storageRow(8L, "COLD-01", "COLD", 200, true, 190L, 2L, 2L,
                            LocalDate.of(2026, 8, 5), 9, 1, 7, 2)));
            given(inventoryRepository.search(null, null, 8L, null)).willReturn(List.of(
                    inventory(1L, "FD-CT-001", "육성우 사료", "LOT-CT-2601",
                            LocalDate.of(2026, 8, 5), 90),
                    inventory(2L, "SP-CT-001", "한우 영양제", "LOT-SP-2651",
                            LocalDate.of(2027, 7, 8), 100)));

            BinDetailDto detail = warehouseMapService.getBinDetail(8L, TODAY);

            assertThat(detail.getBin().getBinCode()).isEqualTo("COLD-01");
            assertThat(detail.getBin().getUsageRate()).isEqualTo(95);
            assertThat(detail.getBin().getStatus()).isEqualTo(BinLoadStatus.FULL);
            assertThat(detail.getBin().getLocationLabel())
                    .as("도면은 센터 탭 안에서 보므로 센터명을 반복하지 않는다")
                    .isEqualTo("COLD구역 · 01랙 · 1단");

            assertThat(detail.getInventoryCount()).isEqualTo(2);
            assertThat(detail.getInventories())
                    .extracting("productCode", "lotNo", "quantity")
                    .containsExactly(
                            tuple("FD-CT-001", "LOT-CT-2601", 90),
                            tuple("SP-CT-001", "LOT-SP-2651", 100));
            assertThat(detail.isHasExpired()).isFalse();
        }

        @Test
        @DisplayName("만료된 로트가 있으면 hasExpired 가 true 가 된다")
        void detectsExpiredLot() {
            given(warehouseBinRepository.findWarehouseMapRowByBinId(3L)).willReturn(Optional.of(
                    storageRow(3L, "C-01", "C", 400, true, 20L, 1L, 1L,
                            LocalDate.of(2026, 7, 23), 9, 13, 7, 2)));
            given(inventoryRepository.search(null, null, 3L, null)).willReturn(List.of(
                    inventory(1L, "FD-PL-001", "산란계 사료", "LOT-PL-2620",
                            LocalDate.of(2026, 7, 23), 20)));

            BinDetailDto detail = warehouseMapService.getBinDetail(3L, TODAY);

            assertThat(detail.isHasExpired()).isTrue();
            assertThat(detail.getInventories().get(0).getRemainingDays()).isEqualTo(-5);
        }

        @Test
        @DisplayName("재고가 없는 구역은 빈 목록을 반환한다")
        void emptyBin_returnsNoInventories() {
            given(warehouseBinRepository.findWarehouseMapRowByBinId(3L)).willReturn(Optional.of(
                    storageRow(3L, "C-01", "C", 400, true, 0L, 0L, 0L, null, 9, 13, 7, 2)));
            given(inventoryRepository.search(null, null, 3L, null)).willReturn(List.of());

            BinDetailDto detail = warehouseMapService.getBinDetail(3L, TODAY);

            assertThat(detail.getInventories()).isEmpty();
            assertThat(detail.getBin().isEmpty()).isTrue();
            assertThat(detail.getBin().getEarliestDDayLabel()).isEqualTo("-");
            assertThat(detail.isHasExpired()).isFalse();
        }

        @Test
        @DisplayName("존재하지 않는 구역을 조회하면 ResourceNotFoundException 이 발생한다")
        void notFound_throwsException() {
            given(warehouseBinRepository.findWarehouseMapRowByBinId(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> warehouseMapService.getBinDetail(999L, TODAY))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("존재하지 않는 창고 구역");
        }
    }

    /* ==================================================================
     * 픽스처
     * ================================================================== */

    /** 적재 상태 판정만 확인할 때 쓰는 간단한 보관 구역 */
    private WarehouseBinMapDto bin(int capacity, Long loaded, LocalDate earliestExpiration) {
        return WarehouseBinMapDto.of(
                storageRow(1L, "A-01", "A", capacity, true, loaded, 1L, 1L,
                        earliestExpiration, 6, 1, 2, 6),
                TODAY);
    }

    private WarehouseMapRow storageRow(Long binId, String binCode, String zone,
                                       Integer maxCapacity, boolean active,
                                       Long loaded, Long lotCount, Long productCount,
                                       LocalDate earliestExpiration,
                                       int posX, int posY, int posWidth, int posHeight) {
        return row(binId, binCode, zone, BinPurpose.STORAGE, maxCapacity, active,
                loaded, lotCount, productCount, earliestExpiration, posX, posY, posWidth, posHeight);
    }

    private WarehouseMapRow row(Long binId, String binCode, String zone, BinPurpose purpose,
                                Integer maxCapacity, boolean active,
                                Long loaded, Long lotCount, Long productCount,
                                LocalDate earliestExpiration,
                                int posX, int posY, int posWidth, int posHeight) {
        // 도면은 센터 단위로 그리므로 집계 행에는 센터 정보가 담기지 않는다
        return new WarehouseMapRow(binId, binCode, zone, purpose, "01", 1,
                maxCapacity, active, posX, posY, posWidth, posHeight,
                loaded, lotCount, productCount, earliestExpiration);
    }

    private Inventory inventory(Long inventoryId, String productCode, String productName,
                                String lotNo, LocalDate expirationDate, int quantity) {
        Product product = Product.builder()
                .productId(1L)
                .productCode(productCode)
                .name(productName)
                .animalType(AnimalType.CATTLE)
                .productType(ProductType.FEED)
                .weightKg(25)
                .price(30000L)
                .totalStock(quantity)
                .safetyStock(10)
                .shelfLifeDays(180)
                .active(true)
                .build();

        ProductLot lot = ProductLot.builder()
                .lotId(inventoryId)
                .product(product)
                .lotNo(lotNo)
                .manufacturedDate(expirationDate.minusDays(180))
                .expirationDate(expirationDate)
                .lotQuantity(quantity)
                .build();

        WarehouseBin warehouseBin = WarehouseBin.builder()
                .binId(8L)
                .binCode("COLD-01")
                .center(Center.builder()
                        .centerId(CENTER_2_ID).centerCode("WH2").name("제2창고")
                        .region("수도권").active(true).build())
                .zone("COLD")
                .binPurpose(BinPurpose.STORAGE)
                .rack("01")
                .binLevel(1)
                .maxCapacity(200)
                .posX(9)
                .posY(1)
                .posWidth(7)
                .posHeight(2)
                .active(true)
                .build();

        return Inventory.builder()
                .inventoryId(inventoryId)
                .lot(lot)
                .bin(warehouseBin)
                .quantity(quantity)
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
