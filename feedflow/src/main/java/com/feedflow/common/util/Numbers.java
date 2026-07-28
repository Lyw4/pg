package com.feedflow.common.util;

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
}
