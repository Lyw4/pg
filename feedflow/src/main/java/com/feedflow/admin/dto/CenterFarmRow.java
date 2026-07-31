package com.feedflow.admin.dto;

/**
 * 센터별 담당 농장 집계 결과 (Repository JPQL 전용 DTO).
 *
 * <h3>왜 한 행에 네 개를 함께 담는가</h3>
 * 화면이 센터 카드에 <b>담당 농장 수 · 거래 중 수 · 사육 규모 · 월 예상 사료량</b>을
 * 함께 보여준다. 네 값을 따로 조회하면 쿼리가 네 번 나가고, 그 사이에 데이터가 바뀌면
 * 같은 카드 안에서 숫자가 서로 맞지 않는다.
 * <p>
 * 원본(팀원 모듈)은 전체 농장을 자바로 읽어 스트림으로 네 번 집계했다.
 * 화면 한 번 열 때 <b>전체 목록을 네 번 로드</b>하고, 센터별 합계는
 * {@code 센터 수 × 농장 수} 만큼 반복문을 돌았다. 농장이 늘면 그대로 느려진다.
 * 집계는 DB 가 가장 잘하는 일이므로 {@code group by} 한 번으로 옮겼다.
 *
 * <h3>세는 기준이 컬럼마다 다르다</h3>
 * <ul>
 *     <li><b>농장 수 · 사육 규모는 전체</b>를 센다. 거래를 잠시 보류했다고
 *         담당 농장이 아니게 되는 것은 아니다.</li>
 *     <li><b>월 예상 사료량은 거래 중({@code ACTIVE})만</b> 합산한다.
 *         보류 농장의 물량을 더하면 실제로 나가지 않을 사료를 기준으로
 *         발주 계획을 세우게 된다.</li>
 * </ul>
 * 이 차이가 화면에서 드러나야 하므로 {@link #farmCount} 와 {@link #activeCount} 를
 * 둘 다 내려보낸다. 하나만 주면 "20곳인데 왜 18곳 물량인가" 를 설명할 수 없다.
 *
 * @param centerId        센터 식별자
 * @param centerName      센터명
 * @param farmCount       담당 농장 수 (거래 보류 포함)
 * @param activeCount     거래 중인 농장 수
 * @param livestockCount  사육 두수 합계 (거래 보류 포함)
 * @param activeFeedQuantity 거래 중 농장의 월 예상 사료량 합계 (포대)
 */
public record CenterFarmRow(
        Long centerId,
        String centerName,
        Long farmCount,
        Long activeCount,
        Long livestockCount,
        Long activeFeedQuantity
) {

    public int farms() {
        return farmCount == null ? 0 : farmCount.intValue();
    }

    public int activeFarms() {
        return activeCount == null ? 0 : activeCount.intValue();
    }

    public int livestock() {
        return livestockCount == null ? 0 : livestockCount.intValue();
    }

    public int activeFeed() {
        return activeFeedQuantity == null ? 0 : activeFeedQuantity.intValue();
    }

    /** 거래 보류 중인 농장 수 */
    public int pausedFarms() {
        return farms() - activeFarms();
    }
}
