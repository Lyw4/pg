package com.feedflow.admin.dto;

import com.feedflow.domain.MovementType;
import lombok.Getter;

import java.util.Comparator;
import java.util.List;

/**
 * 전국 물류망 현황 (센터 카드 목록 + 전국 요약).
 * <p>
 * 센터별 카드만 나열하면 "전국이 어떤 상태인가" 를 사람이 머리로 합산해야 한다.
 * 합계와 최대·최소를 함께 담아 화면이 계산하지 않게 한다.
 */
@Getter
public class CenterNetworkDto {

    private final List<CenterOverviewDto> centers;

    /** 실적 집계 기간 (일) — 화면 문구에 쓴다 */
    private final int activityDays;

    /** 유통기한 임박 기준 (일) — 화면 문구에 쓴다 */
    private final int expiringSoonDays;

    /* ---------------- 전국 요약 ---------------- */

    private final int totalQuantity;

    /** 그중 보관 구역에 있는 수량 — 전국 적재율의 분자 (대기 · 운송 중 제외) */
    private final int totalStorageQuantity;

    private final int totalCapacity;
    private final int totalExpiringCount;
    private final int totalExpiredCount;
    private final int totalInbound;
    private final int totalOutbound;

    /** 기간 중 센터 간 이관으로 움직인 수량 (출고 기준 — 입고와 짝이라 중복 계산을 피한다) */
    private final int totalTransferred;

    private CenterNetworkDto(List<CenterOverviewDto> centers, int activityDays, int expiringSoonDays) {
        this.centers = centers;
        this.activityDays = activityDays;
        this.expiringSoonDays = expiringSoonDays;

        this.totalQuantity = centers.stream().mapToInt(CenterOverviewDto::getQuantity).sum();
        this.totalStorageQuantity = centers.stream()
                .mapToInt(CenterOverviewDto::getStorageQuantity).sum();
        this.totalCapacity = centers.stream().mapToInt(CenterOverviewDto::getCapacity).sum();
        this.totalExpiringCount = centers.stream().mapToInt(CenterOverviewDto::getExpiringCount).sum();
        this.totalExpiredCount = centers.stream().mapToInt(CenterOverviewDto::getExpiredCount).sum();
        this.totalInbound = centers.stream().mapToInt(CenterOverviewDto::getInboundQuantity).sum();
        this.totalOutbound = centers.stream().mapToInt(CenterOverviewDto::getOutboundQuantity).sum();

        // 이관은 출고와 입고가 짝이므로 한쪽만 센다. 둘을 더하면 같은 물량이 두 번 잡힌다.
        this.totalTransferred = centers.stream()
                .mapToInt(c -> c.quantityOf(MovementType.TRANSFER_OUT)).sum();
    }

    public static CenterNetworkDto of(List<CenterOverviewDto> centers,
                                      int activityDays,
                                      int expiringSoonDays) {
        return new CenterNetworkDto(centers, activityDays, expiringSoonDays);
    }

    /* ------------------------------------------------------------------
     * 화면 표기
     * ------------------------------------------------------------------ */

    public int getCenterCount() {
        return centers.size();
    }

    public boolean isEmpty() {
        return centers.isEmpty();
    }

    /**
     * 전국 평균 적재율 (%) — 센터별 적재율의 평균이 아니라 <b>전국 합계 기준</b>이다.
     * <p>
     * 센터별 적재율을 평균하면 작은 센터와 큰 센터가 같은 무게를 갖는다.
     * 분자는 보관 구역 재고이므로 분모(보관 구역 수용량)와 기준이 맞는다.
     */
    public int getUsageRate() {
        if (totalCapacity <= 0) {
            return 0;
        }
        return (int) Math.round(totalStorageQuantity * 100.0 / totalCapacity);
    }

    /** 전국에서 보관 구역 밖에 있는 수량 (입고 · 출고 대기 + 운송 중) */
    public int getWaitingQuantity() {
        return Math.max(totalQuantity - totalStorageQuantity, 0);
    }

    /** 대기 · 운송 중 재고가 있는지 (없으면 화면에서 그 줄을 숨긴다) */
    public boolean isHasWaitingStock() {
        return getWaitingQuantity() > 0;
    }

    /**
     * 재고가 가장 많이 쏠린 센터.
     * <p>
     * 전국망에서 한 곳에 재고가 몰리면 그 센터의 출고가 병목이 되고 운송비도 늘어난다.
     * 화면에서 가장 먼저 보여야 하는 정보다.
     */
    public CenterOverviewDto getBusiestCenter() {
        return centers.stream()
                .max(Comparator.comparingInt(CenterOverviewDto::getQuantity))
                .orElse(null);
    }

    /** 적재율이 가장 높은 센터 (공간이 먼저 부족해지는 곳) */
    public CenterOverviewDto getMostLoadedCenter() {
        return centers.stream()
                .max(Comparator.comparingInt(CenterOverviewDto::getUsageRate))
                .orElse(null);
    }

    /** 유통기한 임박 재고가 가장 많은 센터 (먼저 손봐야 하는 곳) */
    public CenterOverviewDto getMostUrgentCenter() {
        return centers.stream()
                .filter(CenterOverviewDto::isHasExpiring)
                .max(Comparator.comparingInt(CenterOverviewDto::getExpiringCount))
                .orElse(null);
    }

    /** 재고가 하나도 없는 센터 (신설 직후이거나 배분에서 빠진 곳) */
    public List<CenterOverviewDto> getEmptyCenters() {
        return centers.stream().filter(CenterOverviewDto::isEmpty).toList();
    }

    public boolean isHasEmptyCenter() {
        return !getEmptyCenters().isEmpty();
    }

    public boolean isHasTransfer() {
        return totalTransferred > 0;
    }

    public boolean isHasExpiring() {
        return totalExpiringCount > 0;
    }

    /** 전국 남은 여유 수량 — 적재율과 같은 기준(보관 구역)으로 뺀다 */
    public int getRemainingCapacity() {
        return Math.max(totalCapacity - totalStorageQuantity, 0);
    }
}
