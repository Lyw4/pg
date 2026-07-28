package com.feedflow.admin.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 재고 정합성 재계산 결과 / 진단 결과.
 * <p>
 * {@code Product.totalStock} 은 조회 성능을 위해 비정규화한 값이므로
 * 로트 수량 합계와 어긋날 수 있다. 이 DTO 는 보정 전/후 값을 함께 담아
 * 관리자 화면에서 무엇이 얼마나 바뀌었는지 보여줄 수 있게 한다.
 * <p>
 * 같은 형태를 두 가지 용도로 사용한다.
 * <ul>
 *     <li><b>진단(diagnosis)</b> : 값을 바꾸지 않고 차이만 보여준다. ({@code adjusted = false})</li>
 *     <li><b>보정(sync)</b> : 실제로 totalStock 을 맞춘 뒤의 결과. ({@code adjusted} 는 변경 여부)</li>
 * </ul>
 */
@Getter
@Builder
public class StockSyncResultDto {

    private final Long productId;
    private final String productCode;
    private final String productName;

    /** 사용 중인 품목인지 (사용 중지 품목도 정합성 점검 대상이다) */
    private final boolean active;

    /** 보정 전 totalStock */
    private final int previousStock;

    /** 로트 수량 합계 (정답으로 간주하는 값) */
    private final int calculatedStock;

    /** 실제로 값이 바뀌었는지 */
    private final boolean adjusted;

    /**
     * 진단 결과 생성 (값을 변경하지 않는 조회 전용).
     *
     * @param row Repository 집계 결과
     */
    public static StockSyncResultDto ofDiagnosis(StockSyncRow row) {
        return StockSyncResultDto.builder()
                .productId(row.productId())
                .productCode(row.productCode())
                .productName(row.productName())
                .active(row.isActive())
                .previousStock(row.bookStock())
                .calculatedStock(row.calculatedStock())
                .adjusted(false)
                .build();
    }

    /** 차이 (양수면 totalStock 이 과다 계상되어 있었음) */
    public int getDifference() {
        return previousStock - calculatedStock;
    }

    /** 장부 재고와 로트 합계가 어긋나 있는지 (보정 대상 여부) */
    public boolean isMismatched() {
        return previousStock != calculatedStock;
    }

    /** 장부 재고가 실제보다 많음 (없는 재고를 팔 수 있는 위험) */
    public boolean isOverstated() {
        return getDifference() > 0;
    }

    /** 장부 재고가 실제보다 적음 (팔 수 있는 재고를 못 파는 손실) */
    public boolean isUnderstated() {
        return getDifference() < 0;
    }

    /* ------------------------------------------------------------------
     * 화면 표기 (Bootstrap Badge)
     * ------------------------------------------------------------------ */

    /** 상태 뱃지 문구 */
    public String getStatusLabel() {
        if (!isMismatched()) {
            return "정상";
        }
        return isOverstated() ? "과다 계상" : "과소 계상";
    }

    /** 상태 뱃지 클래스 (정상: 초록 / 과다: 빨강 / 과소: 노랑) */
    public String getStatusBadgeClass() {
        if (!isMismatched()) {
            return "bg-success";
        }
        return isOverstated() ? "bg-danger" : "bg-warning text-dark";
    }

    /** 차이 표기 (부호 포함, 정상이면 '-') */
    public String getDifferenceLabel() {
        int difference = getDifference();
        if (difference == 0) {
            return "-";
        }
        return (difference > 0 ? "+" : "") + difference;
    }

    public String getSummaryMessage() {
        if (!adjusted) {
            return "[" + productCode + "] 재고가 정확합니다. (" + calculatedStock + ")";
        }
        return "[" + productCode + "] 재고를 보정했습니다. "
                + previousStock + " → " + calculatedStock
                + " (차이 " + getDifference() + ")";
    }
}
