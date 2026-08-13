package com.ex.service;

import java.util.Set;

import com.ex.entity.BinPurpose;
import com.ex.entity.WarehouseBin;

/** 출고·차감에 사용할 수 있는 창고 구역 규칙입니다. */
public final class WmsAllocationPolicy {

    public static final Set<BinPurpose> ALLOCATABLE_PURPOSES =
            Set.of(BinPurpose.STORAGE, BinPurpose.SHIPPING);

    private WmsAllocationPolicy() {
    }

    /** 출고 가능한 활성 보관·출고 대기 구역인지 확인합니다. */
    public static boolean isAllocatable(WarehouseBin bin) {
        return bin != null
                && bin.isActive()
                && ALLOCATABLE_PURPOSES.contains(bin.getPurpose());
    }
}
