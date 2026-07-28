package com.feedflow.admin.dto;

import com.feedflow.common.util.DDay;
import com.feedflow.domain.BinLoadStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 창고 2D 도면의 구역 타일 하나.
 * <p>
 * 적재율과 상태 색상은 이 DTO 안에서 계산해 화면(Thymeleaf)이 계산 로직을 갖지 않게 한다.
 */
@Getter
@Builder
public class WarehouseBinMapDto {

    private final Long binId;
    private final String binCode;
    private final String zone;
    private final String rack;
    private final Integer binLevel;

    /** 사용 중인 구역인지 (사용 중지 구역은 도면에서 회색 처리) */
    private final boolean active;

    /** 최대 수용량 */
    private final int maxCapacity;

    /** 현재 적재 수량 */
    private final int loadedQuantity;

    /** 수용량 대비 사용률 (%) — 0 ~ 100+ */
    private final int usageRate;

    private final BinLoadStatus status;

    /** 보관 중인 로트 수 */
    private final int lotCount;

    /** 보관 중인 품목 수 */
    private final int productCount;

    /** 가장 먼저 만료되는 로트의 유통기한 (재고 없으면 null) */
    private final LocalDate earliestExpiration;

    /** 가장 임박한 유통기한까지 남은 일수 (재고 없으면 null) */
    private final Long earliestRemainingDays;

    /**
     * 집계 결과 한 행을 도면 타일로 변환한다.
     *
     * @param row   Repository 집계 결과
     * @param today D-Day 계산 기준일
     */
    public static WarehouseBinMapDto of(WarehouseMapRow row, LocalDate today) {
        int loaded = row.loaded();
        int capacity = row.capacity();
        int usageRate = calculateUsageRate(loaded, capacity);

        return WarehouseBinMapDto.builder()
                .binId(row.binId())
                .binCode(row.binCode())
                .zone(row.zone())
                .rack(row.rack())
                .binLevel(row.binLevel())
                .active(row.isActive())
                .maxCapacity(capacity)
                .loadedQuantity(loaded)
                .usageRate(usageRate)
                .status(BinLoadStatus.of(loaded, usageRate))
                .lotCount(row.lots())
                .productCount(row.products())
                .earliestExpiration(row.earliestExpiration())
                .earliestRemainingDays(remainingDays(row.earliestExpiration(), today))
                .build();
    }

    /**
     * 사용률 계산.
     * <p>
     * 수용량이 0 이거나 없는 구역은 나눗셈이 불가능하므로 0% 로 본다.
     * 반올림을 쓰므로 210/400 은 53% 가 된다.
     * <p>
     * 구역 그룹(Zone) 합계와 창고 전체 요약도 같은 규칙으로 계산해야 화면 숫자가 어긋나지 않으므로
     * 서비스 계층에서도 쓸 수 있도록 {@code public} 으로 공개한다.
     */
    public static int calculateUsageRate(int loadedQuantity, int maxCapacity) {
        if (maxCapacity <= 0) {
            return 0;
        }
        return (int) Math.round(loadedQuantity * 100.0 / maxCapacity);
    }

    private static Long remainingDays(LocalDate expiration, LocalDate today) {
        if (expiration == null) {
            return null;
        }
        return ChronoUnit.DAYS.between(today, expiration);
    }

    /* ------------------------------------------------------------------
     * 화면 표기
     * ------------------------------------------------------------------ */

    /** 도면 타일 CSS 클래스 (사용 중지 구역은 상태색 대신 회색 사선 처리) */
    public String getTileClass() {
        return active ? status.getTileClass() : "ff-bin-inactive";
    }

    public String getStatusBadgeClass() {
        return active ? status.getBadgeClass() : "bg-secondary";
    }

    public String getStatusLabel() {
        return active ? status.getDescription() : "사용 중지";
    }

    /** 남은 여유 수량 (초과 적재 시 0) */
    public int getRemainingCapacity() {
        return Math.max(maxCapacity - loadedQuantity, 0);
    }

    /** 진행바 너비용 (100% 초과 시에도 막대는 100 에서 멈춘다) */
    public int getUsageRateCapped() {
        return Math.min(usageRate, 100);
    }

    /** 재고가 하나도 없는 구역인지 */
    public boolean isEmpty() {
        return loadedQuantity <= 0;
    }

    /** 유통기한 임박 알림 기준 (일) — 대시보드 경고와 동일하게 30일 */
    private static final long EXPIRING_SOON_DAYS = 30;

    /**
     * 유통기한이 임박(또는 만료)한 재고가 있는 구역인지.
     * <p>
     * 도면에서 "어느 구역에 급한 재고가 있는지" 바로 보이게 하기 위한 표시다.
     * 기준일은 대시보드 '유통기한 임박 알림'과 같은 30일을 쓴다.
     */
    public boolean isExpiringSoon() {
        return earliestRemainingDays != null && earliestRemainingDays <= EXPIRING_SOON_DAYS;
    }

    /** 가장 임박한 유통기한 D-Day 라벨 */
    public String getEarliestDDayLabel() {
        return earliestRemainingDays == null ? "-" : DDay.label(earliestRemainingDays);
    }

    public String getEarliestDDayBadgeClass() {
        return DDay.badgeClass(earliestRemainingDays);
    }

    /**
     * 도면에서 이 칸이 차지할 상대 너비 (CSS {@code flex-grow}).
     * <p>
     * 수용량에 비례시켜 <b>큰 구역이 도면에서도 크게</b> 보이도록 한다.
     * 모든 칸을 같은 크기로 그리면 수용량 600 구역과 200 구역이 구분되지 않아
     * 도면만 보고 창고 규모를 파악할 수 없다.
     * <p>
     * 수용량이 없는 구역은 최소 크기(1)로 둔다.
     */
    public int getFlexGrow() {
        return Math.max(maxCapacity, 1);
    }

    /** 위치 표기 (A구역 · 01랙 · 1단) */
    public String getLocationLabel() {
        StringBuilder sb = new StringBuilder(zone).append("구역");
        if (rack != null && !rack.isBlank()) {
            sb.append(" · ").append(rack).append("랙");
        }
        if (binLevel != null) {
            sb.append(" · ").append(binLevel).append("단");
        }
        return sb.toString();
    }
}
