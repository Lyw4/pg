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

    /** 비어 있는 구역 수 (사용 중인 보관 구역만) */
    private final int emptyBins;

    /** 보관 구역 수 (입고/출고 대기 · 검수 제외) */
    private final int storageBins;

    /* ------------------------------------------------------------------
     * 대기 구역 (입고 대기 / 출고 대기 / 검수)
     *
     * 적재율 계산에서는 빼지만 <b>실물은 창고를 차지한다.</b>
     * 요약에서 아예 감추면 "창고에 실제로 얼마나 쌓여 있는지" 를 알 수 없고,
     * 대기 구역 수용량 비중이 창고마다 달라 두 창고의 사용률을 비교할 수 없다.
     * (제1창고는 전체 수용량의 3%, 제2창고는 22% 가 대기 구역이다)
     * 그래서 통계에서 제외하되 별도 항목으로 함께 보여준다.
     * ------------------------------------------------------------------ */

    /** 대기 구역 수 */
    private final int waitingBins;

    /** 대기 구역 수용량 합계 */
    private final int waitingCapacity;

    /** 대기 구역에 실제로 쌓여 있는 수량 */
    private final int waitingLoaded;

    /** 사용 중지 구역의 수용량 (쓸 수 없는 공간) */
    private final int inactiveCapacity;

    /** 유통기한 임박 재고가 있는 구역 수 */
    private final int expiringBins;

    public static WarehouseMapSummaryDto of(List<WarehouseBinMapDto> bins) {
        int activeBins = 0;
        int inactiveBins = 0;
        int storageBins = 0;
        int totalCapacity = 0;
        int totalLoaded = 0;
        int fullBins = 0;
        int emptyBins = 0;
        int expiringBins = 0;
        int waitingBins = 0;
        int waitingCapacity = 0;
        int waitingLoaded = 0;
        int inactiveCapacity = 0;

        for (WarehouseBinMapDto bin : bins) {
            if (!bin.isActive()) {
                inactiveBins++;
                inactiveCapacity += bin.getMaxCapacity();
                continue;
            }
            activeBins++;

            // 입고/출고 대기 · 검수 구역은 상시 보관 공간이 아니므로 적재율에서 제외한다.
            // 다만 실물은 창고를 차지하므로 별도로 집계해 화면에 함께 보여준다.
            if (!bin.isStorage()) {
                waitingBins++;
                waitingCapacity += bin.getMaxCapacity();
                waitingLoaded += bin.getLoadedQuantity();
                continue;
            }
            storageBins++;
            totalCapacity += bin.getMaxCapacity();
            totalLoaded += bin.getLoadedQuantity();

            if (bin.getStatus() == BinLoadStatus.FULL) {
                fullBins++;
            }
            if (bin.getStatus() == BinLoadStatus.EMPTY) {
                emptyBins++;
            }
            if (bin.isExpiringSoon()) {
                expiringBins++;
            }
        }

        return WarehouseMapSummaryDto.builder()
                .totalBins(bins.size())
                .activeBins(activeBins)
                .inactiveBins(inactiveBins)
                .storageBins(storageBins)
                .totalCapacity(totalCapacity)
                .totalLoaded(totalLoaded)
                .usageRate(WarehouseBinMapDto.calculateUsageRate(totalLoaded, totalCapacity))
                .fullBins(fullBins)
                .emptyBins(emptyBins)
                .expiringBins(expiringBins)
                .waitingBins(waitingBins)
                .waitingCapacity(waitingCapacity)
                .waitingLoaded(waitingLoaded)
                .inactiveCapacity(inactiveCapacity)
                .build();
    }

    /** 대기 구역에 물건이 있는지 (있을 때만 화면에 별도 표기) */
    public boolean isHasWaitingStock() {
        return waitingLoaded > 0;
    }

    /**
     * 창고에 실제로 쌓여 있는 총 수량 (보관 + 대기).
     * <p>
     * 적재율의 분자와 다르다. 실물 재고 규모를 확인할 때 쓴다.
     */
    public int getTotalLoadedIncludingWaiting() {
        return totalLoaded + waitingLoaded;
    }

    /** 남은 여유 수량 */
    public int getRemainingCapacity() {
        return Math.max(totalCapacity - totalLoaded, 0);
    }

    public int getUsageRateCapped() {
        return Math.min(usageRate, 100);
    }
}
