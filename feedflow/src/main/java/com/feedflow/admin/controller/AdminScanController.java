package com.feedflow.admin.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 현장 작업자용 바코드 스캔 화면.
 * 실제 조회는 {@link BarcodeApiController} 를 fetch 로 호출한다.
 */
@Controller
@RequestMapping("/admin/scan")
public class AdminScanController {

    @ModelAttribute("menu")
    public String menu() {
        return "scan";
    }

    @GetMapping
    public String scanPage(Model model) {
        model.addAttribute("subMenu", "scan");
        return "admin/scan/index";
    }
}
