package com.feedflow.admin.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 2D 도면의 구역 그룹 (Zone) 하나.
 * <p>
 * 실제 창고처럼 <b>랙을 가로축, 단을 세로축</b> 으로 배치하기 위해
 * CSS Grid 의 열/행 개수를 서버에서 미리 계산해 내려준다.
 * 비어 있는 좌표는 타일이 없으므로 도면에 자연스럽게 빈칸으로 남는다.
 */
@Getter
@Builder
public class WarehouseMapZoneDto {

    /** 구역 그룹 (A / B / COLD) */
    private final String zone;

    /** 가로축 라벨 (랙 번호, 왼쪽부터) */
    private final List<String> rackLabels;

    /** 세로축 라벨 (단, 위에서부터 = 높은 단이 위) */
    private final List<Integer> levelLabels;

    private final List<WarehouseBinMapDto> bins;

    private final int totalCapacity;
    private final int totalLoaded;
    private final int usageRate;

    public int getColumnCount() {
        return rackLabels.size();
    }

    public int getRowCount() {
        return levelLabels.size();
    }

    public int getBinCount() {
        return bins.size();
    }

    /** CSS Grid 열 정의 (예: repeat(3, minmax(0, 1fr))) */
    public String getGridTemplateColumns() {
        return "repeat(" + Math.max(getColumnCount(), 1) + ", minmax(0, 1fr))";
    }

    /** 진행바 너비용 (초과 적재 시에도 막대는 100 에서 멈춘다) */
    public int getUsageRateCapped() {
        return Math.min(usageRate, 100);
    }
}
