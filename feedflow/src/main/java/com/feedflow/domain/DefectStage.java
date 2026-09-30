package com.feedflow.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 불량이 발견된 단계 (어디서 발견했는가).
 *
 * <h3>팀원 값을 그대로 쓰지 않고 우리 구역 체계에 맞췄다</h3>
 * 원본({@code com.ex.entity.DefectRecord.OccurrenceStage})은
 * 입고 검사 · 보관 · <b>생산</b> · 출고 검사 · <b>고객 반품</b> 다섯 가지였다.
 * 두 개를 빼고 하나를 넣었다.
 * <ul>
 *     <li><b>생산 제외</b> — 우리는 사료를 만들지 않고 유통만 한다. 제조 공정이 없으므로
 *         그 단계에서 불량을 발견할 수 없다.</li>
 *     <li><b>고객 반품 제외</b> — 반품 절차를 의도적으로 만들지 않았다. 배송 완료 주문은
 *         취소 대상이 아니고, 실물이 고객에게 있는 상태에서 창고 재고를 되돌리면
 *         실물과 장부가 어긋난다. 없는 기능의 단계를 값으로 두면 화면에 고를 수 있는
 *         선택지가 생긴다.</li>
 *     <li><b>센터 간 이관 추가</b> — 우리에게는 있는 단계다. 운송 중 파손은 실제로
 *         일어나고, {@link BinPurpose#IN_TRANSIT} 가 그 구간을 표현한다.</li>
 * </ul>
 * 결과적으로 네 단계가 각각 {@link BinPurpose} 와 대응한다. 불량을 발견한 곳이
 * 곧 그 재고가 있던 구역이기 때문이다.
 */
@Getter
@RequiredArgsConstructor
public enum DefectStage {

    RECEIVING("입고 검사", "bg-primary", BinPurpose.RECEIVING),
    STORAGE("보관 중", "bg-secondary", BinPurpose.STORAGE),
    SHIPPING("출고 검사", "bg-info text-dark", BinPurpose.SHIPPING),
    TRANSFER("센터 간 이관 중", "bg-dark", BinPurpose.IN_TRANSIT);

    /** 화면 표기용 한글 라벨 */
    private final String description;

    /** Bootstrap 5 뱃지 클래스 */
    private final String badgeClass;

    /**
     * 이 단계에 해당하는 구역 용도.
     * <p>
     * 불량 등록 화면에서 <b>구역을 고르면 단계를 추천</b>하는 데 쓴다.
     * 담당자가 같은 사실을 두 번 입력하지 않게 하려는 것이다.
     */
    private final BinPurpose binPurpose;

    /**
     * 입고 시점에 발견된 불량인지.
     * <p>
     * 입고 검사 단계의 불량은 <b>공급업체 책임을 물을 여지가 크다.</b>
     * 아직 우리가 보관해 본 적이 없는 재고이기 때문이다.
     */
    public boolean isAtReceiving() {
        return this == RECEIVING;
    }

    /**
     * 구역 용도로 단계를 추정한다.
     * <p>
     * 검수 구역({@link BinPurpose#INSPECTION})은 입고 검사 단계로 본다.
     * 검수는 입고 직후에 하는 일이고, 별도 단계를 두면 담당자가 '입고 검사' 와
     * '검수' 중 무엇을 고를지 매번 판단해야 한다.
     * <p>
     * 대응하는 단계가 없으면 {@code null} 을 반환한다. 그때는 담당자가 직접 고른다.
     */
    public static DefectStage from(BinPurpose binPurpose) {
        if (binPurpose == null) {
            return null;
        }
        if (binPurpose == BinPurpose.INSPECTION) {
            return RECEIVING;
        }
        for (DefectStage stage : values()) {
            if (stage.binPurpose == binPurpose) {
                return stage;
            }
        }
        return null;
    }
}
