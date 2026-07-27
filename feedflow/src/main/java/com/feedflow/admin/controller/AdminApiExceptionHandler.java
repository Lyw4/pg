package com.feedflow.admin.controller;

import com.feedflow.admin.dto.ApiErrorResponse;
import com.feedflow.common.exception.BusinessRuleException;
import com.feedflow.common.exception.ResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 관리자 JSON API 전용 예외 처리.
 * 화면(HTML)용 {@link AdminViewExceptionHandler} 와 분리하여 JSON 오류 본문을 반환한다.
 */
@RestControllerAdvice(assignableTypes = {
        AdminRestController.class,
        BarcodeApiController.class,
        OutboundApiController.class
})
public class AdminApiExceptionHandler {

    /** 등록되지 않은 바코드 등 → 404 */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(ResourceNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(HttpStatus.NOT_FOUND, e.getMessage()));
    }

    /** 업무 규칙 위반 (빈 코드 등) → 400 */
    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiErrorResponse> handleBusinessRule(BusinessRuleException e) {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of(HttpStatus.BAD_REQUEST, e.getMessage()));
    }

    /** 필수 파라미터 누락 → 400 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParameter(
            MissingServletRequestParameterException e) {
        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of(HttpStatus.BAD_REQUEST,
                        "필수 파라미터가 누락되었습니다: " + e.getParameterName()));
    }
}
