package com.feedflow.admin.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 출고 취소로 되돌린 재고 한 줄.
 * <p>
 * 어느 로트를 어느 구역에 몇 개 되돌렸는지, 복구 전/후 수량까지 보여줘
 * 관리자가 결과를 눈으로 검증할 수 있게 한다.
 */
@Getter
@Builder
public class RestorationLineDto {

    private final int sequence;

    private final Long productId;
    private final String productCode;
    private final String productName;

    private final Long lotId;
    private final String lotNo;

    private final Long binId;
    private final String binCode;

    /** 되돌린 수량 */
    private final int restoredQuantity;

    /** 구역 재고 복구 전 / 후 */
    private final int binQuantityBefore;
    private final int binQuantityAfter;

    /** 로트 잔여 복구 후 */
    private final int lotQuantityAfter;

    /** 품목 총 재고 복구 후 */
    private final int totalStockAfter;

    /**
     * 출고 당시의 구역 재고 행이 사라져 새로 만들었는지.
     * <p>
     * 출고로 0이 된 구역 재고를 다른 작업(폐기 등)이 정리했을 수 있다.
     * 이때는 행을 새로 만들어 되돌리므로 화면에서 구분해 알려준다.
     */
    private final boolean binRecreated;
}
