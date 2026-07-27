package com.feedflow.admin.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 사원용 로그인 / 접근 거부 화면.
 */
@Controller
public class LoginController {

    @GetMapping("/login")
    public String loginForm() {
        return "login";
    }

    @GetMapping("/access-denied")
    public String accessDenied() {
        return "error/access-denied";
    }
}
