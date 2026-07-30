package com.feedflow.common.util;

import com.feedflow.common.StockPolicy;

/**
 * 숫자 null 처리 유틸.
 * <p>
 * JPQL 의 {@code sum()} 은 대상 행이 없으면 null 을 반환하므로
 * 집계 결과를 화면/계산에 쓰기 전에 0 으로 치환해야 한다.
 */
public final class Numbers {

    private Numbers() {
    }

    /** null 이면 0 (집계 결과용) */
    public static long orZero(Long value) {
        return value == null ? 0L : value;
    }

    /** null 이면 0 (수량용) */
    public static int orZero(Integer value) {
        return value == null ? 0 : value;
    }

    /**
     * 비율을 진행바 표기용 상한({@link StockPolicy#MAX_PERCENT})으로 자른다.
     * <p>
     * 적재율은 한도를 넘을 수 있지만 진행바는 100% 에서 멈춰야 레이아웃이 깨지지 않는다.
     * 세 DTO 가 {@code Math.min(usageRate, 100)} 을 각각 갖고 있었다.
     */
    public static int cappedPercent(int rate) {
        return Math.min(rate, StockPolicy.MAX_PERCENT);
    }
}
