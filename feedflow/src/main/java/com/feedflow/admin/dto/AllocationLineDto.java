package com.feedflow.admin.dto;

import com.feedflow.common.util.DDay;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

/**
 * FEFO 출고 시 로트 × 구역 단위로 차감된(또는 차감될) 한 줄.
 */
@Getter
@Builder
public class AllocationLineDto {

    /** FEFO 순서 (1 = 가장 먼저 만료되는 로트) */
    private final int sequence;

    private final Long lotId;
    private final String lotNo;
    private final LocalDate expirationDate;
    private final long remainingDays;

    private final Long binId;
    private final String binCode;

    /** 이 로트/구역에서 차감된 수량 */
    private final int allocatedQuantity;

    /** 차감 전 구역 보관 수량 */
    private final int binQuantityBefore;

    /** 차감 후 구역 보관 수량 */
    private final int binQuantityAfter;

    /** 차감 후 로트 전체 잔여 수량 */
    private final int lotQuantityAfter;

    /** 해당 구역 재고가 전량 소진되었는지 */
    public boolean isDepleted() {
        return binQuantityAfter == 0;
    }

    public String getDDayLabel() {
        return DDay.label(remainingDays);
    }

    public String getDDayBadgeClass() {
        return DDay.badgeClass(remainingDays);
    }
}
