package com.feedflow.admin.dto;

import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;

/**
 * JSON API 오류 응답.
 *
 * @param status    HTTP 상태 코드
 * @param error     상태 코드 설명 (예: Not Found)
 * @param message   사용자에게 보여줄 메시지
 * @param timestamp 발생 시각
 */
public record ApiErrorResponse(
        int status,
        String error,
        String message,
        LocalDateTime timestamp
) {

    public static ApiErrorResponse of(HttpStatus status, String message) {
        return new ApiErrorResponse(
                status.value(),
                status.getReasonPhrase(),
                message,
                LocalDateTime.now());
    }
}
