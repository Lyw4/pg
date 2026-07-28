package com.feedflow.admin.dto;

import com.feedflow.domain.Warehouse;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 창고 한 동의 2D 평면도 전체.
 * <p>
 * 화면은 이 DTO 하나만 받아 그린다. (사각형 + 부대시설 + 구역 라벨 + 요약)
 */
@Getter
@Builder
public class WarehouseFloorPlanDto {

    /** 도면 격자 크기 — 좌표 입력 검증({@link WarehouseBinForm})과 같은 값을 써야 한다 */
    public static final int GRID_COLUMNS = WarehouseBinForm.GRID_COLUMNS;
    public static final int GRID_ROWS = WarehouseBinForm.GRID_ROWS;

    private final Warehouse warehouse;

    /** 구역 사각형 */
    private final List<WarehouseBinMapDto> bins;

    /** 출입구 · 벽 · 검수실 */
    private final List<WarehouseFacilityDto> facilities;

    /** 구역별 요약 (도면 위 라벨 + 하단 칩) */
    private final List<WarehouseMapZoneDto> zones;

    /** 창고 전체 적재 요약 */
    private final WarehouseMapSummaryDto summary;

    public int getGridColumns() {
        return GRID_COLUMNS;
    }

    public int getGridRows() {
        return GRID_ROWS;
    }

    /** CSS Grid 열 정의 */
    public String getGridTemplateColumns() {
        return "repeat(" + GRID_COLUMNS + ", minmax(0, 1fr))";
    }

    /** CSS Grid 행 정의 */
    public String getGridTemplateRows() {
        return "repeat(" + GRID_ROWS + ", minmax(22px, auto))";
    }

    public boolean isEmpty() {
        return bins.isEmpty();
    }
}
