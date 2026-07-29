package com.feedflow.admin.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 구역 클릭 시 모달에 뿌릴 상세 정보 (JSON 응답).
 * <p>
 * 구역 요약 + 그 구역에 보관 중인 재고(로트 단위) 목록을 함께 담는다.
 */
@Getter
@Builder
public class BinDetailDto {

    /** 구역 요약 (적재율 / 상태 색상 포함) */
    private final WarehouseBinMapDto bin;

    /** 이 구역에 보관 중인 재고 (유통기한 임박 순) */
    private final List<InventoryDto> inventories;

    public int getInventoryCount() {
        return inventories.size();
    }

    /** 만료된 로트가 섞여 있는지 (모달에 경고 표시용) */
    public boolean isHasExpired() {
        return inventories.stream().anyMatch(InventoryDto::isExpired);
    }
}
