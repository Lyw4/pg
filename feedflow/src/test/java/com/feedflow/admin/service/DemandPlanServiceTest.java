package com.feedflow.admin.service;

import com.feedflow.admin.dto.CenterAnimalQuantityRow;
import com.feedflow.admin.dto.CenterCoverageDto;
import com.feedflow.admin.dto.DemandPlanDto;
import com.feedflow.domain.AnimalType;
import com.feedflow.domain.Center;
import com.feedflow.domain.CoverageStatus;
import com.feedflow.repository.CenterRepository;
import com.feedflow.repository.FarmCustomerRepository;
import com.feedflow.repository.InventoryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * 수요 계획 서비스 단위 테스트.
 *
 * <h3>여기서 검증하는 것</h3>
 * 수요와 공급을 결합하는 규칙이다. 특히 <b>어느 한쪽에만 있는 조합</b>을 다루는 방식이
 * 핵심이다. 공급 기준으로만 순회하면 "담당 농장은 있는데 재고가 하나도 없는" 가장
 * 위험한 상태가 목록에서 아예 빠진다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("수요 계획 서비스 테스트")
class DemandPlanServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 7, 31);

    @Mock
    private CenterRepository centerRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private FarmCustomerRepository farmCustomerRepository;

    @InjectMocks
    private DemandPlanService demandPlanService;

    /* ==================================================================
     * 합집합 순회 — 어느 한쪽에만 있는 조합
     * ================================================================== */

    @Test
    @DisplayName("수요만 있고 재고가 없는 축종도 목록에 나온다 (가장 위험한 상태)")
    void includesAnimalWithDemandButNoStock() {
        givenCenters(center(1L, "C1-YS", "충남 예산 센터"));
        givenSupply();  // 재고 없음
        givenDemand(row(1L, AnimalType.CATTLE, 720L));

        DemandPlanDto plan = demandPlanService.getDemandPlan(TODAY);

        assertThat(plan.getCenters().get(0).getAnimals())
                .as("공급 기준으로 순회하면 이 줄이 사라져 부족을 놓친다")
                .hasSize(1);

        var cattle = plan.getCenters().get(0).getAnimals().get(0);
        assertThat(cattle.getDemandQuantity()).isEqualTo(720);
        assertThat(cattle.getSupplyQuantity()).isZero();
        assertThat(cattle.getCoverageRate()).isZero();
        assertThat(cattle.getStatus()).isEqualTo(CoverageStatus.SHORTAGE);
        assertThat(cattle.getShortageQuantity()).isEqualTo(720);
    }

    @Test
    @DisplayName("재고만 있고 담당 농장이 없는 축종도 목록에 나온다 (수요 없음)")
    void includesAnimalWithStockButNoDemand() {
        givenCenters(center(1L, "C1-YS", "충남 예산 센터"));
        givenSupply(row(1L, AnimalType.POULTRY, 500L));
        givenDemand();  // 수요 없음

        DemandPlanDto plan = demandPlanService.getDemandPlan(TODAY);

        var poultry = plan.getCenters().get(0).getAnimals().get(0);
        assertThat(poultry.getSupplyQuantity()).isEqualTo(500);
        assertThat(poultry.getDemandQuantity()).isZero();
        assertThat(poultry.getStatus())
                .as("0% 로 두면 '부족' 으로 보여 없는 수요를 채우려 하고, "
                    + "100% 로 두면 '적정' 으로 보여 재고가 왜 거기 있는지 묻지 않는다")
                .isEqualTo(CoverageStatus.NO_DEMAND);
        assertThat(poultry.isHasDemand()).isFalse();
        assertThat(poultry.getShortageQuantity()).isZero();
    }

    @Test
    @DisplayName("수요도 재고도 없는 축종은 목록에 넣지 않는다")
    void skipsAnimalWithNeitherDemandNorStock() {
        givenCenters(center(1L, "C1-YS", "충남 예산 센터"));
        givenSupply(row(1L, AnimalType.CATTLE, 900L));
        givenDemand(row(1L, AnimalType.CATTLE, 720L));

        DemandPlanDto plan = demandPlanService.getDemandPlan(TODAY);

        assertThat(plan.getCenters().get(0).getAnimals())
                .as("축종 3종을 모두 넣으면 그 센터가 실제로 다루는 축종을 알 수 없다")
                .hasSize(1)
                .extracting(a -> a.getAnimalType())
                .containsExactly(AnimalType.CATTLE);
    }

    @Test
    @DisplayName("담당 농장이 없는 센터도 카드가 나온다")
    void centerWithoutFarmStillGetsCard() {
        givenCenters(center(1L, "C1-YS", "예산"), center(9L, "C9-ZZ", "미배정"));
        givenSupply(row(1L, AnimalType.CATTLE, 900L));
        givenDemand(row(1L, AnimalType.CATTLE, 720L));

        DemandPlanDto plan = demandPlanService.getDemandPlan(TODAY);

        assertThat(plan.getCenters()).hasSize(2);

        CenterCoverageDto empty = plan.getCenters().get(1);
        assertThat(empty.isNoDemand()).isTrue();
        assertThat(empty.getAnimals()).isEmpty();
        assertThat(empty.getTotalCoverageRate())
                .as("분모가 0 이어도 나눗셈이 터지지 않는다")
                .isZero();
    }

    /* ==================================================================
     * 충족률과 상태 판정
     * ================================================================== */

    @Test
    @DisplayName("충족률은 수요 대비 재고 비율이다")
    void coverageRateIsSupplyOverDemand() {
        givenCenters(center(1L, "C1-YS", "예산"));
        givenSupply(row(1L, AnimalType.CATTLE, 900L));
        givenDemand(row(1L, AnimalType.CATTLE, 720L));

        DemandPlanDto plan = demandPlanService.getDemandPlan(TODAY);

        assertThat(plan.getCenters().get(0).getAnimals().get(0).getCoverageRate())
                .as("900 / 720 = 125%")
                .isEqualTo(125);
        assertThat(plan.getCenters().get(0).getAnimals().get(0).getStatus())
                .isEqualTo(CoverageStatus.ADEQUATE);
    }

    @Test
    @DisplayName("300% 를 넘으면 과다로 본다 — 사료는 유통기한이 있어 과잉이 폐기가 된다")
    void surplusIsAlsoAWarning() {
        givenCenters(center(1L, "C1-YS", "예산"));
        givenSupply(row(1L, AnimalType.PIG, 3000L));
        givenDemand(row(1L, AnimalType.PIG, 900L));

        var pig = demandPlanService.getDemandPlan(TODAY).getCenters().get(0).getAnimals().get(0);

        assertThat(pig.getCoverageRate()).isEqualTo(333);
        assertThat(pig.getStatus()).isEqualTo(CoverageStatus.SURPLUS);
        assertThat(pig.isNeedsAction())
                .as("과다는 조치 대상이 아니다. 발주를 늘리라는 신호가 아니라 줄이라는 신호다")
                .isFalse();
        assertThat(pig.getBarWidth())
                .as("막대는 100% 에서 멈춘다. 넘치면 옆 센터와 길이를 비교할 수 없다")
                .isEqualTo(100);
    }

    @Test
    @DisplayName("100% 이상 120% 미만은 빠듯 — 조치 대상이다")
    void tightNeedsAction() {
        givenCenters(center(1L, "C1-YS", "예산"));
        givenSupply(row(1L, AnimalType.CATTLE, 750L));
        givenDemand(row(1L, AnimalType.CATTLE, 720L));

        var cattle = demandPlanService.getDemandPlan(TODAY).getCenters().get(0).getAnimals().get(0);

        assertThat(cattle.getCoverageRate()).isEqualTo(104);
        assertThat(cattle.getStatus()).isEqualTo(CoverageStatus.TIGHT);
        assertThat(cattle.isNeedsAction())
                .as("딱 한 달치는 발주가 늦으면 바로 부족해진다")
                .isTrue();
        assertThat(cattle.getShortageQuantity())
                .as("이미 수요를 넘겼으므로 부족분은 0")
                .isZero();
    }

    /* ==================================================================
     * 집계
     * ================================================================== */

    @Test
    @DisplayName("센터 합계와 전국 합계는 축종별 결과를 더한 값이다")
    void totalsAreSumOfAnimals() {
        givenCenters(center(1L, "C1-YS", "예산"), center(2L, "C2-GJ", "김제"));
        givenSupply(row(1L, AnimalType.CATTLE, 500L),
                    row(1L, AnimalType.POULTRY, 300L),
                    row(2L, AnimalType.PIG, 1000L));
        givenDemand(row(1L, AnimalType.CATTLE, 720L),
                    row(1L, AnimalType.POULTRY, 200L),
                    row(2L, AnimalType.PIG, 900L));

        DemandPlanDto plan = demandPlanService.getDemandPlan(TODAY);

        /*
            판정 근거
              센터1 소   500 / 720  =  69%  SHORTAGE  → 조치 필요
              센터1 가금 300 / 200  = 150%  ADEQUATE
              센터2 돼지 1000 / 900 = 111%  TIGHT     → 조치 필요 (100~120% 구간)
         */
        CenterCoverageDto yesan = plan.getCenters().get(0);
        assertThat(yesan.getTotalDemand()).isEqualTo(920);
        assertThat(yesan.getTotalSupply()).isEqualTo(800);
        assertThat(yesan.getActionCount())
                .as("소는 부족(69%), 가금은 적정(150%). 이 센터의 조치 대상은 소 1건")
                .isEqualTo(1);
        assertThat(yesan.getShortageQuantity())
                .as("소 720 − 500 = 220. 가금은 남으므로 0")
                .isEqualTo(220);

        assertThat(plan.getTotalDemand()).isEqualTo(1820);
        assertThat(plan.getTotalSupply()).isEqualTo(1800);
        assertThat(plan.getAnimalsNeedingAction())
                .as("센터1 소(부족) + 센터2 돼지(빠듯) = 2건")
                .isEqualTo(2);
        assertThat(plan.getCentersNeedingAction())
                .as("두 센터 모두 조치가 필요하다")
                .isEqualTo(2);
        assertThat(plan.getTotalShortage())
                .as("조치 필요 건수와 부족 수량은 다르다 — 빠듯(111%)은 이미 수요를 "
                    + "넘겼으므로 부족분이 0 이다. 그래도 발주가 늦으면 바로 모자라므로 "
                    + "조치 대상으로는 센다")
                .isEqualTo(220);
        assertThat(plan.isHasAction()).isTrue();
    }

    @Test
    @DisplayName("센터 합계가 충족되어 보여도 축종별로는 부족할 수 있다")
    void centerTotalCanHideShortage() {
        givenCenters(center(1L, "C1-YS", "예산"));
        givenSupply(row(1L, AnimalType.CATTLE, 100L),
                    row(1L, AnimalType.POULTRY, 2000L));
        givenDemand(row(1L, AnimalType.CATTLE, 700L),
                    row(1L, AnimalType.POULTRY, 700L));

        CenterCoverageDto center = demandPlanService.getDemandPlan(TODAY).getCenters().get(0);

        assertThat(center.getTotalCoverageRate())
                .as("합계로는 2100/1400 = 150% 로 충족되어 보인다")
                .isEqualTo(150);
        assertThat(center.isNeedsAction())
                .as("그런데 소는 14% 다. 사료는 축종이 다르면 대체할 수 없다")
                .isTrue();
        assertThat(center.getShortageQuantity()).isEqualTo(600);
    }

    @Test
    @DisplayName("수요가 전혀 없으면 전국 충족률 계산에서 0 으로 나누지 않는다")
    void noDivisionByZeroWhenNoDemand() {
        givenCenters(center(1L, "C1-YS", "예산"));
        givenSupply();
        givenDemand();

        DemandPlanDto plan = demandPlanService.getDemandPlan(TODAY);

        assertThat(plan.getTotalCoverageRate()).isZero();
        assertThat(plan.isHasAction()).isFalse();
        assertThat(plan.getPeakDeliveryDay()).isNull();
        assertThat(plan.getDeliveryDayCount()).isZero();
    }

    /* ==================================================================
     * 픽스처
     * ================================================================== */

    private void givenCenters(Center... centers) {
        given(centerRepository.findByActiveTrueOrderByCenterCodeAsc())
                .willReturn(List.of(centers));
        given(farmCustomerRepository.findDeliverySchedule()).willReturn(List.of());
    }

    private void givenSupply(CenterAnimalQuantityRow... rows) {
        given(inventoryRepository.findAllocatableStockByCenterAndAnimalType(any()))
                .willReturn(List.of(rows));
    }

    private void givenDemand(CenterAnimalQuantityRow... rows) {
        given(farmCustomerRepository.findDemandByCenterAndAnimalType())
                .willReturn(List.of(rows));
    }

    private CenterAnimalQuantityRow row(Long centerId, AnimalType animalType, Long quantity) {
        return new CenterAnimalQuantityRow(centerId, animalType, quantity);
    }

    private Center center(Long centerId, String code, String name) {
        return Center.builder()
                .centerId(centerId)
                .centerCode(code)
                .name(name)
                .region("테스트 권역")
                .note("테스트 운영 방향")
                .active(true)
                .createdAt(LocalDateTime.now())
                .build();
    }
}
