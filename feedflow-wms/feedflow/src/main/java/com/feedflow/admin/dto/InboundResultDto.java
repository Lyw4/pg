package com.feedflow.admin.dto;

import com.feedflow.domain.BinPurpose;
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

    /** 입고한 구역 (대기 구역이면 '구역 간 이동' 링크에 넘긴다) */
    private final Long binId;
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

    /**
     * 입고된 로트가 이미 유통기한이 지났는지 여부.
     * 만료 로트는 출고 대상에서 제외되므로 화면에서 경고해야 한다.
     */
    private final boolean expiredLot;

    /** 입고한 구역의 용도 (경고 문구를 용도에 맞게 쓰기 위해 담는다) */
    private final BinPurpose binPurpose;

    /**
     * 입고한 구역이 출고 대상이 아닌지.
     * <p>
     * 입고 대기 · 검수 구역에 넣은 물건은 검수를 통과하지 않아 출고할 수 없다.
     * <b>재고 수량은 늘어나는데 출고 가능 재고는 늘어나지 않는다.</b> 이 사실을
     * 입고 직후에 알려주지 않으면, 나중에 출고가 막혔을 때 담당자는 원인을
     * 유통기한에서 찾게 된다(부족 안내가 제일 먼저 말하는 사유가 그것이다).
     */
    public boolean isNotShippableBin() {
        return binPurpose == BinPurpose.RECEIVING || binPurpose == BinPurpose.INSPECTION;
    }

    /** 화면 안내 메시지 */
    public String getSummaryMessage() {
        return "[" + lotNo + "] 로트를 " + binCode + " 구역에 " + quantity + "개 입고했습니다."
                + " (유통기한 " + expirationDate + ", 구역 보관 수량 " + binQuantity + "개)";
    }
}
