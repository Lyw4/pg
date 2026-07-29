package com.feedflow.admin.dto;

import com.feedflow.domain.BinPurpose;
import com.feedflow.domain.Warehouse;
import com.feedflow.domain.WarehouseBin;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 창고 구역 목록 / 상세 조회용 DTO.
 */
@Getter
@Builder
public class WarehouseBinDto {

    private final Long binId;
    private final String binCode;
    private final Warehouse warehouse;
    private final String zone;
    private final BinPurpose binPurpose;
    private final String rack;
    private final Integer binLevel;
    private final Integer maxCapacity;

    /* 2D 도면 배치 좌표 */
    private final Integer posX;
    private final Integer posY;
    private final Integer posWidth;
    private final Integer posHeight;

    private final boolean active;
    private final String memo;
    private final String locationLabel;
    private final LocalDateTime createdAt;

    public static WarehouseBinDto from(WarehouseBin bin) {
        return WarehouseBinDto.builder()
                .binId(bin.getBinId())
                .binCode(bin.getBinCode())
                .warehouse(bin.getWarehouse())
                .zone(bin.getZone())
                .binPurpose(bin.getBinPurpose())
                .rack(bin.getRack())
                .binLevel(bin.getBinLevel())
                .maxCapacity(bin.getMaxCapacity())
                .posX(bin.getPosX())
                .posY(bin.getPosY())
                .posWidth(bin.getPosWidth())
                .posHeight(bin.getPosHeight())
                .active(bin.isActive())
                .memo(bin.getMemo())
                .locationLabel(bin.locationLabel())
                .createdAt(bin.getCreatedAt())
                .build();
    }

    public String getActiveBadgeClass() {
        return active ? "bg-success" : "bg-secondary";
    }

    public String getActiveLabel() {
        return active ? "사용" : "중지";
    }
}
