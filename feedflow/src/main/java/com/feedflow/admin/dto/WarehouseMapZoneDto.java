package com.feedflow.admin.dto;

import com.feedflow.domain.BinLoadStatus;
import com.feedflow.common.util.Numbers;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 구역(Zone) 요약 — 도면 위의 범례/칩으로 표시한다.
 * <p>
 * 자유 배치 도면에서는 구역이 사각형 묶음으로만 구분되므로,
 * 구역별 적재 현황을 별도 요약으로 보여줘야 "어느 구역이 포화인지" 알 수 있다.
 * <p>
 * 배치 좌표의 <b>경계 상자(bounding box)</b> 도 함께 계산해 도면 위에 구역 이름을
 * 큰 글자로 겹쳐 표시한다. (스케치의 A · B · C 라벨)
 */
@Getter
@Builder
public class WarehouseMapZoneDto {

    /**
     * 저온 구역 코드.
     * <p>
     * 저온 구역은 도면에서 파란 점선으로 따로 표시해 일반 보관 구역과 구분한다.
     * 온도 관리 대상이라 아무 곳에나 적재할 수 없어 시각적으로 즉시 구분되어야 한다.
     */
    public static final String COLD_ZONE_CODE = "COLD";

    private final String zone;

    private final int binCount;

    /** 보관 구역의 수용량 합계 (사용 중지 · 비보관 용도 제외) */
    private final int totalCapacity;

    private final int totalLoaded;
    private final int usageRate;

    /* ---------------- 도면 위 라벨 위치 (경계 상자) ---------------- */
    private final int posX;
    private final int posY;
    private final int posWidth;
    private final int posHeight;

    /**
     * 구역 사각형들의 경계 상자로 라벨 위치를 계산한다.
     *
     * @param zone 구역 코드
     * @param bins 이 구역에 속한 사각형 (1건 이상)
     */
    public static WarehouseMapZoneDto of(String zone, List<WarehouseBinMapDto> bins) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;

        int totalCapacity = 0;
        int totalLoaded = 0;

        for (WarehouseBinMapDto bin : bins) {
            minX = Math.min(minX, bin.getPosX());
            minY = Math.min(minY, bin.getPosY());
            maxX = Math.max(maxX, bin.getPosX() + bin.getPosWidth() - 1);
            maxY = Math.max(maxY, bin.getPosY() + bin.getPosHeight() - 1);

            // 사용 중이면서 보관 용도인 구역만 적재율 통계에 넣는다
            if (bin.isActive() && bin.isStorage()) {
                totalCapacity += bin.getMaxCapacity();
                totalLoaded += bin.getLoadedQuantity();
            }
        }

        return WarehouseMapZoneDto.builder()
                .zone(zone)
                .binCount(bins.size())
                .totalCapacity(totalCapacity)
                .totalLoaded(totalLoaded)
                .usageRate(WarehouseBinMapDto.calculateUsageRate(totalLoaded, totalCapacity))
                .posX(minX)
                .posY(minY)
                .posWidth(maxX - minX + 1)
                .posHeight(maxY - minY + 1)
                .build();
    }

    /** {@code grid-area: y / x / span h / span w} */
    public String getGridArea() {
        return posY + " / " + posX + " / span " + posHeight + " / span " + posWidth;
    }

    /** 저온 구역인지 */
    public boolean isCold() {
        return COLD_ZONE_CODE.equalsIgnoreCase(zone);
    }

    /** 구역 경계선 CSS 클래스 (저온 구역은 파란 점선으로 구분) */
    public String getAreaClass() {
        return isCold() ? "ff-zone-area-cold" : "ff-zone-area-normal";
    }

    /** 구역 이름 칩에 붙일 보조 라벨 */
    public String getKindLabel() {
        return isCold() ? "저온" : "상온";
    }

    public int getUsageRateCapped() {
        return Numbers.cappedPercent(usageRate);
    }

    /**
     * 구역 요약 진행바 색.
     * <p>
     * 구간 경계는 {@link BinLoadStatus} 의 상수를 쓴다. 도면 타일 색과 여기서 쓰는
     * 기준이 갈리면 같은 구역이 타일과 요약 카드에서 다른 색으로 보인다.
     */
    public String getBarClass() {
        if (usageRate >= BinLoadStatus.FULL_THRESHOLD) {
            return "bg-danger";
        }
        if (usageRate >= BinLoadStatus.NORMAL_THRESHOLD) {
            return "bg-warning";
        }
        return "bg-success";
    }
}
