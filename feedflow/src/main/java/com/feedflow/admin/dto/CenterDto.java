package com.feedflow.admin.dto;

import com.feedflow.domain.Center;
import lombok.Builder;
import lombok.Getter;

/**
 * 물류센터 화면 표기용 DTO.
 * <p>
 * 센터 선택 상자 · 2D 도면 탭 등에 쓴다. 엔티티를 화면으로 직접 넘기지 않는다는
 * 규칙을 지키기 위한 변환 계층이다.
 */
@Getter
@Builder
public class CenterDto {

    private final Long centerId;
    private final String centerCode;
    private final String name;
    private final String region;
    private final String address;

    /** 보관 정책 요약 (도면 탭 부제) */
    private final String note;

    private final boolean active;

    /* 지도 좌표 — 부지 확정 전이면 null */
    private final Double latitude;
    private final Double longitude;

    public static CenterDto from(Center center) {
        return CenterDto.builder()
                .centerId(center.getCenterId())
                .centerCode(center.getCenterCode())
                .name(center.displayName())
                .region(center.getRegion())
                .address(center.getAddress())
                .note(center.getNote())
                .active(center.isActive())
                .latitude(center.getLatitude())
                .longitude(center.getLongitude())
                .build();
    }

    /** 지도에 핀을 찍을 수 있는지 (좌표 둘 다 있어야 한다) */
    public boolean isMappable() {
        return latitude != null && longitude != null;
    }

    public String getActiveBadgeClass() {
        return active ? "bg-success" : "bg-secondary";
    }

    public String getActiveLabel() {
        return active ? "운영중" : "중지";
    }
}
