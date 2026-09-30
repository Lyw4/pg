package com.feedflow.admin.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 센터 카드 1개 — 그 센터가 담당하는 농장 현황.
 * <p>
 * 센터 기준 정보({@code centers})와 농장 집계({@link CenterFarmRow})를 합친 것이다.
 * 담당 농장이 없는 센터도 카드가 나와야 하므로, 집계 행이 없으면 0 으로 채운다.
 * 조용히 빠지면 센터 5곳 중 4곳만 보여도 아무도 눈치채지 못한다.
 */
@Getter
@Builder
public class CenterFarmSummaryDto {

    private final Long centerId;
    private final String centerCode;
    private final String centerName;

    /** 권역 예: 충남 서북부 */
    private final String region;

    /** 운영 방향 예: 양계 · 양돈 중심 */
    private final String note;

    /** 담당 농장 수 (거래 보류 포함) */
    private final int farmCount;

    /** 거래 중인 농장 수 */
    private final int activeCount;

    /** 사육 두수 합계 */
    private final int livestockCount;

    /** 거래 중 농장의 월 예상 사료량 합계 (포대) */
    private final int activeFeedQuantity;

    /** 전국 월 예상 사료량 대비 이 센터의 비중 (%) */
    private final int sharePercent;

    /** 거래 보류 농장 수 */
    public int getPausedCount() {
        return farmCount - activeCount;
    }

    public boolean isHasPaused() {
        return getPausedCount() > 0;
    }

    /** 담당 농장이 아직 없는 센터인지 (화면에서 안내 문구를 띄운다) */
    public boolean isEmptyCenter() {
        return farmCount == 0;
    }
}
