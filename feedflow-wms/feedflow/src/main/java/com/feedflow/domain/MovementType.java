package com.feedflow.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 재고 이동 유형.
 */
@Getter
@RequiredArgsConstructor
public enum MovementType {

    INBOUND("입고", "bg-primary", 1),
    OUTBOUND("출고", "bg-warning text-dark", -1),

    /**
     * 출고 취소에 따른 재고 복구.
     * <p>
     * 재고가 늘어나는 점은 입고와 같지만 <b>입고와 구분해서 남긴다.</b>
     * 입고로 기록하면 실제로 들어오지 않은 물량이 입고 실적에 섞여
     * 매입 집계와 이력 추적이 왜곡된다.
     */
    CANCEL("출고취소", "bg-dark", 1),

    DISPOSAL("폐기", "bg-danger", -1),

    /**
     * 같은 센터 안에서의 구역 이동.
     * <p>
     * <b>같은 센터 안에서만 쓴다.</b> 센터가 다르면 출발 센터의 재고가 실제로 줄어들어
     * {@code sign = 0}(총량 불변) 전제가 깨진다. 센터를 넘는 이동은
     * {@link #TRANSFER_OUT} / {@link #TRANSFER_IN} 한 쌍으로 기록한다.
     */
    MOVE("구역이동", "bg-info text-dark", 0),

    /**
     * 센터 간 이관 - 출발 센터에서 나감.
     * <p>
     * <b>sign 이 0 이 아닌 이유</b> — 출발 센터 관점에서 재고는 실제로 줄어든다.
     * {@code MOVE} 처럼 0 으로 두면 "어느 센터에서 빠졌는지" 를 표현할 수 없다.
     * {@link #TRANSFER_IN} 과 짝을 이뤄 <b>두 건의 합이 0</b> 이 되므로
     * 전국 총량({@code Product.totalStock})은 변하지 않는다.
     */
    TRANSFER_OUT("센터 이관 출고", "bg-danger", -1),

    /** 센터 간 이관 - 도착 센터로 들어옴. {@link #TRANSFER_OUT} 과 짝을 이룬다. */
    TRANSFER_IN("센터 이관 입고", "bg-success", 1),

    ADJUST("재고조정", "bg-secondary", 0);

    private final String description;
    private final String badgeClass;

    /** 재고 증감 방향 (+1 증가, -1 감소, 0 이동/조정) */
    private final int sign;

    /**
     * 센터 간 이관 이력인지.
     * <p>
     * 이력 화면이 센터 내 이동과 구분해 표기하고, 로트 잔여 수량 계산에서
     * 짝을 이루는 두 건이 서로 상쇄된다는 점을 드러내기 위해 쓴다.
     */
    public boolean isCenterTransfer() {
        return this == TRANSFER_OUT || this == TRANSFER_IN;
    }

    /**
     * 위치만 바뀌고 <b>전국 총 재고량은 변하지 않는</b> 유형인지.
     * <p>
     * {@code MOVE} 는 한 건으로, 이관은 두 건의 합으로 총량이 유지된다.
     * 어느 쪽이든 {@code ProductLot.lotQuantity} 와 {@code Product.totalStock} 을
     * 건드리면 재고가 이중 계상된다.
     */
    public boolean isRelocationOnly() {
        return this == MOVE || isCenterTransfer();
    }
}
