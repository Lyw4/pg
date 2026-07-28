package com.feedflow.common.util;

/**
 * 문자열 정규화 유틸.
 * <p>
 * 품목코드 / 구역코드 / 로트번호 등 업무 코드는 "공백 제거 + 대문자" 규칙으로
 * 정규화해서 저장하고 비교해야 대소문자만 다른 중복 등록을 막을 수 있다.
 * 기존에는 5개 서비스가 같은 private 헬퍼를 각각 들고 있었다.
 */
public final class Texts {

    private Texts() {
    }

    /** 업무 코드 정규화 : 앞뒤 공백 제거 + 대문자 */
    public static String code(String value) {
        return value == null ? null : value.trim().toUpperCase();
    }

    /** 앞뒤 공백 제거 (null 안전) */
    public static String trim(String value) {
        return value == null ? null : value.trim();
    }

    /** 비어 있으면 null (검색 조건에서 "조건 없음"으로 취급하기 위함) */
    public static String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    /** 비어 있으면 기본값 */
    public static String defaultIfBlank(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value.trim();
    }

    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
