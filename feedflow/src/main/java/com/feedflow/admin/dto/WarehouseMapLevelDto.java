package com.feedflow.admin.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 도면의 한 줄(단, Level).
 * <p>
 * 실제 창고 도면처럼 <b>같은 단에 있는 구역을 한 줄로 나열</b>한다.
 * 줄 안에서는 랙 번호 순으로 왼쪽부터 배치되고,
 * 각 구역의 <b>너비는 최대 수용량에 비례</b>하므로 큰 구역이 실제로 크게 보인다.
 */
@Getter
@Builder
public class WarehouseMapLevelDto {

    /** 단 (1단, 2단 ...) */
    private final int level;

    /** 이 단에 속한 구역 (랙 번호 순) */
    private final List<WarehouseBinMapDto> bins;

    public int getBinCount() {
        return bins.size();
    }
}
