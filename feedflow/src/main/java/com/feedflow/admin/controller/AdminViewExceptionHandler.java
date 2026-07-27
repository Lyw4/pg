package com.feedflow.admin.controller;

import com.feedflow.common.exception.BusinessRuleException;
import com.feedflow.common.exception.ResourceNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * 관리자 화면(HTML) 전용 예외 처리.
 * 존재하지 않는 ID 로 접근했을 때 500 대신 안내 화면을 보여준다.
 */
@ControllerAdvice(assignableTypes = {
        AdminController.class,
        AdminProductController.class,
        AdminWarehouseBinController.class,
        AdminInventoryController.class
})
public class AdminViewExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleResourceNotFound(ResourceNotFoundException e, Model model) {
        model.addAttribute("errorMessage", e.getMessage());
        return "error/not-found";
    }

    /**
     * 업무 규칙 위반.
     * 폼 화면에서 처리하지 못한 경우(직접 URL 호출 등)의 안전망이다.
     */
    @ExceptionHandler(BusinessRuleException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleBusinessRule(BusinessRuleException e, Model model) {
        model.addAttribute("errorMessage", e.getMessage());
        return "error/not-found";
    }
}
