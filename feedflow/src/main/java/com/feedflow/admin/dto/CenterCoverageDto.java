package com.feedflow.admin.dto;

import lombok.Getter;

import java.util.List;

/**
 * 센터 하나의 수요 계획 — 축종별 대조 + 센터 합계.
 *
 * <h3>축종별로 나눠 보는 이유</h3>
 * 센터 전체 합계만 보면 <b>부족이 가려진다.</b> 예를 들어 가금 사료가 남고 소 사료가
 * 모자란 센터는 합계로는 충족되어 보이지만, 실제로는 한우 농장에 보낼 사료가 없다.
 * 사료는 축종이 다르면 대체할 수 없으므로 합계는 참고값일 뿐이다.
 * <p>
 * 그래서 <b>센터 합계와 축종별 목록을 함께</b> 내려보내고, 화면은 합계 옆에
 * 부족한 축종 수를 표시한다.
 */
@Getter
public class CenterCoverageDto {

    private final Long centerId;
    private final String centerCode;
    private final String centerName;

    /** 운영 방향 예: 양계 · 양돈 중심 */
    private final String note;

    /** 축종별 대조 (수요나 재고가 있는 축종만) */
    private final List<AnimalCoverageDto> animals;

    /** 센터의 월 예상 사료량 합계 (포대) */
    private final int totalDemand;

    /** 센터의 출고 가능 재고 합계 (포대) */
    private final int totalSupply;

    /** 조치가 필요한 축종 수 (부족 · 빠듯) */
    private final int actionCount;

    /** 이번 달 수요를 채우려면 더 필요한 수량 합계 (포대) */
    private final int shortageQuantity;

    private CenterCoverageDto(Long centerId,
                              String centerCode,
                              String centerName,
                              String note,
                              List<AnimalCoverageDto> animals) {
        this.centerId = centerId;
        this.centerCode = centerCode;
        this.centerName = centerName;
        this.note = note;
        this.animals = animals;

        int demand = 0;
        int supply = 0;
        int action = 0;
        int shortage = 0;

        for (AnimalCoverageDto animal : animals) {
            demand += animal.getDemandQuantity();
            supply += animal.getSupplyQuantity();
            if (animal.isNeedsAction()) {
                action++;
            }
            shortage += animal.getShortageQuantity();
        }

        this.totalDemand = demand;
        this.totalSupply = supply;
        this.actionCount = action;
        this.shortageQuantity = shortage;
    }

    public static CenterCoverageDto of(Long centerId,
                                       String centerCode,
                                       String centerName,
                                       String note,
                                       List<AnimalCoverageDto> animals) {
        return new CenterCoverageDto(centerId, centerCode, centerName, note, animals);
    }

    /** 담당 농장이 없어 수요가 전혀 없는 센터인지 */
    public boolean isNoDemand() {
        return totalDemand <= 0;
    }

    /** 조치가 필요한 축종이 있는지 */
    public boolean isNeedsAction() {
        return actionCount > 0;
    }

    /**
     * 센터 합계 기준 충족률 (%).
     * <p>
     * <b>참고값이다.</b> 축종이 다르면 사료를 대체할 수 없으므로, 이 값이 100% 를
     * 넘어도 특정 축종은 부족할 수 있다. 화면에서 {@link #actionCount} 를 함께
     * 보여주는 이유다.
     */
    public int getTotalCoverageRate() {
        if (totalDemand <= 0) {
            return 0;
        }
        return (int) Math.round(totalSupply * 100.0 / totalDemand);
    }
}
