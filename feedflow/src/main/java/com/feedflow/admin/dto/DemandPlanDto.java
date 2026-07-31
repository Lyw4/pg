package com.feedflow.admin.dto;

import lombok.Getter;

import java.util.List;

/**
 * 수요 계획 화면 전체 데이터 — 센터별 대조 + 전국 요약 + 정기 배송 일정.
 *
 * <h3>전국 합계를 별도 쿼리로 구하지 않는다</h3>
 * 센터별 결과를 더하면 전국 값이 된다. 별도 집계 쿼리를 두면 두 결과가 미묘하게
 * 어긋날 여지가 생긴다(그 사이에 데이터가 바뀌면). 같은 데이터에서 파생된 값은
 * 같은 시점의 것이어야 한다.
 */
@Getter
public class DemandPlanDto {

    private final List<CenterCoverageDto> centers;

    /** 정기 배송일별 일정 (거래 중 농장 기준) */
    private final List<DeliveryScheduleRow> schedule;

    /** 전국 월 예상 사료량 (포대) */
    private final int totalDemand;

    /** 전국 출고 가능 재고 (포대) */
    private final int totalSupply;

    /** 조치가 필요한 센터 수 (부족·빠듯 축종을 가진 센터) */
    private final int centersNeedingAction;

    /** 조치가 필요한 센터 × 축종 조합 수 */
    private final int animalsNeedingAction;

    /** 전국에서 더 필요한 수량 합계 (포대) */
    private final int totalShortage;

    private DemandPlanDto(List<CenterCoverageDto> centers, List<DeliveryScheduleRow> schedule) {
        this.centers = centers;
        this.schedule = schedule;

        int demand = 0;
        int supply = 0;
        int centerAction = 0;
        int animalAction = 0;
        int shortage = 0;

        for (CenterCoverageDto center : centers) {
            demand += center.getTotalDemand();
            supply += center.getTotalSupply();
            if (center.isNeedsAction()) {
                centerAction++;
            }
            animalAction += center.getActionCount();
            shortage += center.getShortageQuantity();
        }

        this.totalDemand = demand;
        this.totalSupply = supply;
        this.centersNeedingAction = centerAction;
        this.animalsNeedingAction = animalAction;
        this.totalShortage = shortage;
    }

    public static DemandPlanDto of(List<CenterCoverageDto> centers,
                                   List<DeliveryScheduleRow> schedule) {
        return new DemandPlanDto(centers, schedule);
    }

    /**
     * 전국 충족률 (%).
     * <p>
     * 센터 합계와 같은 이유로 <b>참고값이다.</b> 전국이 충족되어 있어도 특정 센터의
     * 특정 축종은 부족할 수 있고, 사료는 축종이 다르면 대체할 수 없다.
     * 게다가 센터 간 이관에는 시간이 걸리므로 다른 센터의 여유 재고가 즉시
     * 부족을 메워주지도 않는다.
     */
    public int getTotalCoverageRate() {
        if (totalDemand <= 0) {
            return 0;
        }
        return (int) Math.round(totalSupply * 100.0 / totalDemand);
    }

    public boolean isHasAction() {
        return animalsNeedingAction > 0;
    }

    /** 정기 배송이 몰려 있는 날 (물량이 가장 많은 날). 일정이 없으면 null */
    public DeliveryScheduleRow getPeakDeliveryDay() {
        return schedule.stream()
                .max((a, b) -> Integer.compare(a.amount(), b.amount()))
                .orElse(null);
    }

    /** 배송일이 지정된 서로 다른 날짜 수 */
    public int getDeliveryDayCount() {
        return schedule.size();
    }
}
