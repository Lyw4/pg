package com.feedflow.common.util;

import com.feedflow.common.StockPolicy;

/**
 * 유통기한 D-Day 표기 유틸.
 * <p>
 * 기존에는 4개 DTO(InventoryDto / ExpiringLotDto / BarcodeLabelDto / AllocationLineDto)가
 * 각각 다른 임계값과 메서드명으로 같은 로직을 복제하고 있었다.
 * (특히 AllocationLineDto 는 만료 분기가 없어 "D--3" 처럼 잘못 표기되는 버그가 있었다)
 * 표기 규칙을 이 클래스 한 곳으로 모아 화면 전체의 일관성을 보장한다.
 *
 * <h3>표기 규칙</h3>
 * <ul>
 *     <li>음수 : "만료 N일 경과" / 검정 뱃지</li>
 *     <li>0    : "오늘 만료" / 빨강 뱃지</li>
 *     <li>1~7  : "D-N" / 빨강 뱃지</li>
 *     <li>8~30 : "D-N" / 노랑 뱃지</li>
 *     <li>31 이상 : "D-N" / 기본 뱃지</li>
 * </ul>
 */
public final class DDay {

    /** 위험 구간 (일) — 도면 뱃지 · 대시보드와 같은 기준을 쓴다 */
    private static final int CRITICAL_DAYS = StockPolicy.EXPIRY_CRITICAL_DAYS;

    /** 주의 구간 (일) */
    private static final int WARNING_DAYS = StockPolicy.EXPIRING_SOON_DAYS;

    private static final String BADGE_EXPIRED = "bg-dark";
    private static final String BADGE_CRITICAL = "bg-danger";
    private static final String BADGE_WARNING = "bg-warning text-dark";
    private static final String BADGE_NORMAL = "bg-light text-dark border";

    private DDay() {
    }

    /** D-Day 라벨 */
    public static String label(Long remainingDays) {
        if (remainingDays == null) {
            return "-";
        }
        if (remainingDays < 0) {
            return "만료 " + Math.abs(remainingDays) + "일 경과";
        }
        if (remainingDays == 0) {
            return "오늘 만료";
        }
        return "D-" + remainingDays;
    }

    /** 위험도별 Bootstrap 뱃지 클래스 */
    public static String badgeClass(Long remainingDays) {
        if (remainingDays == null) {
            return BADGE_NORMAL;
        }
        if (remainingDays < 0) {
            return BADGE_EXPIRED;
        }
        if (remainingDays <= CRITICAL_DAYS) {
            return BADGE_CRITICAL;
        }
        if (remainingDays <= WARNING_DAYS) {
            return BADGE_WARNING;
        }
        return BADGE_NORMAL;
    }

    /** 이미 만료되었는지 */
    public static boolean isExpired(Long remainingDays) {
        return remainingDays != null && remainingDays < 0;
    }
}
