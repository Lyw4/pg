package com.feedflow.admin.dto;

import com.feedflow.domain.BinLoadStatus;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 창고 2D 맵 상단 요약.
 * <p>
 * 사용 중지된 구역은 <b>수용량 통계에서 제외</b>한다.
 * 쓸 수 없는 공간을 창고 여유 공간으로 계산하면 적재 계획이 왜곡되기 때문이다.
 */
@Getter
@Builder
public class WarehouseMapSummaryDto {

    /** 전체 구역 수 (사용 중지 포함) */
    private final int totalBins;

    /** 사용 중인 구역 수 */
    private final int activeBins;

    /** 사용 중지된 구역 수 */
    private final int inactiveBins;

    /** 사용 중인 구역의 수용량 합계 */
    private final int totalCapacity;

    /** 사용 중인 구역의 적재량 합계 */
    private final int totalLoaded;

    /** 창고 전체 사용률 (%) */
    private final int usageRate;

    /** 포화(90% 이상) 구역 수 */
    private final int fullBins;

    /** 비어 있는 구역 수 (사용 중인 구역만) */
    private final int emptyBins;

    public static WarehouseMapSummaryDto of(List<WarehouseBinMapDto> bins) {
        int activeBins = 0;
        int inactiveBins = 0;
        int totalCapacity = 0;
        int totalLoaded = 0;
        int fullBins = 0;
        int emptyBins = 0;

        for (WarehouseBinMapDto bin : bins) {
            if (!bin.isActive()) {
                inactiveBins++;
                continue;
            }
            activeBins++;
            totalCapacity += bin.getMaxCapacity();
            totalLoaded += bin.getLoadedQuantity();

            if (bin.getStatus() == BinLoadStatus.FULL) {
                fullBins++;
            }
            if (bin.getStatus() == BinLoadStatus.EMPTY) {
                emptyBins++;
            }
        }

        return WarehouseMapSummaryDto.builder()
                .totalBins(bins.size())
                .activeBins(activeBins)
                .inactiveBins(inactiveBins)
                .totalCapacity(totalCapacity)
                .totalLoaded(totalLoaded)
                .usageRate(WarehouseBinMapDto.calculateUsageRate(totalLoaded, totalCapacity))
                .fullBins(fullBins)
                .emptyBins(emptyBins)
                .build();
    }

    /** 남은 여유 수량 */
    public int getRemainingCapacity() {
        return Math.max(totalCapacity - totalLoaded, 0);
    }

    public int getUsageRateCapped() {
        return Math.min(usageRate, 100);
    }
}
