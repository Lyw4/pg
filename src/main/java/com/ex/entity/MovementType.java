package com.ex.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MovementType {
    INBOUND("입고", 1),
    OUTBOUND("출고", -1),
    CANCEL_RESTORE("출고 취소 복원", 1),
    DISPOSAL("폐기", -1),
    MOVE("구역 이동", 0),
    TRANSFER_OUT("센터 이관 출발", -1),
    TRANSFER_IN("센터 이관 도착", 1),
    ADJUSTMENT("재고 조정", 0);

    private final String label;
    private final int sign;

    public boolean isTransfer() {
        return this == TRANSFER_OUT || this == TRANSFER_IN;
    }
}
