package com.feedflow.admin.controller;

import com.feedflow.admin.dto.ApiErrorResponse;
import com.feedflow.common.exception.BusinessRuleException;
import com.feedflow.common.exception.ResourceNotFoundException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Objects;

/**
 * 관리자 JSON API 전용 예외 처리.
 * 화면(HTML)용 {@link AdminViewExceptionHandler} 와 분리하여 JSON 오류 본문을 반환한다.
 */
@RestControllerAdvice(assignableTypes = {
        AdminRestController.class,
        BarcodeApiController.class,
        OutboundApiController.class,
        ScanActionApiController.class,
        WarehouseMapApiController.class,
        TraceabilityApiController.class
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

    /**
     * 낙관적 락 충돌 → 409 Conflict
     * 같은 재고를 동시에 수정한 경우이므로 클라이언트가 재시도하면 된다.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorResponse> handleOptimisticLock(OptimisticLockingFailureException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiErrorResponse.of(HttpStatus.CONFLICT,
                        "다른 사용자가 같은 재고를 동시에 처리했습니다. 최신 재고를 확인한 뒤 다시 시도해 주세요."));
    }

    /** 요청 본문 검증 실패 → 400 (첫 번째 오류 메시지를 그대로 전달) */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse("입력값이 올바르지 않습니다.");

        return ResponseEntity.badRequest()
                .body(ApiErrorResponse.of(HttpStatus.BAD_REQUEST, message));
    }
}
