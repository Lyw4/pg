package com.feedflow.common.exception;

/**
 * 업무 규칙 위반 시 발생 (사용 중지된 기준정보 사용, 적재 용량 초과 등).
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
