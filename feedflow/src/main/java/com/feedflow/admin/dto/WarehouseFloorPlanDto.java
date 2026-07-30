package com.feedflow.admin.dto;

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

    /**
     * CSS Grid 행 정의.
     * <p>
     * 행 높이를 충분히 확보해야 사각형이 납작한 막대처럼 보이지 않는다.
     */
    public String getGridTemplateRows() {
        return "repeat(" + GRID_ROWS + ", minmax(34px, auto))";
    }

    public boolean isEmpty() {
        return bins.isEmpty();
    }

    /**
     * 보여줄 센터가 없을 때의 빈 도면.
     * <p>
     * 운영 중인 센터가 하나도 없을 수 있다(전부 운영 중지). 이때 조회 조건을 {@code null} 로
     * 넘기면 Repository 가 <b>전체 센터의 구역을 한 도면에 겹쳐</b> 내려줘 실제 위치를 오해하게 된다.
     * 화면은 "표시할 센터가 없음" 상태를 그려야 하므로 DB 를 건드리지 않고 빈 도면을 만든다.
     */
    public static WarehouseFloorPlanDto empty() {
        return WarehouseFloorPlanDto.builder()
                .bins(List.of())
                .facilities(List.of())
                .zones(List.of())
                .summary(WarehouseMapSummaryDto.of(List.of()))
                .build();
    }
}
