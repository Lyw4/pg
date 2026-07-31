package com.feedflow.common.exception;

/**
 * 기준 정보의 업무 코드(품목 코드, 구역 코드 등)가 이미 존재할 때 발생.
 */
public class DuplicateCodeException extends RuntimeException {

    private final String code;

    public DuplicateCodeException(String code, String message) {
        super(message);
        this.code = code;
    }

    public static DuplicateCodeException ofProductCode(String code) {
        return new DuplicateCodeException(code, "이미 등록된 품목 코드입니다: " + code);
    }

    public static DuplicateCodeException ofBinCode(String code) {
        return new DuplicateCodeException(code, "이미 등록된 구역 코드입니다: " + code);
    }

    public String getCode() {
        return code;
    }
}
