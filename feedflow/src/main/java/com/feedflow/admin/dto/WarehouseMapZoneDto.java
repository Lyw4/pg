package com.feedflow.admin.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 도면의 구역 블록(Zone) 하나.
 * <p>
 * 창고 평면도처럼 <b>단(Level)을 한 줄씩 쌓아</b> 표현한다.
 * 높은 단이 위로 오도록 정렬되어 있고, 줄 안에서는 랙 번호 순으로 배치된다.
 * <p>
 * 칸의 크기를 모두 똑같이 그리면 수용량 600 구역과 200 구역이 같아 보여
 * 도면을 봐도 창고 규모를 알 수 없다. 그래서 너비를 수용량에 비례시킨다.
 * ({@link WarehouseBinMapDto#getFlexGrow()})
 */
@Getter
@Builder
public class WarehouseMapZoneDto {

    /** 구역 그룹 (A / B / COLD) */
    private final String zone;

    /** 단별 배치 (위에서부터 = 높은 단이 위) */
    private final List<WarehouseMapLevelDto> levels;

    /** 이 구역의 전체 칸 (요약 집계용) */
    private final List<WarehouseBinMapDto> bins;

    private final int totalCapacity;
    private final int totalLoaded;
    private final int usageRate;

    public int getBinCount() {
        return bins.size();
    }

    public int getLevelCount() {
        return levels.size();
    }

    /** 진행바 너비용 (초과 적재 시에도 막대는 100 에서 멈춘다) */
    public int getUsageRateCapped() {
        return Math.min(usageRate, 100);
    }
}
