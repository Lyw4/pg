package com.feedflow.admin.controller;

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
        AdminWarehouseBinController.class
})
public class AdminViewExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleResourceNotFound(ResourceNotFoundException e, Model model) {
        model.addAttribute("errorMessage", e.getMessage());
        return "error/not-found";
    }
}
