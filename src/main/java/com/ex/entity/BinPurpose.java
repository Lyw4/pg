package com.ex.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BinPurpose {
    STORAGE("보관", true, true, false),
    RECEIVING("입고 대기", false, true, false),
    SHIPPING("출고 대기", false, true, false),
    INSPECTION("검수·격리", false, true, false),
    IN_TRANSIT("센터 간 이동 중", false, false, true);

    private final String label;
    private final boolean countedInCapacity;
    private final boolean physicalSpace;
    private final boolean systemManaged;

    public boolean isSelectableByUser() {
        return !systemManaged;
    }
}
