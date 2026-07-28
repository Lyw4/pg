package com.feedflow.admin.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 도면의 한 줄(단, Level).
 * <p>
 * 실제 창고 도면처럼 <b>같은 단에 있는 구역을 한 줄로 나열</b>한다.
 * 줄 안에서는 랙 번호 순으로 왼쪽부터 배치된다.
 *
 * <h3>칸 너비를 도면 전체에서 통일하는 방법</h3>
 * 칸 너비를 CSS {@code flex-grow} 로만 주면 <b>비율이 줄 안에서만 유지된다.</b>
 * 예를 들어 2단에 500 칸 하나만 있고 1단에 500 + 400 두 칸이 있으면,
 * 2단의 500 칸이 줄 전체를 채워버려 1단의 500 칸보다 넓게 보인다.
 * 같은 수용량인데 크기가 다르게 보이므로 "칸 크기 = 수용 규모"가 깨진다.
 * <p>
 * 그래서 줄 끝에 <b>빈 채움 칸(filler)</b> 을 두어 모든 줄의 flex-grow 총합을
 * 도면 전체 기준값({@code referenceCapacity})으로 맞춘다.
 * 그러면 수용량이 같은 칸은 어느 줄에 있어도 같은 너비가 되고,
 * 같은 랙끼리 세로로 줄도 맞는다.
 */
@Getter
@Builder
public class WarehouseMapLevelDto {

    /** 단 (1단, 2단 ...) */
    private final int level;

    /** 이 단에 속한 구역 (랙 번호 순) */
    private final List<WarehouseBinMapDto> bins;

    /**
     * 줄 끝 빈 채움 칸의 상대 너비.
     * <p>
     * {@code 도면 전체 기준 수용량 - 이 줄의 수용량 합계}.
     * 가장 넓은 줄은 0 이 되어 채움 칸이 렌더링되지 않는다.
     */
    private final int fillerGrow;

    public int getBinCount() {
        return bins.size();
    }

    /** 채움 칸을 그려야 하는지 */
    public boolean isHasFiller() {
        return fillerGrow > 0;
    }
}
