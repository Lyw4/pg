package com.feedflow.admin.dto;

import lombok.Getter;

import java.util.List;

/**
 * 전국 농장 고객사 현황 — 센터 카드 목록 + 전국 합계.
 *
 * <h3>전국 합계를 별도 쿼리로 구하지 않는다</h3>
 * 센터별 집계를 이미 받았으므로 그것을 더하면 전국 값이 된다. 별도 집계 쿼리를
 * 하나 더 두면 두 결과가 미묘하게 어긋날 여지가 생긴다(그 사이에 데이터가 바뀌면).
 * <b>같은 데이터에서 파생된 값은 같은 시점의 것이어야 한다.</b>
 */
@Getter
public class FarmNetworkDto {

    private final List<CenterFarmSummaryDto> centers;

    /** 전국 담당 농장 수 (거래 보류 포함) */
    private final int totalFarmCount;

    /** 전국 거래 중 농장 수 */
    private final int totalActiveCount;

    /** 전국 사육 두수 합계 */
    private final int totalLivestockCount;

    /** 전국 월 예상 사료량 합계 (거래 중만) */
    private final int totalActiveFeedQuantity;

    /** 담당 농장이 한 곳도 없는 센터 수 */
    private final int emptyCenterCount;

    private FarmNetworkDto(List<CenterFarmSummaryDto> centers) {
        this.centers = centers;

        int farms = 0;
        int active = 0;
        int livestock = 0;
        int feed = 0;
        int empty = 0;

        for (CenterFarmSummaryDto center : centers) {
            farms += center.getFarmCount();
            active += center.getActiveCount();
            livestock += center.getLivestockCount();
            feed += center.getActiveFeedQuantity();
            if (center.isEmptyCenter()) {
                empty++;
            }
        }

        this.totalFarmCount = farms;
        this.totalActiveCount = active;
        this.totalLivestockCount = livestock;
        this.totalActiveFeedQuantity = feed;
        this.emptyCenterCount = empty;
    }

    public static FarmNetworkDto of(List<CenterFarmSummaryDto> centers) {
        return new FarmNetworkDto(centers);
    }

    /** 전국 거래 보류 농장 수 */
    public int getTotalPausedCount() {
        return totalFarmCount - totalActiveCount;
    }

    public boolean isHasPaused() {
        return getTotalPausedCount() > 0;
    }

    /**
     * 농장 1곳당 평균 월 사료량 (포대).
     * <p>
     * 거래 중 농장만 대상이다. 분모에 보류 농장을 넣으면 평균이 낮아져
     * 센터 간 비교가 왜곡된다. 분자와 분모의 기준을 맞춘다.
     */
    public int getAverageFeedPerActiveFarm() {
        return totalActiveCount == 0 ? 0 : totalActiveFeedQuantity / totalActiveCount;
    }
}
