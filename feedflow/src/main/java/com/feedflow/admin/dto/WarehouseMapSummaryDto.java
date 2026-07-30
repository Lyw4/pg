package com.feedflow.admin.dto;

import com.feedflow.common.util.Numbers;
import com.feedflow.domain.BinLoadStatus;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 창고 2D 맵 상단 요약.
 *
 * <h3>사용률 산정 정책 (확정)</h3>
 * 사용률의 분모는 <b>사용 중인 보관(STORAGE) 구역의 수용량만</b> 쓴다.
 * 창고 전체 수용량이 아니다.
 * <ul>
 *     <li><b>사용 중지 구역 제외</b> — 쓸 수 없는 공간을 여유 공간으로 계산하면
 *         적재 계획이 왜곡된다.</li>
 *     <li><b>입고/출고 대기 · 검수 구역 제외</b> — 물건이 잠시 머무는 곳이라
 *         비어 있는 것이 정상이다. 분모에 넣으면 정상 상태가 낮은 사용률로 나와
 *         "여유가 많다" 는 잘못된 신호를 준다.</li>
 * </ul>
 *
 * <h3>주의 - 창고끼리 사용률을 직접 비교하지 말 것</h3>
 * 대기 구역이 차지하는 비중은 창고마다 다르다.
 * (제1창고는 전체 수용량의 3%, 제2창고는 22% 가 대기 구역이다)
 * 따라서 두 창고의 사용률은 <b>분모 기준이 서로 다른 값</b>이다.
 * 화면에서 "대기 구역 수용 N 제외" 를 함께 표기하는 이유가 이것이다.
 * <p>
 * 전체 수용량 기준으로 바꾸려는 시도는 위 두 번째 이유 때문에 하지 않는다.
 * 실물 규모가 필요하면 {@link #getTotalLoadedIncludingWaiting()} 를 쓴다.
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

    /**
     * 사용률 분모 — <b>사용 중인 보관 구역</b>의 수용량 합계.
     * (사용 중지 · 입고/출고 대기 · 검수 구역은 포함하지 않는다)
     */
    private final int totalCapacity;

    /**
     * 사용률 분자 — <b>사용 중인 보관 구역</b>의 적재량 합계.
     * 창고에 쌓여 있는 실물 총량과 다르다. 실물은 {@link #getTotalLoadedIncludingWaiting()}.
     */
    private final int totalLoaded;

    /** 보관 구역 사용률 (%) — 창고 전체 수용량 기준이 아니다 */
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

    /**
     * 보관 구역에 더 쌓을 수 있는 수량.
     * <p>
     * 대기 구역의 빈 공간은 포함하지 않는다. 거기에 상시 적재하면
     * 입출고 동선이 막히므로 "적재 가능 공간" 으로 볼 수 없다.
     */
    public int getRemainingCapacity() {
        return Math.max(totalCapacity - totalLoaded, 0);
    }

    public int getUsageRateCapped() {
        return Numbers.cappedPercent(usageRate);
    }
}
