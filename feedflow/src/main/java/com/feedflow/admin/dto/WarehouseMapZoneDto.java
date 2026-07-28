package com.feedflow.admin.dto;

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

    /* 도면 한 칸의 대략적인 크기 (px) — 라벨 글자 크기 계산에만 쓴다 */
    private static final double CELL_WIDTH = 36;
    private static final double CELL_HEIGHT = 34;

    /** 라벨 글자 크기 하한 / 상한 (px) */
    private static final int MIN_FONT_SIZE = 13;
    private static final int MAX_FONT_SIZE = 58;

    /**
     * 도면 위 구역 라벨의 글자 크기(px).
     * <p>
     * 고정 크기로 두면 <b>{@code COLD} 처럼 긴 이름이 박스를 넘어 잘린다.</b>
     * (실제로 'COL' 까지만 보이는 문제가 있었다)
     * 경계 상자의 너비/높이와 <b>글자 수</b>를 함께 반영해 박스 안에 들어가는 크기를 구한다.
     */
    public int getLabelFontSize() {
        int nameLength = Math.max(zone == null ? 1 : zone.length(), 1);

        // 폭 기준 : 글자 하나가 대략 폰트 크기의 0.62배 너비를 차지한다
        double byWidth = (posWidth * CELL_WIDTH * 0.85) / (nameLength * 0.62);
        // 높이 기준 : 박스 높이의 70% 를 넘지 않게 한다
        double byHeight = posHeight * CELL_HEIGHT * 0.7;

        int fontSize = (int) Math.floor(Math.min(byWidth, byHeight));
        return Math.max(MIN_FONT_SIZE, Math.min(MAX_FONT_SIZE, fontSize));
    }

    public int getUsageRateCapped() {
        return Math.min(usageRate, 100);
    }

    /** 구역 요약 진행바 색 */
    public String getBarClass() {
        if (usageRate >= 90) {
            return "bg-danger";
        }
        if (usageRate >= 60) {
            return "bg-warning";
        }
        return "bg-success";
    }
}
