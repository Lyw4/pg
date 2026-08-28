package com.feedflow.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 담당 농장 수요 대비 재고 충족 상태 (수요 계획 화면의 색상 구분용).
 * <p>
 * <b>DB 에 저장되지 않는 파생 값이다.</b> 월 예상 사료량 대비 출고 가능 재고 비율로
 * 그때그때 계산한다. {@link BinLoadStatus} 와 같은 방식이다.
 *
 * <h3>구간을 이렇게 나눈 이유 — 사료에는 유통기한이 있다</h3>
 * 일반 자재라면 재고가 많을수록 안전하다. 사료는 다르다. 유통기한이 지나면
 * 폐기해야 하므로 <b>과다 재고가 곧 손실</b>이다. 그래서 위쪽에도 경계를 둔다.
 *
 * <table>
 *     <tr><th>상태</th><th>조건</th><th>뜻</th></tr>
 *     <tr><td>SHORTAGE</td><td>100% 미만</td><td>이번 달 수요를 못 채운다</td></tr>
 *     <tr><td>TIGHT</td><td>100% 이상 120% 미만</td><td>딱 한 달치. 발주가 늦으면 바로 부족해진다</td></tr>
 *     <tr><td>ADEQUATE</td><td>120% 이상 300% 미만</td><td>1~3개월치. 적정</td></tr>
 *     <tr><td>SURPLUS</td><td>300% 이상</td><td>3개월치 초과. 유통기한 경과 위험</td></tr>
 *     <tr><td>NO_DEMAND</td><td>수요 0</td><td>담당 농장이 없는 축종. 비율을 계산할 수 없다</td></tr>
 * </table>
 *
 * <h3>왜 3개월인가</h3>
 * 시드의 사료 유통기한은 품목별로 90~180일이다. 3개월치를 넘게 쌓으면 그 중 일부는
 * 유통기한 안에 나가지 못할 수 있다. 정확한 판정은 로트별 만료일로 해야 하고
 * 그건 유통기한 임박 알림이 이미 한다. 여기서는 <b>발주량 판단</b>을 돕는 것이 목적이다.
 */
@Getter
@RequiredArgsConstructor
public enum CoverageStatus {

    SHORTAGE("부족", "bg-danger"),
    TIGHT("빠듯", "bg-warning text-dark"),
    ADEQUATE("적정", "bg-success"),
    SURPLUS("과다", "bg-info text-dark"),
    NO_DEMAND("수요 없음", "bg-light text-dark");

    /** 부족 → 빠듯 경계 (%) — 이번 달 수요를 채울 수 있는 최소선 */
    public static final int TIGHT_THRESHOLD = 100;

    /** 빠듯 → 적정 경계 (%) */
    public static final int ADEQUATE_THRESHOLD = 120;

    /** 적정 → 과다 경계 (%) — 3개월치. 유통기한 경과 위험 구간 */
    public static final int SURPLUS_THRESHOLD = 300;

    /** 화면 표기용 한글 라벨 */
    private final String description;

    /** Bootstrap 5 뱃지 클래스 */
    private final String badgeClass;

    /**
     * 충족 상태 판정.
     * <p>
     * <b>수요가 0 인지를 비율보다 먼저 본다.</b> 수요가 없으면 비율 자체가 성립하지 않는다.
     * 이때 0% 로 판정하면 '부족' 으로 보여 담당자가 있지도 않은 수요를 채우려 하고,
     * 100% 로 판정하면 '적정' 으로 보여 재고가 왜 거기 있는지 묻지 않게 된다.
     * 둘 다 잘못된 안내이므로 별도 상태로 둔다.
     *
     * @param demandQuantity 월 예상 사료량 (수요)
     * @param coverageRate   수요 대비 출고 가능 재고 비율 (%)
     */
    public static CoverageStatus of(int demandQuantity, int coverageRate) {
        if (demandQuantity <= 0) {
            return NO_DEMAND;
        }
        if (coverageRate >= SURPLUS_THRESHOLD) {
            return SURPLUS;
        }
        if (coverageRate >= ADEQUATE_THRESHOLD) {
            return ADEQUATE;
        }
        if (coverageRate >= TIGHT_THRESHOLD) {
            return TIGHT;
        }
        return SHORTAGE;
    }

    /** 조치가 필요한 상태인지 (부족 · 빠듯) */
    public boolean needsAction() {
        return this == SHORTAGE || this == TIGHT;
    }
}
