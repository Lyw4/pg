package com.feedflow.admin.service;

import com.feedflow.admin.dto.CenterActivityRow;
import com.feedflow.admin.dto.CenterAlertRow;
import com.feedflow.admin.dto.CenterAnimalMixRow;
import com.feedflow.admin.dto.CenterCapacityRow;
import com.feedflow.admin.dto.CenterNetworkDto;
import com.feedflow.admin.dto.CenterOverviewDto;
import com.feedflow.admin.dto.CenterStockChartDto;
import com.feedflow.admin.dto.CenterStockRow;
import com.feedflow.domain.AnimalType;
import com.feedflow.domain.Center;
import com.feedflow.domain.MovementType;
import com.feedflow.repository.CenterRepository;
import com.feedflow.repository.InventoryRepository;
import com.feedflow.repository.StockMovementRepository;
import com.feedflow.repository.WarehouseBinRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * 전국 물류망 대시보드 서비스 테스트 (Epic Phase 4a).
 *
 * <h3>핵심 검증</h3>
 * <ul>
 *     <li>여러 집계 결과가 <b>센터 단위로 올바르게 짝지어지는지</b></li>
 *     <li>집계에 없는 센터(재고 0)가 <b>목록에서 빠지지 않는지</b> — 빠지면 신설 센터가 화면에서 사라진다</li>
 *     <li>이관 합계가 <b>중복 계산되지 않는지</b> — 출고와 입고는 짝이다</li>
 *     <li>적재율이 2D 도면과 <b>같은 경계</b>로 분류되는지</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CenterDashboardService 단위 테스트")
class CenterDashboardServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 30);
    private static final int ACTIVITY_DAYS = 7;

    @Mock
    private CenterRepository centerRepository;
    @Mock
    private InventoryRepository inventoryRepository;
    @Mock
    private WarehouseBinRepository warehouseBinRepository;
    @Mock
    private StockMovementRepository stockMovementRepository;

    private CenterDashboardService service;

    @BeforeEach
    void setUp() {
        service = new CenterDashboardService(centerRepository, inventoryRepository,
                warehouseBinRepository, stockMovementRepository);
        // @Value 는 스프링 컨텍스트 없이는 주입되지 않는다
        ReflectionTestUtils.setField(service, "activityDays", ACTIVITY_DAYS);
    }

    /* ==================================================================
     * 집계 조립
     * ================================================================== */

    @Nested
    @DisplayName("센터별 집계 조립")
    class Assembly {

        @Test
        @DisplayName("재고 · 수용량 · 경보 · 실적 · 축종 구성을 센터 단위로 짝지어 내려준다")
        void joinsAggregatesByCenter() {
            givenCenters(center(1L, "C1-YS", "충남 예산 센터"), center(2L, "C5-NJ", "전남 나주 센터"));

            given(inventoryRepository.findStockByCenter(null)).willReturn(List.of(
                    new CenterStockRow(1L, "충남 예산 센터", 900L, 12L),
                    new CenterStockRow(2L, "전남 나주 센터", 470L, 6L)));
            given(warehouseBinRepository.findStorageCapacityByCenter()).willReturn(List.of(
                    new CenterCapacityRow(1L, 2000L, 9L),
                    new CenterCapacityRow(2L, 1000L, 5L)));
            given(inventoryRepository.findExpiringByCenter(any(), any())).willReturn(List.of(
                    new CenterAlertRow(1L, "충남 예산 센터", 3L, 1L, 120L)));
            given(stockMovementRepository.findActivityByCenter(any(), any())).willReturn(List.of(
                    new CenterActivityRow(1L, "충남 예산 센터", MovementType.INBOUND, 500L, 4L),
                    new CenterActivityRow(1L, "충남 예산 센터", MovementType.OUTBOUND, 80L, 2L),
                    new CenterActivityRow(2L, "전남 나주 센터", MovementType.INBOUND, 200L, 2L)));
            given(inventoryRepository.findAnimalMixByCenter()).willReturn(List.of(
                    new CenterAnimalMixRow(1L, AnimalType.PIG, 570L),
                    new CenterAnimalMixRow(1L, AnimalType.POULTRY, 330L),
                    new CenterAnimalMixRow(2L, AnimalType.POULTRY, 470L)));

            CenterNetworkDto net = service.getNetworkOverview(TODAY);

            assertThat(net.getCenters())
                    .extracting(CenterOverviewDto::getCenterName,
                            CenterOverviewDto::getQuantity,
                            CenterOverviewDto::getCapacity,
                            CenterOverviewDto::getExpiringCount,
                            CenterOverviewDto::getInboundQuantity)
                    .containsExactly(
                            tuple("충남 예산 센터", 900, 2000, 3, 500),
                            tuple("전남 나주 센터", 470, 1000, 0, 200));

            CenterOverviewDto ys = net.getCenters().get(0);
            assertThat(ys.getOutboundQuantity()).isEqualTo(80);
            assertThat(ys.getAnimalMix()).containsExactly(
                    java.util.Map.entry("돼지", 570),
                    java.util.Map.entry("조류", 330));
            assertThat(ys.getAnimalMixSummary()).isEqualTo("돼지 570 · 조류 330");
        }

        /**
         * 집계 쿼리는 <b>재고가 있는 센터만</b> 돌려준다(group by 결과).
         * 센터 목록을 기준으로 조립하지 않으면 재고 0 인 센터가 화면에서 사라져,
         * 신설 센터에 배분이 누락된 사실을 아무도 알아채지 못한다.
         */
        @Test
        @DisplayName("집계에 없는 센터도 0 으로 채워 목록에 남긴다")
        void keepsCentersWithoutStock() {
            givenCenters(center(1L, "C1-YS", "충남 예산 센터"), center(5L, "C5-NJ", "전남 나주 센터"));

            given(inventoryRepository.findStockByCenter(null)).willReturn(List.of(
                    new CenterStockRow(1L, "충남 예산 센터", 900L, 12L)));
            given(warehouseBinRepository.findStorageCapacityByCenter()).willReturn(List.of(
                    new CenterCapacityRow(1L, 2000L, 9L),
                    new CenterCapacityRow(5L, 1000L, 5L)));
            given(inventoryRepository.findExpiringByCenter(any(), any())).willReturn(List.of());
            given(stockMovementRepository.findActivityByCenter(any(), any())).willReturn(List.of());
            given(inventoryRepository.findAnimalMixByCenter()).willReturn(List.of());

            CenterNetworkDto net = service.getNetworkOverview(TODAY);

            assertThat(net.getCenterCount()).isEqualTo(2);

            CenterOverviewDto nj = net.getCenters().get(1);
            assertThat(nj.getCenterName()).isEqualTo("전남 나주 센터");
            assertThat(nj.getQuantity()).isZero();
            assertThat(nj.isEmpty()).isTrue();
            assertThat(nj.getUsageRate())
                    .as("재고가 없으면 적재율은 0 이다 (수용량이 있어도)")
                    .isZero();
            assertThat(nj.getInboundQuantity()).isZero();

            assertThat(net.isHasEmptyCenter()).isTrue();
            assertThat(net.getEmptyCenters())
                    .extracting(CenterOverviewDto::getCenterName)
                    .containsExactly("전남 나주 센터");
        }

        @Test
        @DisplayName("전국 비중은 센터 수량 합계를 분모로 계산한다")
        void calculatesNationwideShare() {
            givenCenters(center(1L, "C1", "가"), center(2L, "C2", "나"));
            given(inventoryRepository.findStockByCenter(null)).willReturn(List.of(
                    new CenterStockRow(1L, "가", 700L, 3L),
                    new CenterStockRow(2L, "나", 300L, 2L)));
            given(warehouseBinRepository.findStorageCapacityByCenter()).willReturn(List.of());
            given(inventoryRepository.findExpiringByCenter(any(), any())).willReturn(List.of());
            given(stockMovementRepository.findActivityByCenter(any(), any())).willReturn(List.of());
            given(inventoryRepository.findAnimalMixByCenter()).willReturn(List.of());

            CenterNetworkDto net = service.getNetworkOverview(TODAY);

            assertThat(net.getCenters())
                    .extracting(CenterOverviewDto::getSharePercent)
                    .containsExactly(70, 30);
            assertThat(net.getTotalQuantity()).isEqualTo(1000);
        }

        @Test
        @DisplayName("운영 중인 센터가 없으면 빈 현황을 돌려준다")
        void noCenters() {
            givenCenters();
            given(inventoryRepository.findStockByCenter(null)).willReturn(List.of());
            given(warehouseBinRepository.findStorageCapacityByCenter()).willReturn(List.of());
            given(inventoryRepository.findExpiringByCenter(any(), any())).willReturn(List.of());
            given(stockMovementRepository.findActivityByCenter(any(), any())).willReturn(List.of());
            given(inventoryRepository.findAnimalMixByCenter()).willReturn(List.of());

            CenterNetworkDto net = service.getNetworkOverview(TODAY);

            assertThat(net.isEmpty()).isTrue();
            assertThat(net.getUsageRate()).as("0 으로 나누지 않는다").isZero();
            assertThat(net.getBusiestCenter()).isNull();
            assertThat(net.getMostUrgentCenter()).isNull();
        }
    }

    /* ==================================================================
     * 전국 요약
     * ================================================================== */

    @Nested
    @DisplayName("전국 요약")
    class Summary {

        /**
         * 이관은 출고와 입고가 짝이다. 둘을 더하면 같은 물량이 두 번 잡혀
         * "전국에서 200포대가 움직였다" 가 400 으로 보인다.
         */
        @Test
        @DisplayName("이관 합계는 출고 기준으로만 세어 중복 계산하지 않는다")
        void transferCountedOnce() {
            givenCenters(center(1L, "C1", "출발"), center(2L, "C2", "도착"));
            given(inventoryRepository.findStockByCenter(null)).willReturn(List.of());
            given(warehouseBinRepository.findStorageCapacityByCenter()).willReturn(List.of());
            given(inventoryRepository.findExpiringByCenter(any(), any())).willReturn(List.of());
            given(inventoryRepository.findAnimalMixByCenter()).willReturn(List.of());
            given(stockMovementRepository.findActivityByCenter(any(), any())).willReturn(List.of(
                    new CenterActivityRow(1L, "출발", MovementType.TRANSFER_OUT, 60L, 1L),
                    new CenterActivityRow(2L, "도착", MovementType.TRANSFER_IN, 60L, 1L)));

            CenterNetworkDto net = service.getNetworkOverview(TODAY);

            assertThat(net.getTotalTransferred())
                    .as("출고 60 + 입고 60 = 120 이 아니라 60 이다")
                    .isEqualTo(60);
            assertThat(net.isHasTransfer()).isTrue();

            CenterOverviewDto from = net.getCenters().get(0);
            CenterOverviewDto to = net.getCenters().get(1);
            assertThat(from.getTransferNet())
                    .as("나간 센터는 순감")
                    .isEqualTo(-60);
            assertThat(to.getTransferNet())
                    .as("들어온 센터는 순증")
                    .isEqualTo(60);
        }

        @Test
        @DisplayName("먼저 봐야 할 센터(재고 최다 · 적재율 최고 · 임박 최다)를 골라준다")
        void picksCentersToWatch() {
            givenCenters(center(1L, "C1", "재고많음"), center(2L, "C2", "빡빡함"), center(3L, "C3", "급함"));
            given(inventoryRepository.findStockByCenter(null)).willReturn(List.of(
                    new CenterStockRow(1L, "재고많음", 1000L, 10L),
                    new CenterStockRow(2L, "빡빡함", 400L, 4L),
                    new CenterStockRow(3L, "급함", 200L, 3L)));
            given(warehouseBinRepository.findStorageCapacityByCenter()).willReturn(List.of(
                    new CenterCapacityRow(1L, 5000L, 10L),     // 20%
                    new CenterCapacityRow(2L, 450L, 2L),       // 89%
                    new CenterCapacityRow(3L, 1000L, 4L)));    // 20%
            given(inventoryRepository.findExpiringByCenter(any(), any())).willReturn(List.of(
                    new CenterAlertRow(1L, "재고많음", 1L, 0L, 10L),
                    new CenterAlertRow(3L, "급함", 5L, 2L, 90L)));
            given(stockMovementRepository.findActivityByCenter(any(), any())).willReturn(List.of());
            given(inventoryRepository.findAnimalMixByCenter()).willReturn(List.of());

            CenterNetworkDto net = service.getNetworkOverview(TODAY);

            assertThat(net.getBusiestCenter().getCenterName()).isEqualTo("재고많음");
            assertThat(net.getMostLoadedCenter().getCenterName()).isEqualTo("빡빡함");
            assertThat(net.getMostUrgentCenter().getCenterName()).isEqualTo("급함");
            assertThat(net.getTotalExpiringCount()).isEqualTo(6);
            assertThat(net.getTotalExpiredCount()).isEqualTo(2);
        }

        /**
         * 적재율 경계가 2D 도면과 달라지면 같은 센터가 어떤 화면에서는 '보통',
         * 다른 화면에서는 '포화' 로 보인다. BinLoadStatus 상수를 재사용하는지 고정한다.
         */
        @Test
        @DisplayName("적재율 분류는 2D 도면과 같은 경계(60 / 90)를 쓴다")
        void usesSameLoadThresholds() {
            assertThat(overview(59, 100).getUsageLabel()).isEqualTo("여유");
            assertThat(overview(60, 100).getUsageLabel()).isEqualTo("보통");
            assertThat(overview(89, 100).getUsageLabel()).isEqualTo("보통");
            assertThat(overview(90, 100).getUsageLabel()).isEqualTo("포화");
            assertThat(overview(0, 100).getUsageLabel()).isEqualTo("비어있음");

            assertThat(overview(95, 100).getUsageBarClass()).isEqualTo("bg-danger");
            assertThat(overview(30, 100).getUsageBarClass()).isEqualTo("bg-success");
        }

        @Test
        @DisplayName("적재율이 100% 를 넘어도 진행바는 100 에서 멈춘다")
        void cappedProgressBar() {
            CenterOverviewDto over = overview(120, 100);
            assertThat(over.getUsageRate()).isEqualTo(120);
            assertThat(over.getUsageRateCapped()).isEqualTo(100);
            assertThat(over.getRemainingCapacity())
                    .as("초과 적재 시 남은 여유는 음수가 아니라 0")
                    .isZero();
        }
    }

    /* ==================================================================
     * 분포 차트
     * ================================================================== */

    @Nested
    @DisplayName("센터별 재고 분포 차트")
    class StockChart {

        @Test
        @DisplayName("센터명과 수량을 순서대로 담고 전국 합계를 함께 내려준다")
        void buildsChartData() {
            given(inventoryRepository.findStockByCenter(null)).willReturn(List.of(
                    new CenterStockRow(1L, "충남 예산 센터", 900L, 12L),
                    new CenterStockRow(5L, "전남 나주 센터", 470L, 6L)));

            CenterStockChartDto chart = service.getStockChart();

            assertThat(chart.labels()).containsExactly("충남 예산 센터", "전남 나주 센터");
            assertThat(chart.quantities()).containsExactly(900, 470);
            assertThat(chart.total()).isEqualTo(1370);
        }

        @Test
        @DisplayName("재고가 없으면 빈 데이터를 돌려준다 (화면이 안내 문구로 처리한다)")
        void emptyChart() {
            given(inventoryRepository.findStockByCenter(null)).willReturn(List.of());

            CenterStockChartDto chart = service.getStockChart();

            assertThat(chart.labels()).isEmpty();
            assertThat(chart.total()).isZero();
        }
    }

    /* ------------------------------------------------------------------
     * 픽스처
     * ------------------------------------------------------------------ */

    private void givenCenters(Center... centers) {
        given(centerRepository.findByActiveTrueOrderByCenterCodeAsc()).willReturn(List.of(centers));
    }

    private Center center(Long id, String code, String name) {
        return Center.builder()
                .centerId(id).centerCode(code).name(name)
                .region("권역").note("운영 방향").active(true)
                .build();
    }

    /** 적재율 계산만 확인할 때 쓰는 카드 */
    private CenterOverviewDto overview(int quantity, int capacity) {
        return CenterOverviewDto.builder()
                .centerId(1L).centerCode("C1").centerName("센터")
                .quantity(quantity).capacity(capacity)
                .activity(java.util.Map.of())
                .animalMix(java.util.Map.of())
                .build();
    }
}
