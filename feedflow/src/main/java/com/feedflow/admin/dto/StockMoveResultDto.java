package com.feedflow.admin.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 구역 간 재고 이동 결과.
 * <p>
 * 총 재고는 변하지 않고 <b>위치만</b> 바뀐다는 점을 화면에서 분명히 보여주기 위해
 * 로트 잔여 수량과 품목 총 재고를 함께 담는다. (이동 전후로 같은 값이어야 한다)
 */
@Getter
@Builder
public class StockMoveResultDto {

    /* 품목 · 로트 */
    private final Long productId;
    private final String productCode;
    private final String productName;
    private final Long lotId;
    private final String lotNo;

    /* 출발 구역 */
    private final Long fromBinId;
    private final String fromBinCode;
    private final String fromBinLocation;
    private final int fromQuantityBefore;
    private final int fromQuantityAfter;

    /* 도착 구역 */
    private final Long toBinId;
    private final String toBinCode;
    private final String toBinLocation;
    private final int toQuantityBefore;
    private final int toQuantityAfter;
    private final int toCapacityLimit;

    /** 이동 수량 */
    private final int movedQuantity;

    /**
     * 이동 후에도 변하지 않는 값들.
     * <p>
     * 이동은 창고 내부의 위치 변경이므로 로트 잔여와 품목 총 재고에 영향이 없다.
     */
    private final int lotQuantity;
    private final int productTotalStock;

    /** 도착 구역에 이 로트의 재고 행을 새로 만들었는지 */
    private final boolean targetCreated;

    /** 출발 구역의 이 로트 재고가 전량 빠져나갔는지 */
    private final boolean sourceDepleted;

    public String getSummaryMessage() {
        return "로트 " + lotNo + " " + movedQuantity + "개를 "
                + fromBinCode + " → " + toBinCode + " 로 이동했습니다."
                + " (총 재고는 변하지 않습니다: " + productTotalStock + "개)";
    }

    /** 도착 구역의 이동 후 남은 여유 공간 */
    public int getToRemainingCapacity() {
        return Math.max(0, toCapacityLimit - toQuantityAfter);
    }
}
