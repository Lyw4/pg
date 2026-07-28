package com.feedflow.admin.service;

import com.feedflow.admin.dto.BinDetailDto;
import com.feedflow.admin.dto.WarehouseBinMapDto;
import com.feedflow.admin.dto.WarehouseMapLevelDto;
import com.feedflow.admin.dto.WarehouseMapRow;
import com.feedflow.admin.dto.WarehouseMapSummaryDto;
import com.feedflow.admin.dto.WarehouseMapZoneDto;
import com.feedflow.common.exception.ResourceNotFoundException;
import com.feedflow.domain.AnimalType;
import com.feedflow.domain.BinLoadStatus;
import com.feedflow.domain.Inventory;
import com.feedflow.domain.Product;
import com.feedflow.domain.ProductLot;
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
 * 창고 2D 도면 맵 서비스 단위 테스트.
 *
 * <h3>검증 포인트</h3>
 * <ul>
 *     <li>수용량 대비 적재율 계산 (반올림 / 0 나눗셈 / 초과 적재)</li>
 *     <li>적재율에 따른 상태(색상) 분류 경계값</li>
 *     <li>구역 그룹(Zone) 별 묶음과 도면 좌표(랙 × 단) 계산</li>
 *     <li>요약 집계에서 사용 중지 구역을 제외하는지</li>
 *     <li>구역 상세 조회 (보관 재고 목록 / 존재하지 않는 구역)</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WarehouseMapService 단위 테스트")
class WarehouseMapServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 28);

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
                    row(1L, "A-01-01", "A", "01", 1, 500, true, 600L, 2L, 2L, null), TODAY);

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
                "240, 500, SPARE",
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
            WarehouseBinMapDto urgent = WarehouseBinMapDto.of(
                    row(1L, "A-01-01", "A", "01", 1, 500, true, 100L, 1L, 1L,
                            TODAY.plusDays(5)), TODAY);
            WarehouseBinMapDto boundary = WarehouseBinMapDto.of(
                    row(2L, "A-01-02", "A", "01", 2, 500, true, 100L, 1L, 1L,
                            TODAY.plusDays(30)), TODAY);
            WarehouseBinMapDto safe = WarehouseBinMapDto.of(
                    row(3L, "A-02-01", "A", "02", 1, 500, true, 100L, 1L, 1L,
                            TODAY.plusDays(31)), TODAY);
            WarehouseBinMapDto expired = WarehouseBinMapDto.of(
                    row(4L, "A-02-02", "A", "02", 2, 500, true, 100L, 1L, 1L,
                            TODAY.minusDays(3)), TODAY);
            WarehouseBinMapDto emptyBin = WarehouseBinMapDto.of(
                    row(5L, "A-03-01", "A", "03", 1, 500, true, 0L, 0L, 0L, null), TODAY);

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
            assertThat(emptyBin.isExpiringSoon())
                    .as("재고가 없으면 임박 표시도 없다")
                    .isFalse();
        }

        @Test
        @DisplayName("사용 중지된 구역은 적재 상태색이 아니라 사용 중지로 표시한다")
        void inactiveBin_showsInactiveStyle() {
            WarehouseBinMapDto bin = WarehouseBinMapDto.of(
                    row(9L, "A-03-01", "A", "03", 1, 400, false, 0L, 0L, 0L, null), TODAY);

            assertThat(bin.getStatus())
                    .as("계산된 상태 자체는 EMPTY 이지만")
                    .isEqualTo(BinLoadStatus.EMPTY);
            assertThat(bin.getTileClass()).isEqualTo("ff-bin-inactive");
            assertThat(bin.getStatusLabel()).isEqualTo("사용 중지");
        }
    }

    /* ==================================================================
     * 구역 그룹 / 도면 좌표
     * ================================================================== */

    @Nested
    @DisplayName("구역 그룹과 도면 좌표")
    class ZoneGrouping {

        @Test
        @DisplayName("구역(Zone) 별로 묶고 단(Level)을 높은 층부터 한 줄씩 쌓는다")
        void groupsByZoneAndLevel() {
            // given : A구역 2랙 × 2단 + B구역 1랙 1단
            given(warehouseBinRepository.findWarehouseMapRows(null)).willReturn(List.of(
                    row(1L, "A-01-01", "A", "01", 1, 500, true, 240L, 2L, 2L, null),
                    row(2L, "A-01-02", "A", "01", 2, 500, true, 170L, 1L, 1L, null),
                    row(3L, "A-02-01", "A", "02", 1, 400, true, 210L, 2L, 2L, null),
                    row(4L, "A-02-02", "A", "02", 2, 400, true, 220L, 1L, 1L, null),
                    row(5L, "B-01-01", "B", "01", 1, 600, true, 360L, 2L, 1L, null)));

            // when
            List<WarehouseMapZoneDto> zones = warehouseMapService.getZones(null, TODAY);

            // then
            assertThat(zones).hasSize(2);

            WarehouseMapZoneDto zoneA = zones.get(0);
            assertThat(zoneA.getZone()).isEqualTo("A");
            assertThat(zoneA.getBinCount()).isEqualTo(4);
            assertThat(zoneA.getLevelCount()).isEqualTo(2);

            assertThat(zoneA.getLevels())
                    .as("높은 단이 위로 오도록 내림차순이어야 한다")
                    .extracting(WarehouseMapLevelDto::getLevel)
                    .containsExactly(2, 1);

            // 각 줄 안에서는 랙 번호 순으로 왼쪽부터 배치된다
            assertThat(zoneA.getLevels().get(0).getBins())
                    .extracting(WarehouseBinMapDto::getBinCode)
                    .containsExactly("A-01-02", "A-02-02");
            assertThat(zoneA.getLevels().get(1).getBins())
                    .extracting(WarehouseBinMapDto::getBinCode)
                    .containsExactly("A-01-01", "A-02-01");

            WarehouseMapZoneDto zoneB = zones.get(1);
            assertThat(zoneB.getZone()).isEqualTo("B");
            assertThat(zoneB.getBinCount()).isEqualTo(1);
            assertThat(zoneB.getLevelCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("칸이 적은 줄에는 채움 칸을 두어 수용량이 같으면 어느 줄이든 같은 너비가 되게 한다")
        void fillerNormalizesWidthAcrossLevels() {
            // given : 2단은 500 하나뿐, 1단은 500 + 400 + 400 = 1300
            given(warehouseBinRepository.findWarehouseMapRows(null)).willReturn(List.of(
                    row(2L, "A-01-02", "A", "01", 2, 500, true, 170L, 2L, 2L, null),
                    row(1L, "A-01-01", "A", "01", 1, 500, true, 240L, 2L, 2L, null),
                    row(3L, "A-02-01", "A", "02", 1, 400, true, 210L, 3L, 2L, null),
                    row(9L, "A-03-01", "A", "03", 1, 400, false, 0L, 0L, 0L, null)));

            // when
            WarehouseMapZoneDto zone = warehouseMapService.getZones(null, TODAY).get(0);

            // then
            WarehouseMapLevelDto level2 = zone.getLevels().get(0);
            WarehouseMapLevelDto level1 = zone.getLevels().get(1);

            assertThat(level2.getFillerGrow())
                    .as("2단은 500 만 있으므로 기준 1300 까지 800 을 채워야 한다")
                    .isEqualTo(800);
            assertThat(level1.getFillerGrow())
                    .as("가장 넓은 줄은 채울 필요가 없다")
                    .isZero();
            assertThat(level1.isHasFiller()).isFalse();
            assertThat(level2.isHasFiller()).isTrue();

            // 채움 칸을 포함한 총 flex-grow 가 두 줄 모두 같아야 너비 기준이 통일된다
            int total2 = level2.getBins().stream().mapToInt(WarehouseBinMapDto::getFlexGrow).sum()
                    + level2.getFillerGrow();
            int total1 = level1.getBins().stream().mapToInt(WarehouseBinMapDto::getFlexGrow).sum()
                    + level1.getFillerGrow();

            assertThat(total2)
                    .as("A-01-02(500) 와 A-01-01(500) 이 같은 너비로 보이려면 줄 총합이 같아야 한다")
                    .isEqualTo(total1)
                    .isEqualTo(1300);
        }

        @Test
        @DisplayName("너비 기준은 구역이 달라도 도면 전체에서 하나로 통일한다")
        void referenceCapacityIsFloorWide() {
            // given : A구역 1단 1300, COLD구역 1단 200
            given(warehouseBinRepository.findWarehouseMapRows(null)).willReturn(List.of(
                    row(1L, "A-01-01", "A", "01", 1, 500, true, 240L, 1L, 1L, null),
                    row(3L, "A-02-01", "A", "02", 1, 400, true, 210L, 1L, 1L, null),
                    row(9L, "A-03-01", "A", "03", 1, 400, true, 0L, 0L, 0L, null),
                    row(8L, "COLD-01", "COLD", "01", 1, 200, true, 190L, 3L, 2L, null)));

            // when
            List<WarehouseMapZoneDto> zones = warehouseMapService.getZones(null, TODAY);

            // then
            WarehouseMapLevelDto coldLevel = zones.get(1).getLevels().get(0);
            assertThat(coldLevel.getFillerGrow())
                    .as("COLD 구역도 A구역과 같은 기준(1300)을 쓰므로 200 을 뺀 1100 을 채운다")
                    .isEqualTo(1100);
        }

        @Test
        @DisplayName("사용 중지 구역도 도면에서 자리를 차지하므로 너비 기준에 포함한다")
        void inactiveBinOccupiesWidth() {
            given(warehouseBinRepository.findWarehouseMapRows(null)).willReturn(List.of(
                    row(1L, "A-01-01", "A", "01", 1, 500, true, 240L, 1L, 1L, null),
                    row(9L, "A-03-01", "A", "03", 1, 400, false, 0L, 0L, 0L, null)));

            WarehouseMapZoneDto zone = warehouseMapService.getZones(null, TODAY).get(0);

            assertThat(zone.getLevels().get(0).getFillerGrow())
                    .as("500 + 400 = 900 이 그대로 기준이므로 채움 칸은 없다")
                    .isZero();
            assertThat(zone.getTotalCapacity())
                    .as("다만 수용량 통계에서는 사용 중지 400 을 제외한다")
                    .isEqualTo(500);
            assertThat(zone.getInactiveBinCount()).isEqualTo(1);
            assertThat(zone.getInactiveCapacity()).isEqualTo(400);
        }

        @Test
        @DisplayName("칸의 상대 너비는 최대 수용량에 비례한다")
        void tileWidthProportionalToCapacity() {
            given(warehouseBinRepository.findWarehouseMapRows(null)).willReturn(List.of(
                    row(5L, "B-01-01", "B", "01", 1, 600, true, 360L, 2L, 1L, null),
                    row(7L, "B-02-01", "B", "02", 1, 300, true, 160L, 1L, 1L, null)));

            List<WarehouseBinMapDto> bins =
                    warehouseMapService.flattenBins(warehouseMapService.getZones(null, TODAY));

            assertThat(bins)
                    .extracting(WarehouseBinMapDto::getBinCode, WarehouseBinMapDto::getFlexGrow)
                    .containsExactly(
                            tuple("B-01-01", 600),
                            tuple("B-02-01", 300));
        }

        @Test
        @DisplayName("수용량이 0 인 구역도 도면에서 사라지지 않도록 최소 너비를 갖는다")
        void zeroCapacity_hasMinimumWidth() {
            given(warehouseBinRepository.findWarehouseMapRows(null)).willReturn(List.of(
                    row(1L, "A-01-01", "A", "01", 1, 0, true, 0L, 0L, 0L, null)));

            WarehouseBinMapDto bin =
                    warehouseMapService.getZones(null, TODAY).get(0).getBins().get(0);

            assertThat(bin.getFlexGrow())
                    .as("flex-grow 가 0 이면 칸이 아예 보이지 않는다")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("구역 그룹별 적재율도 함께 계산한다")
        void calculatesZoneUsageRate() {
            given(warehouseBinRepository.findWarehouseMapRows("A")).willReturn(List.of(
                    row(1L, "A-01-01", "A", "01", 1, 500, true, 250L, 1L, 1L, null),
                    row(2L, "A-01-02", "A", "01", 2, 500, true, 150L, 1L, 1L, null)));

            List<WarehouseMapZoneDto> zones = warehouseMapService.getZones("A", TODAY);

            assertThat(zones).hasSize(1);
            assertThat(zones.get(0).getTotalCapacity()).isEqualTo(1000);
            assertThat(zones.get(0).getTotalLoaded()).isEqualTo(400);
            assertThat(zones.get(0).getUsageRate()).isEqualTo(40);
        }

        @Test
        @DisplayName("구역 그룹 적재율 계산에서도 사용 중지 구역의 수용량은 제외한다")
        void zoneUsageRate_excludesInactiveBin() {
            given(warehouseBinRepository.findWarehouseMapRows("A")).willReturn(List.of(
                    row(1L, "A-01-01", "A", "01", 1, 500, true, 250L, 1L, 1L, null),
                    row(9L, "A-03-01", "A", "03", 1, 500, false, 0L, 0L, 0L, null)));

            WarehouseMapZoneDto zone = warehouseMapService.getZones("A", TODAY).get(0);

            assertThat(zone.getTotalCapacity())
                    .as("사용 중지 구역 500 은 수용량에 포함하지 않는다")
                    .isEqualTo(500);
            assertThat(zone.getUsageRate()).isEqualTo(50);
        }

        @Test
        @DisplayName("랙이나 단 정보가 없는 구역은 1단으로 취급해 도면에 그려진다")
        void binWithoutRackOrLevel_stillPlaced() {
            given(warehouseBinRepository.findWarehouseMapRows(null)).willReturn(List.of(
                    row(8L, "COLD-01", "COLD", null, null, 200, true, 190L, 2L, 2L, null)));

            WarehouseMapZoneDto zone = warehouseMapService.getZones(null, TODAY).get(0);

            assertThat(zone.getLevelCount()).isEqualTo(1);
            assertThat(zone.getLevels().get(0).getLevel()).isEqualTo(1);
            assertThat(zone.getBins()).hasSize(1);
            assertThat(zone.getBins().get(0).getBinCode()).isEqualTo("COLD-01");
        }

        @Test
        @DisplayName("랙 번호가 없는 구역은 같은 단에서 맨 오른쪽으로 배치한다")
        void binWithoutRack_sortedLast() {
            given(warehouseBinRepository.findWarehouseMapRows(null)).willReturn(List.of(
                    row(1L, "A-99-01", "A", null, 1, 300, true, 0L, 0L, 0L, null),
                    row(2L, "A-01-01", "A", "01", 1, 500, true, 100L, 1L, 1L, null)));

            WarehouseMapZoneDto zone = warehouseMapService.getZones(null, TODAY).get(0);

            assertThat(zone.getLevels().get(0).getBins())
                    .extracting(WarehouseBinMapDto::getBinCode)
                    .containsExactly("A-01-01", "A-99-01");
        }

        @Test
        @DisplayName("조회 결과가 없으면 빈 목록을 반환한다")
        void noBins_returnsEmptyList() {
            given(warehouseBinRepository.findWarehouseMapRows(null)).willReturn(List.of());

            assertThat(warehouseMapService.getZones(null, TODAY)).isEmpty();
        }

        @Test
        @DisplayName("공백만 입력된 구역 필터는 null 로 변환되어 전체 조회가 된다")
        void blankZoneFilter_treatedAsNull() {
            given(warehouseBinRepository.findWarehouseMapRows(null)).willReturn(List.of());

            warehouseMapService.getZones("   ", TODAY);

            verify(warehouseBinRepository).findWarehouseMapRows(null);
        }
    }

    /* ==================================================================
     * 요약 집계
     * ================================================================== */

    @Nested
    @DisplayName("창고 전체 요약")
    class Summary {

        @Test
        @DisplayName("사용 중지 구역은 수용량/적재량 통계에서 제외하고 건수만 센다")
        void excludesInactiveBinsFromCapacity() {
            given(warehouseBinRepository.findWarehouseMapRows(null)).willReturn(List.of(
                    row(1L, "A-01-01", "A", "01", 1, 500, true, 240L, 2L, 2L, null),
                    row(8L, "COLD-01", "COLD", "01", 1, 200, true, 190L, 2L, 2L, null),
                    row(9L, "A-03-01", "A", "03", 1, 400, false, 0L, 0L, 0L, null)));

            List<WarehouseMapZoneDto> zones = warehouseMapService.getZones(null, TODAY);
            WarehouseMapSummaryDto summary = warehouseMapService.getSummary(zones);

            assertThat(summary.getTotalBins()).isEqualTo(3);
            assertThat(summary.getActiveBins()).isEqualTo(2);
            assertThat(summary.getInactiveBins()).isEqualTo(1);
            assertThat(summary.getTotalCapacity())
                    .as("사용 중지 구역 400 은 제외되어 500 + 200 = 700")
                    .isEqualTo(700);
            assertThat(summary.getTotalLoaded()).isEqualTo(430);
            assertThat(summary.getUsageRate())
                    .as("430 / 700 = 61.4% → 61")
                    .isEqualTo(61);
            assertThat(summary.getRemainingCapacity()).isEqualTo(270);
        }

        @Test
        @DisplayName("포화 구역과 빈 구역 개수를 센다")
        void countsFullAndEmptyBins() {
            given(warehouseBinRepository.findWarehouseMapRows(null)).willReturn(List.of(
                    row(1L, "A-01-01", "A", "01", 1, 200, true, 190L, 1L, 1L, null),  // 95% FULL
                    row(2L, "A-01-02", "A", "01", 2, 200, true, 180L, 1L, 1L, null),  // 90% FULL
                    row(3L, "A-02-01", "A", "02", 1, 400, true, 0L, 0L, 0L, null),    // EMPTY
                    row(4L, "A-02-02", "A", "02", 2, 400, true, 100L, 1L, 1L, null),  // 25% SPARE
                    row(9L, "A-03-01", "A", "03", 1, 400, false, 0L, 0L, 0L, null))); // 사용 중지

            WarehouseMapSummaryDto summary =
                    warehouseMapService.getSummary(warehouseMapService.getZones(null, TODAY));

            assertThat(summary.getFullBins()).isEqualTo(2);
            assertThat(summary.getEmptyBins())
                    .as("사용 중지 구역은 빈 구역으로 세지 않는다")
                    .isEqualTo(1);
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
            // given
            given(warehouseBinRepository.findWarehouseMapRowByBinId(8L)).willReturn(Optional.of(
                    row(8L, "COLD-01", "COLD", "01", 1, 200, true, 190L, 2L, 2L,
                            LocalDate.of(2026, 8, 5))));
            given(inventoryRepository.search(null, 8L, null)).willReturn(List.of(
                    inventory(1L, "FD-CT-001", "육성우 사료", "LOT-CT-2601",
                            LocalDate.of(2026, 8, 5), 90),
                    inventory(2L, "SP-CT-001", "한우 영양제", "LOT-SP-2651",
                            LocalDate.of(2027, 7, 8), 100)));

            // when
            BinDetailDto detail = warehouseMapService.getBinDetail(8L, TODAY);

            // then
            assertThat(detail.getBin().getBinCode()).isEqualTo("COLD-01");
            assertThat(detail.getBin().getUsageRate()).isEqualTo(95);
            assertThat(detail.getBin().getStatus()).isEqualTo(BinLoadStatus.FULL);
            assertThat(detail.getBin().getLocationLabel()).isEqualTo("COLD구역 · 01랙 · 1단");

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
                    row(3L, "A-02-01", "A", "02", 1, 400, true, 20L, 1L, 1L,
                            LocalDate.of(2026, 7, 23))));
            given(inventoryRepository.search(null, 3L, null)).willReturn(List.of(
                    inventory(1L, "FD-PL-001", "산란계 사료", "LOT-PL-2620",
                            LocalDate.of(2026, 7, 23), 20)));   // TODAY 보다 과거 → 만료

            BinDetailDto detail = warehouseMapService.getBinDetail(3L, TODAY);

            assertThat(detail.isHasExpired()).isTrue();
            assertThat(detail.getInventories().get(0).getRemainingDays()).isEqualTo(-5);
        }

        @Test
        @DisplayName("재고가 없는 구역은 빈 목록을 반환한다")
        void emptyBin_returnsNoInventories() {
            given(warehouseBinRepository.findWarehouseMapRowByBinId(3L)).willReturn(Optional.of(
                    row(3L, "A-02-01", "A", "02", 1, 400, true, 0L, 0L, 0L, null)));
            given(inventoryRepository.search(null, 3L, null)).willReturn(List.of());

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

    private WarehouseMapRow row(Long binId, String binCode, String zone, String rack,
                                Integer binLevel, Integer maxCapacity, boolean active,
                                Long loaded, Long lotCount, Long productCount,
                                LocalDate earliestExpiration) {
        return new WarehouseMapRow(binId, binCode, zone, rack, binLevel, maxCapacity, active,
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

        WarehouseBin bin = WarehouseBin.builder()
                .binId(8L)
                .binCode("COLD-01")
                .zone("COLD")
                .rack("01")
                .binLevel(1)
                .maxCapacity(200)
                .active(true)
                .build();

        return Inventory.builder()
                .inventoryId(inventoryId)
                .lot(lot)
                .bin(bin)
                .quantity(quantity)
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
