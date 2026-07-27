package com.feedflow.admin.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

/**
 * 입고 처리 결과.
 */
@Getter
@Builder
public class InboundResultDto {

    private final Long lotId;
    private final String lotNo;
    private final String productCode;
    private final String productName;
    private final String binCode;

    private final LocalDate manufacturedDate;

    /** 자동 계산된 유통기한 */
    private final LocalDate expirationDate;

    /** 입고 수량 */
    private final int quantity;

    /** 입고 후 해당 구역의 보관 수량 */
    private final int binQuantity;

    /** 입고 후 로트 전체 수량 */
    private final int lotQuantity;

    /** 입고 후 품목 전체 재고 */
    private final int productTotalStock;

    /** 신규 로트 생성 여부 (false = 기존 로트에 합산) */
    private final boolean newLot;

    /** 신규 구역 재고 생성 여부 (false = 기존 구역 재고에 합산) */
    private final boolean newInventory;

    /** 화면 안내 메시지 */
    public String getSummaryMessage() {
        return "[" + lotNo + "] 로트를 " + binCode + " 구역에 " + quantity + "개 입고했습니다."
                + " (유통기한 " + expirationDate + ", 구역 보관 수량 " + binQuantity + "개)";
    }
}
