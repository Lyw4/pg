package com.ex.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import jakarta.persistence.PessimisticLockException;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleIllegalArgument(IllegalArgumentException exception) {
        return errorBody(exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("입력값을 확인해주세요.");
        return errorBody(message);
    }

    /**
     * 재고 부족·주문 상태 충돌처럼 요청 자체는 올바르나 현재 상태로는 처리할 수
     * 없는 비즈니스 규칙 위반입니다. 전용 핸들러가 없으면 500으로 나가면서
     * 프런트엔드가 사용자에게 보여줄 안내 문구(message)를 읽지 못합니다.
     */
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, Object> handleIllegalState(IllegalStateException exception) {
        return errorBody(exception.getMessage());
    }

    @ExceptionHandler({
            DataIntegrityViolationException.class,
            ObjectOptimisticLockingFailureException.class,
            PessimisticLockException.class
    })
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, Object> handleConcurrency(RuntimeException exception) {
        return errorBody("동시에 처리된 요청과 충돌했습니다. 최신 상태를 확인한 뒤 다시 시도해주세요.");
    }

    /**
     * 컨트롤러가 직접 지정한 상태 코드(예: 로그인 필요 401)를 그대로 유지합니다.
     * 아래 최종 핸들러가 삼켜서 500으로 바뀌지 않도록 먼저 처리합니다.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(
            ResponseStatusException exception) {
        String reason = exception.getReason();
        return ResponseEntity
                .status(exception.getStatusCode())
                .body(errorBody(reason == null
                        ? "요청을 처리하지 못했습니다."
                        : reason));
    }

    /**
     * 예상하지 못한 예외도 같은 JSON 형태로 반환해 프런트엔드 오류 처리가
     * 응답 형태에 따라 갈라지지 않게 합니다. 상세 원인은 로그로만 남깁니다.
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleUnexpected(Exception exception) {
        log.error("처리하지 못한 예외", exception);
        return errorBody("요청을 처리하지 못했습니다. 잠시 후 다시 시도해주세요.");
    }

    private Map<String, Object> errorBody(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("message", message);
        return body;
    }
}
