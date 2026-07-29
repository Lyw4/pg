package com.feedflow.admin.dto;

import com.feedflow.common.util.DDay;
import com.feedflow.domain.BinPurpose;
import com.feedflow.domain.BinLoadStatus;
import com.feedflow.domain.Warehouse;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 창고 2D 평면도의 구역 사각형 하나.
 * <p>
 * 적재율과 상태 색상, CSS Grid 배치 좌표를 이 DTO 안에서 계산해
 * 화면(Thymeleaf)이 계산 로직을 갖지 않게 한다.
 */
@Getter
@Builder
public class WarehouseBinMapDto {

    /** 유통기한 임박 알림 기준 (일) — 대시보드 경고와 동일하게 30일 */
    private static final long EXPIRING_SOON_DAYS = 30;

    private final Long binId;
    private final String binCode;
    private final Warehouse warehouse;
    private final String zone;
    private final BinPurpose binPurpose;
    private final String rack;
    private final Integer binLevel;

    /** 사용 중인 구역인지 (사용 중지 구역은 도면에서 사선 처리) */
    private final boolean active;

    private final int maxCapacity;
    private final int loadedQuantity;

    /** 수용량 대비 사용률 (%) — 0 ~ 100+ */
    private final int usageRate;

    private final BinLoadStatus status;

    private final int lotCount;
    private final int productCount;

    private final LocalDate earliestExpiration;
    private final Long earliestRemainingDays;

    /* ---------------- 도면 배치 좌표 (1-based) ---------------- */
    private final int posX;
    private final int posY;
    private final int posWidth;
    private final int posHeight;

    /**
     * 집계 결과 한 행을 도면 사각형으로 변환한다.
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
                .warehouse(row.warehouse())
                .zone(row.zone())
                .binPurpose(row.purpose())
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
                .posX(row.x())
                .posY(row.y())
                .posWidth(row.width())
                .posHeight(row.height())
                .build();
    }

    /**
     * 사용률 계산.
     * <p>
     * 수용량이 0 이거나 없는 구역은 나눗셈이 불가능하므로 0% 로 본다.
     * 반올림을 쓰므로 210/400 은 53% 가 된다.
     * <p>
     * 구역 합계와 창고 전체 요약도 같은 규칙으로 계산해야 화면 숫자가 어긋나지 않으므로
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
     * 도면 배치 (CSS Grid)
     * ------------------------------------------------------------------ */

    /** {@code grid-area: y / x / span h / span w} 형태의 CSS 값 */
    public String getGridArea() {
        return posY + " / " + posX + " / span " + posHeight + " / span " + posWidth;
    }

    /** 사각형이 좁으면(2칸 이하) 글자를 줄여야 하므로 화면에서 구분한다 */
    public boolean isNarrow() {
        return posWidth <= 2;
    }

    /** 사각형이 낮으면(1칸) 요약만 표시한다 */
    public boolean isFlat() {
        return posHeight <= 1;
    }

    /* ------------------------------------------------------------------
     * 상태 판정
     * ------------------------------------------------------------------ */

    /** 재고가 하나도 없는 구역인지 */
    public boolean isEmpty() {
        return loadedQuantity <= 0;
    }

    /**
     * 유통기한이 임박(또는 만료)한 재고가 있는 구역인지.
     * <p>
     * 도면에서 "어느 구역에 급한 재고가 있는지" 바로 보이게 하기 위한 표시다.
     * 기준일은 대시보드 '유통기한 임박 알림'과 같은 30일을 쓴다.
     */
    public boolean isExpiringSoon() {
        return earliestRemainingDays != null && earliestRemainingDays <= EXPIRING_SOON_DAYS;
    }

    /** 보관 구역인지 (입고/출고 대기, 검수 구역은 적재율 통계에서 제외) */
    public boolean isStorage() {
        return binPurpose.isCountedInCapacity();
    }

    /* ------------------------------------------------------------------
     * 화면 표기
     * ------------------------------------------------------------------ */

    /** 도면 사각형 CSS 클래스 */
    public String getTileClass() {
        if (!active) {
            return "ff-bin-inactive";
        }
        if (!isStorage()) {
            // 보관 구역이 아니면 적재율 색을 쓰지 않는다 (용도별 고정색)
            return switch (binPurpose) {
                case RECEIVING -> "ff-bin-receiving";
                case SHIPPING -> "ff-bin-shipping";
                case INSPECTION -> "ff-bin-inspection";
                default -> status.getTileClass();
            };
        }
        return status.getTileClass();
    }

    public String getStatusBadgeClass() {
        if (!active) {
            return "bg-secondary";
        }
        return isStorage() ? status.getBadgeClass() : binPurpose.getBadgeClass();
    }

    public String getStatusLabel() {
        if (!active) {
            return "사용 중지";
        }
        return isStorage() ? status.getDescription() : binPurpose.getDescription();
    }

    /** 남은 여유 수량 (초과 적재 시 0) */
    public int getRemainingCapacity() {
        return Math.max(maxCapacity - loadedQuantity, 0);
    }

    /** 진행바 너비용 (100% 초과 시에도 막대는 100 에서 멈춘다) */
    public int getUsageRateCapped() {
        return Math.min(usageRate, 100);
    }

    /** 가장 임박한 유통기한 D-Day 라벨 */
    public String getEarliestDDayLabel() {
        return earliestRemainingDays == null ? "-" : DDay.label(earliestRemainingDays);
    }

    /**
     * 도면 사각형 안에 넣을 짧은 D-Day 라벨.
     * <p>
     * 기본 라벨은 만료 시 {@code "만료 5일 경과"} 처럼 길어서 사각형을 넘쳐 글자가 잘린다.
     * 도면에서는 위험 신호만 보이면 되므로 압축해서 쓰고,
     * 정확한 문구는 툴팁과 상세 모달에서 보여준다.
     */
    public String getEarliestDDayShortLabel() {
        if (earliestRemainingDays == null) {
            return "-";
        }
        if (earliestRemainingDays < 0) {
            return "만료+" + Math.abs(earliestRemainingDays);
        }
        if (earliestRemainingDays == 0) {
            return "오늘";
        }
        return "D-" + earliestRemainingDays;
    }

    public String getEarliestDDayBadgeClass() {
        return DDay.badgeClass(earliestRemainingDays);
    }

    /** 위치 표기 (제1창고 · A구역 · 01랙 · 1단) */
    public String getLocationLabel() {
        StringBuilder sb = new StringBuilder();
        if (warehouse != null) {
            sb.append(warehouse.getDescription()).append(" · ");
        }
        sb.append(zone).append("구역");
        if (rack != null && !rack.isBlank()) {
            sb.append(" · ").append(rack).append("랙");
        }
        if (binLevel != null) {
            sb.append(" · ").append(binLevel).append("단");
        }
        return sb.toString();
    }
}
