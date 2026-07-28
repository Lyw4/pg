package com.feedflow.admin.dto;

import com.feedflow.domain.Warehouse;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 도면의 부대시설 (출입구 · 벽 · 검수실).
 * <p>
 * 재고를 보관하지 않는 <b>건물 구조물</b>이라 DB(WarehouseBin)에 넣지 않는다.
 * 구역으로 등록하면 적재율 통계와 입고 대상 목록에 섞여 들어가기 때문이다.
 * 창고 건물 구조는 자주 바뀌지 않으므로 창고별 상수로 관리한다.
 * <p>
 * 좌표계는 구역과 동일한 격자({@link WarehouseFloorPlanDto#GRID_COLUMNS} x
 * {@link WarehouseFloorPlanDto#GRID_ROWS})를 쓴다.
 */
@Getter
@Builder
public class WarehouseFacilityDto {

    /** 시설 종류 (CSS 클래스와 1:1) */
    public enum FacilityType {
        DOOR("ff-facility-door"),
        WALL("ff-facility-wall"),
        INSPECTION("ff-facility-inspection");

        private final String cssClass;

        FacilityType(String cssClass) {
            this.cssClass = cssClass;
        }

        public String getCssClass() {
            return cssClass;
        }
    }

    private final String label;
    private final FacilityType type;
    private final int posX;
    private final int posY;
    private final int posWidth;
    private final int posHeight;

    public String getCssClass() {
        return type.getCssClass();
    }

    /** {@code grid-area: y / x / span h / span w} */
    public String getGridArea() {
        return posY + " / " + posX + " / span " + posHeight + " / span " + posWidth;
    }

    private static WarehouseFacilityDto of(String label, FacilityType type,
                                           int x, int y, int width, int height) {
        return WarehouseFacilityDto.builder()
                .label(label)
                .type(type)
                .posX(x)
                .posY(y)
                .posWidth(width)
                .posHeight(height)
                .build();
    }

    /**
     * 창고별 부대시설 배치.
     * <p>
     * 두 창고 모두 왼쪽에 하역장(출입구 · 검수실)을 두고 세로 벽으로 보관 구역과 나눈다.
     */
    public static List<WarehouseFacilityDto> forWarehouse(Warehouse warehouse) {
        if (warehouse == null) {
            return List.of();
        }
        return switch (warehouse) {
            case WH1 -> List.of(
                    of("입고 출입구", FacilityType.DOOR, 1, 1, 4, 2),
                    of("하역장 벽", FacilityType.WALL, 5, 1, 1, 18),
                    of("출고 출입구", FacilityType.DOOR, 1, 13, 4, 2),
                    of("검수실", FacilityType.INSPECTION, 1, 16, 4, 3));
            case WH2 -> List.of(
                    of("입고 출입구", FacilityType.DOOR, 1, 1, 4, 2),
                    of("하역장 벽", FacilityType.WALL, 5, 1, 1, 18),
                    of("출고 출입구", FacilityType.DOOR, 1, 13, 4, 2),
                    of("검수실", FacilityType.INSPECTION, 1, 16, 4, 3));
        };
    }
}
