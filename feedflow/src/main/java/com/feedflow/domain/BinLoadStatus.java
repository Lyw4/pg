package com.feedflow.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 창고 구역의 적재 상태 (2D 도면 색상 구분용).
 * <p>
 * <b>DB 에 저장되지 않는 파생(derived) 값이다.</b>
 * 최대 수용량 대비 현재 적재량 비율로 그때그때 계산한다.
 *
 * <h3>구간</h3>
 * <table>
 *     <tr><th>상태</th><th>조건</th><th>색</th></tr>
 *     <tr><td>EMPTY</td><td>적재량 0</td><td>회색</td></tr>
 *     <tr><td>SPARE</td><td>60% 미만</td><td>초록</td></tr>
 *     <tr><td>NORMAL</td><td>60% 이상 90% 미만</td><td>노랑</td></tr>
 *     <tr><td>FULL</td><td>90% 이상</td><td>빨강</td></tr>
 * </table>
 */
@Getter
@RequiredArgsConstructor
public enum BinLoadStatus {

    EMPTY("비어있음", "ff-bin-empty", "bg-secondary"),
    SPARE("여유", "ff-bin-spare", "bg-success"),
    NORMAL("보통", "ff-bin-normal", "bg-warning text-dark"),
    FULL("포화", "ff-bin-full", "bg-danger");

    /** 여유 → 보통 경계 (%) */
    public static final int NORMAL_THRESHOLD = 60;

    /** 보통 → 포화 경계 (%) */
    public static final int FULL_THRESHOLD = 90;

    /** 화면 표기용 한글 라벨 */
    private final String description;

    /** 도면 타일 CSS 클래스 (admin.css 에 정의) */
    private final String tileClass;

    /** Bootstrap 5 뱃지 클래스 */
    private final String badgeClass;

    /**
     * 적재 상태 판정.
     * <p>
     * 적재량이 0 인지를 <b>비율이 아니라 실제 수량으로</b> 판단한다.
     * 수용량이 큰 구역에 1포대만 있으면 비율은 0% 로 반내림되지만 '비어있음'은 아니기 때문이다.
     *
     * @param loadedQuantity 현재 적재 수량
     * @param usageRate      수용량 대비 사용률 (%)
     */
    public static BinLoadStatus of(int loadedQuantity, int usageRate) {
        if (loadedQuantity <= 0) {
            return EMPTY;
        }
        if (usageRate >= FULL_THRESHOLD) {
            return FULL;
        }
        if (usageRate >= NORMAL_THRESHOLD) {
            return NORMAL;
        }
        return SPARE;
    }
}
